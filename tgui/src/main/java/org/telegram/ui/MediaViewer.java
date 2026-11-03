/*
 * This is the source code of Telegram for Android v. 11.4.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarMenuSlider;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SpeedIconDrawable;
import org.telegram.ui.Components.GroupedPhotosListView;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumSet;

import static org.telegram.messenger.AndroidUtilities.dp;

/**
 * tgui: a small full-screen photo / video viewer. Not a port of upstream PhotoViewer (20k lines of
 * loaders, editing, PiP, ExoPlayer); it borrows that class's look and gesture numbers on a plain
 * {@link Item} list:
 * <ul>
 * <li>openPhoto / closePhoto: the image grows from (and shrinks back into) the source rect the
 *     {@link Provider} reports, the source photo is hidden meanwhile; 200ms DecelerateInterpolator</li>
 * <li>onTouchEvent: drag down at scale 1 after 30dp (dy / 2 &gt; dx), closes past a sixth of the
 *     height; background alpha max(127, 255 * (1 - |ty| / (width / 4)))</li>
 * <li>pinch zoom up to 3x, double tap to 3x around the finger and back, pan with clamping;
 *     paging past a third of the width or 650dp/s, 15dp gap between pages; imageMoveAnimation
 *     250ms DecelerateInterpolator(1.5)</li>
 * <li>0x7f000000 action bar (back, sender, date) and caption panel, a 64dp play button and a
 *     VideoPlayerSeekBar-like line (4dp, 0x33ffffff / white, 6dp thumb, 8dp pressed) with
 *     "current / total" time</li>
 * </ul>
 * Videos play with {@link MediaPlayer} into a {@link TextureView}.
 */
public class MediaViewer {

    /** One page: a photo (or a video's thumbnail) and its texts. */
    public static class Item {
        /** The photo, or a video's preview frame (may be null for a video). */
        public Drawable photo;
        /** Media size in pixels (aspect ratio); defaults to the drawable's intrinsic size. */
        public int width, height;
        /** Non-null for a video. */
        public Uri video;
        public CharSequence caption;
        /** Action bar title / subtitle (upstream: the sender's name and the date). */
        public CharSequence sender;
        public CharSequence date;
        /** Free for the host (e.g. the message). */
        public Object tag;
        /** Stable host album ID. Zero is an ungrouped item; groups must be contiguous. */
        public long groupId;

        public static Item photo(Drawable photo, CharSequence sender, CharSequence date, CharSequence caption) {
            Item item = new Item();
            item.photo = photo;
            item.sender = sender;
            item.date = date;
            item.caption = caption;
            return item;
        }

        public static Item video(Uri video, Drawable thumb, CharSequence sender, CharSequence date, CharSequence caption) {
            Item item = photo(thumb, sender, date, caption);
            item.video = video;
            return item;
        }

        int mediaWidth() {
            if (width > 0) {
                return width;
            }
            return photo != null && photo.getIntrinsicWidth() > 0 ? photo.getIntrinsicWidth() : AndroidUtilities.displaySize.x;
        }

        int mediaHeight() {
            if (height > 0) {
                return height;
            }
            return photo != null && photo.getIntrinsicHeight() > 0 ? photo.getIntrinsicHeight() : AndroidUtilities.displaySize.x;
        }
    }

    /** Host-executed media actions; the viewer never exports or deletes files itself. */
    public enum Action { SHARE, SAVE, DELETE }

    /** The host side (upstream PhotoViewerProvider). */
    public interface Provider {
        /** Opt in per item. Evaluated when its page is displayed, the menu opens, and a row is clicked. */
        default boolean canPerformAction(Action action, int index, Item item) { return false; }
        /** Receives the original item/index captured at menu opening. Confirmation and IO belong to the host. */
        default void onAction(Action action, int index, Item item) { }
        /** A viewer-local preference changed (paused videos apply the cached speed when resumed). */
        default void onPlaybackSpeedChanged(float speed) { }
        default void onMutedChanged(boolean muted) { }
        /** The decoder rejected this speed; getPlaybackSpeed() now reports the restored effective value. */
        default void onPlaybackSpeedError(float requestedSpeed) { }

        /** Show the complete supplied gallery as one thumbnail group (e.g. a host album picker). */
        default boolean forceAllInGroup() { return false; }
        /**
         * Where item {@code index} is on screen (getLocationOnScreen coordinates) and its corner
         * radii (8 values like Path.addRoundRect, may be left untouched); false if it is not visible,
         * then the viewer fades in / out instead.
         */
        default boolean getSourceRect(int index, RectF outRect, float[] outRadii) {
            return false;
        }

        /** Hide the source photo while the viewer shows it (upstream photoImage.setVisible). */
        default void setSourceVisible(int index, boolean visible) {
        }

        default void onPageSelected(int index) {
        }

        default void onClosed() {
        }
    }

    private static final int OPEN_DURATION = 200;
    private static final float MAX_SCALE = 3.0f;

    private final Activity activity;
    private final ArrayList<Item> items;
    private final Provider provider;
    private Dialog dialog;
    private ContainerView containerView;
    private FrameLayout actionBar;
    private ActionBarMenuItem actionsMenu;
    private ActionBarMenuItem playbackMenu;
    private SpeedIconDrawable playbackSpeedIcon;
    private ActionBarMenuSlider.SpeedSlider speedSlider;
    private SpeedButtonsLayout speedButtons;
    private ActionBarMenuSubItem muteMenuItem;
    private ViewTreeObserver.OnPreDrawListener pendingPlaybackMenuOpen;
    private long playbackMenuGeneration;
    private float playbackSpeed = 1f, appliedPlaybackSpeed = 1f;
    private boolean muted;
    private ActionSnapshot actionSnapshot;
    private ViewTreeObserver.OnPreDrawListener pendingActionMenuOpen;
    private long actionGeneration;
    private static final class ActionSnapshot {
        final int index;
        final Item item;
        final long generation;
        final EnumSet<Action> allowed = EnumSet.noneOf(Action.class);
        ActionSnapshot(int index, Item item, long generation) {
            this.index = index; this.item = item; this.generation = generation;
        }
    }
    private TextView titleView, subtitleView;
    private LinearLayout bottomLayout;
    private TextView captionView;
    private FrameLayout videoControls;
    private PlayPauseView playPauseView;
    private VideoSeekBarView seekBarView;
    private TextView videoTimeView;
    private GroupedPhotosListView groupedPhotosListView;
    private int groupStart = -1, groupEnd = -1;
    private boolean selectingFromThumbnails;

    private int currentIndex;
    private int hiddenIndex = -1;
    private boolean barsVisible = true;
    private float barsAlpha = 1f;
    private boolean bottomHasContent;
    private ValueAnimator barsAnimator;
    private int insetTop, insetBottom, insetLeft, insetRight;

    // image state (PhotoViewer scale / translationX / translationY)
    private float scale = 1f, translationX, translationY;
    private float animateFromScale, animateFromX, animateFromY;
    private float animateToScale, animateToX, animateToY;
    private ValueAnimator imageMoveAnimation;
    private boolean zoomAnimation;
    private int switchImageAfterAnimation;
    private final DecelerateInterpolator interpolator = new DecelerateInterpolator(1.5f);
    private float minX, maxX, minY, maxY;

    // open / close
    private ValueAnimator transitionAnimator;
    private float transitionProgress = 1f;
    private boolean transitionHasSource;
    private boolean closing;
    private final RectF sourceRect = new RectF();
    private final float[] sourceRadii = new float[8];
    private final RectF transitionTargetRect = new RectF();
    private Drawable transitionDrawable;
    private float closeStartBackgroundAlpha = 1f;

    // touch
    private GestureDetector gestureDetector;
    private VelocityTracker velocityTracker;
    private boolean draggingDown, moving, zooming, canDragDown = true;
    private float dragX, dragY, moveStartX, moveStartY;
    private float pinchStartDistance, pinchStartScale, pinchCenterX, pinchCenterY, pinchStartX, pinchStartY;
    private boolean doubleTap;

    // video
    private TextureView textureView;
    private MediaPlayer player;
    private Surface surface;
    private boolean playerPrepared, playWhenReady, playerFinished;
    private int videoWidth, videoHeight;
    private boolean seekDragging;
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updateVideoProgress();
            if (player != null && playerPrepared && player.isPlaying()) {
                AndroidUtilities.runOnUIThread(this, 30);
            }
        }
    };

    private final Paint backgroundPaint = new Paint();
    private final Paint playButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path playPath = new Path();
    private final Path clipPath = new Path();
    private final float[] clipRadii = new float[8];
    private final RectF tmpRect = new RectF();
    private final RectF tmpRect2 = new RectF();

    private MediaViewer(Activity activity, List<Item> items, int index, Provider provider) {
        this.activity = activity;
        this.items = new ArrayList<>(items);
        this.currentIndex = Math.max(0, Math.min(index, items.size() - 1));
        this.provider = provider != null ? provider : new Provider() {
        };
    }

    /** Opens the viewer on {@code items[index]}, growing out of the provider's source rect. */
    public static MediaViewer open(Activity activity, List<Item> items, int index, Provider provider) {
        if (activity == null || items == null || items.isEmpty()) {
            return null;
        }
        MediaViewer viewer = new MediaViewer(activity, items, index, provider);
        viewer.show();
        return viewer;
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public GroupedPhotosListView getGroupedPhotosListView() { return groupedPhotosListView; }

    /** Changes page on the UI thread, preserving source visibility and releasing the old player. */
    public void setCurrentIndex(int index) {
        if (!isShowing() || closing || transitionProgress < 1f || index < 0 || index >= items.size() || index == currentIndex) return;
        cancelMoveZoomAnimation();
        switchToIndex(index);
    }

    /** Opens a fresh permission snapshot for the current page. No actions are enabled by default. */
    public void openActionsMenu() {
        if (!isShowing() || closing || transitionProgress < 1f || actionsMenu == null) return;
        dismissActionsMenu();
        final ActionSnapshot snapshot = new ActionSnapshot(currentIndex, currentItem(), actionGeneration);
        for (Action action : Action.values()) {
            boolean allowed = provider.canPerformAction(action, snapshot.index, snapshot.item);
            if (!isCurrentActionPage(snapshot)) return;
            if (allowed) snapshot.allowed.add(action);
        }
        setActionsButtonVisible(!snapshot.allowed.isEmpty());
        if (snapshot.allowed.isEmpty()) return;
        actionSnapshot = snapshot;
        actionsMenu.removeAllSubItems();
        // PhotoViewer 5613, 5633, 5639: original icons/text/48dp sub-items and fixed viewer palette.
        if (snapshot.allowed.contains(Action.SAVE)) addActionRow(snapshot, Action.SAVE, R.drawable.msg_gallery, R.string.SaveToGallery);
        if (snapshot.allowed.contains(Action.SHARE)) addActionRow(snapshot, Action.SHARE, R.drawable.msg_shareout, R.string.ShareFile);
        if (snapshot.allowed.contains(Action.DELETE)) addActionRow(snapshot, Action.DELETE, R.drawable.msg_delete, R.string.Delete);
        actionsMenu.redrawPopup(0xf9222222);
        // A previously hidden button first needs its normal 48dp action-bar layout.
        pendingActionMenuOpen = new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                containerView.getViewTreeObserver().removeOnPreDrawListener(this);
                if (pendingActionMenuOpen == this) pendingActionMenuOpen = null;
                if (!isCurrentActionSnapshot(snapshot) || !actionsMenu.isAttachedToWindow()) return true;
                actionsMenu.toggleSubMenu();
                actionsMenu.setOnMenuDismiss(byClick -> {
                    if (actionSnapshot == snapshot) actionSnapshot = null;
                });
                return true;
            }
        };
        containerView.getViewTreeObserver().addOnPreDrawListener(pendingActionMenuOpen);
        containerView.invalidate();
    }

    private void addActionRow(ActionSnapshot snapshot, Action action, int icon, int title) {
        ActionBarMenuSubItem row = actionsMenu.addSubItem(action.ordinal() + 1, icon, null,
                activity.getString(title), false, false);
        row.setColors(0xfffafafa, 0xfffafafa);
        // Capture this opening's snapshot on each row; stale detached rows cannot execute a new page's action.
        row.setOnClickListener(view -> {
            if (!isCurrentActionSnapshot(snapshot) || !actionsMenu.isSubMenuShowing() || !snapshot.allowed.contains(action)) return;
            boolean allowed = provider.canPerformAction(action, snapshot.index, snapshot.item);
            if (!isCurrentActionSnapshot(snapshot)) return;
            dismissActionsMenu(true); // invalidate before calling host code, which may close/reopen/change page
            if (allowed) provider.onAction(action, snapshot.index, snapshot.item);
        });
    }

    private boolean isCurrentActionPage(ActionSnapshot snapshot) {
        return isShowing() && !closing && transitionProgress >= 1f && actionGeneration == snapshot.generation
                && currentIndex == snapshot.index && currentItem() == snapshot.item;
    }
    private boolean isCurrentActionSnapshot(ActionSnapshot snapshot) {
        return actionSnapshot == snapshot && isCurrentActionPage(snapshot);
    }
    private void dismissActionsMenu() { dismissActionsMenu(false); }
    private void dismissActionsMenu(boolean animated) {
        dismissPlaybackMenu(false);
        actionSnapshot = null; actionGeneration++;
        if (pendingActionMenuOpen != null) {
            if (containerView != null && containerView.getViewTreeObserver().isAlive()) containerView.getViewTreeObserver().removeOnPreDrawListener(pendingActionMenuOpen);
            pendingActionMenuOpen = null;
        }
        if (actionsMenu != null && actionsMenu.isSubMenuShowing()) {
            // Keep the upstream row-dismiss animation; retire page/menu sessions immediately.
            if (!animated) actionsMenu.setPopupAnimationEnabled(false);
            actionsMenu.closeSubMenu();
            if (!animated) actionsMenu.setPopupAnimationEnabled(true);
        }
    }
    private void updateActionsButton() {
        if (actionsMenu == null || closing) return;
        int index = currentIndex;
        Item item = currentItem();
        long generation = actionGeneration;
        boolean available = false;
        for (Action action : Action.values()) {
            available |= provider.canPerformAction(action, index, item);
            if (closing || !isShowing() || currentIndex != index || currentItem() != item || generation != actionGeneration) return;
        }
        setActionsButtonVisible(available);
    }
    private void setActionsButtonVisible(boolean visible) {
        actionsMenu.setVisibility(visible ? View.VISIBLE : View.GONE);
        View titles = (View) titleView.getParent();
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) titles.getLayoutParams();
        boolean video = currentItem().video != null;
        if (playbackMenu != null) {
            playbackMenu.setVisibility(video ? View.VISIBLE : View.GONE);
            FrameLayout.LayoutParams menuParams = (FrameLayout.LayoutParams) playbackMenu.getLayoutParams();
            menuParams.rightMargin = dp(visible ? 48 : 0); playbackMenu.setLayoutParams(menuParams);
        }
        int margin = dp(16 + (visible ? 48 : 0) + (video ? 48 : 0));
        if (params.rightMargin != margin) { params.rightMargin = margin; titles.setLayoutParams(params); }
    }

    public boolean isPlaybackSpeedSupported() { return Build.VERSION.SDK_INT >= 23; }
    public float getPlaybackSpeed() { return playbackSpeed; }
    public boolean isMuted() { return muted; }

    /** Accepts finite 0.2–3x speeds on API 23+. Paused/preparing players only cache the preference. */
    public boolean setPlaybackSpeed(float speed) {
        if (closing || !isShowing() || !isPlaybackSpeedSupported() || Float.isNaN(speed) || Float.isInfinite(speed) || speed < .2f || speed > 3f) return false;
        if (playbackSpeed == speed) return true;
        float previous = playbackSpeed;
        playbackSpeed = speed;
        MediaPlayer current = player;
        if (current != null && playerPrepared && playWhenReady && !playerFinished) {
            if (!applyPlaybackSpeed(current)) return false;
            if (closing || current != player) return false;
        }
        updatePlaybackMenuState(true);
        if (previous != playbackSpeed) provider.onPlaybackSpeedChanged(playbackSpeed);
        return true;
    }

    public void setMuted(boolean value) {
        if (closing || !isShowing() || muted == value) return;
        MediaPlayer current = player;
        try { if (current != null) current.setVolume(value ? 0f : 1f, value ? 0f : 1f); }
        catch (RuntimeException error) { FileLog.e(error); return; }
        muted = value; updatePlaybackMenuState(true);
        provider.onMutedChanged(value);
    }

    /** Uses the original PhotoViewer speed slider/buttons; the mute row controls viewing only. */
    public void openPlaybackMenu() {
        if (closing || !isShowing() || transitionProgress < 1f || currentItem().video == null) return;
        dismissActionsMenu();
        final long generation = playbackMenuGeneration;
        final int index = currentIndex;
        final Item item = currentItem();
        playbackMenu.removeAllSubItems();
        speedSlider = null; speedButtons = null;
        if (isPlaybackSpeedSupported()) {
            speedSlider = new ActionBarMenuSlider.SpeedSlider(activity, null) {
                @Override public boolean performAccessibilityAction(int action, android.os.Bundle arguments) {
                    boolean handled = super.performAccessibilityAction(action, arguments);
                    if (handled && validPlaybackMenu(generation, index, item)) {
                        setPlaybackSpeed(getSpeed());
                        if (validPlaybackMenu(generation, index, item)) updatePlaybackMenuState(true);
                    }
                    return handled;
                }
            };
            speedSlider.setStops(new float[] { .5f, 1f, 1.5f, 2f, 2.5f });
            speedSlider.setMinimumWidth(dp(196)); speedSlider.setDrawShadow(false);
            speedSlider.setBackgroundColor(0xff222222); speedSlider.setTextColor(0xffffffff);
            speedSlider.setLabel(activity.getString(R.string.VideoPlayerSpeed));
            speedSlider.setOnValueChange((value, isFinal) -> {
                if (!validPlaybackMenu(generation, index, item)) return;
                setPlaybackSpeed(ActionBarMenuSlider.SpeedSlider.MIN_SPEED +
                        (ActionBarMenuSlider.SpeedSlider.MAX_SPEED - ActionBarMenuSlider.SpeedSlider.MIN_SPEED) * value);
                if (validPlaybackMenu(generation, index, item)) updatePlaybackMenuState(isFinal);
            });
            playbackMenu.getPopupLayout().addView(speedSlider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
            playbackMenu.addColoredGap().setColor(0xff181818);
            speedButtons = new SpeedButtonsLayout(activity, (speed, isFinal, closeMenu) -> {
                if (!validPlaybackMenu(generation, index, item)) return;
                setPlaybackSpeed(speed);
                if (validPlaybackMenu(generation, index, item) && closeMenu) dismissPlaybackMenu(true);
            });
            playbackMenu.getPopupLayout().addView(speedButtons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        muteMenuItem = playbackMenu.addSubItem(101, muted ? R.drawable.video_send_unmute : R.drawable.video_send_mute,
                null, activity.getString(muted ? R.string.Unmute : R.string.Mute), false, false);
        muteMenuItem.setColors(0xfffafafa, 0xfffafafa);
        muteMenuItem.setOnClickListener(view -> {
            if (!validPlaybackMenu(generation, index, item)) return;
            setMuted(!muted);
            if (validPlaybackMenu(generation, index, item)) dismissPlaybackMenu(true);
        });
        updatePlaybackMenuState(true);
        playbackMenu.redrawPopup(0xf9222222);
        pendingPlaybackMenuOpen = new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                containerView.getViewTreeObserver().removeOnPreDrawListener(this);
                if (pendingPlaybackMenuOpen == this) pendingPlaybackMenuOpen = null;
                if (!validPlaybackPage(generation, index, item) || !playbackMenu.isAttachedToWindow()) return true;
                playbackMenu.toggleSubMenu();
                playbackMenu.setOnMenuDismiss(byClick -> { if (generation == playbackMenuGeneration) playbackMenuGeneration++; });
                return true;
            }
        };
        containerView.getViewTreeObserver().addOnPreDrawListener(pendingPlaybackMenuOpen);
        containerView.invalidate();
    }
    private boolean validPlaybackPage(long generation, int index, Item item) {
        return !closing && isShowing() && transitionProgress >= 1f && playbackMenuGeneration == generation
                && currentIndex == index && currentItem() == item;
    }
    private boolean validPlaybackMenu(long generation, int index, Item item) {
        return validPlaybackPage(generation, index, item) && playbackMenu.isSubMenuShowing();
    }
    private void dismissPlaybackMenu(boolean animated) {
        playbackMenuGeneration++;
        if (pendingPlaybackMenuOpen != null) {
            if (containerView != null && containerView.getViewTreeObserver().isAlive()) containerView.getViewTreeObserver().removeOnPreDrawListener(pendingPlaybackMenuOpen);
            pendingPlaybackMenuOpen = null;
        }
        if (playbackMenu != null && playbackMenu.isSubMenuShowing()) {
            if (!animated) playbackMenu.setPopupAnimationEnabled(false);
            playbackMenu.closeSubMenu();
            if (!animated) playbackMenu.setPopupAnimationEnabled(true);
        }
    }
    private void updatePlaybackMenuState(boolean isFinal) {
        if (playbackSpeedIcon != null) playbackSpeedIcon.setValue(playbackSpeed, false);
        if (speedSlider != null) speedSlider.setSpeed(playbackSpeed, false);
        if (speedButtons != null) speedButtons.update(playbackSpeed, isFinal);
        if (muteMenuItem != null) muteMenuItem.setTextAndIcon(activity.getString(muted ? R.string.Unmute : R.string.Mute),
                muted ? R.drawable.video_send_unmute : R.drawable.video_send_mute);
    }
    private boolean applyPlaybackSpeed(MediaPlayer current) {
        if (Build.VERSION.SDK_INT < 23 || current != player || !playerPrepared || closing) return Build.VERSION.SDK_INT < 23;
        float requested = playbackSpeed, previous = appliedPlaybackSpeed;
        try {
            current.setPlaybackParams(new PlaybackParams().setPitch(1f).setSpeed(requested));
            appliedPlaybackSpeed = requested;
            return true;
        } catch (RuntimeException error) {
            FileLog.e(error);
            playbackSpeed = previous;
            try { current.setPlaybackParams(new PlaybackParams().setPitch(1f).setSpeed(previous)); }
            catch (RuntimeException restoreError) { FileLog.e(restoreError); if (current == player) { releasePlayer(); playPauseView.setPlaying(false); } }
            updatePlaybackMenuState(true);
            provider.onPlaybackSpeedError(requested);
            // The host may close or replace this player from the error callback.
            if (!closing && current == player && playbackSpeed == previous && appliedPlaybackSpeed == previous && requested != previous) provider.onPlaybackSpeedChanged(previous);
            return false;
        }
    }
    private boolean startPlayer(MediaPlayer current) {
        if (current != player || !playerPrepared || closing) return false;
        playWhenReady = true;
        try {
            current.setVolume(muted ? 0f : 1f, muted ? 0f : 1f);
            if (Build.VERSION.SDK_INT >= 23) applyPlaybackSpeed(current);
            // Error callbacks may close/change page or intentionally pause this same player.
            if (current != player || closing || !playWhenReady) return false;
            current.start();
            return true;
        } catch (RuntimeException error) {
            FileLog.e(error);
            if (current == player) { releasePlayer(); playPauseView.setPlaying(false); }
            return false;
        }
    }

    // ---------------------------------------------------------------- window

    private void show() {
        containerView = new ContainerView(activity);
        createBars();
        gestureDetector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                return onSingleTap(e);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                return MediaViewer.this.onDoubleTap(e);
            }
        });

        dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar) {
            @Override
            public void onBackPressed() {
                close(true);
            }
        };
        dialog.setContentView(containerView);
        dialog.setCancelable(true);
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                close(true);
                return true;
            }
            return keyCode == KeyEvent.KEYCODE_BACK;
        });
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.setWindowAnimations(0);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(0);
            window.setNavigationBarColor(0);
            if (Build.VERSION.SDK_INT >= 29) {
                window.setNavigationBarContrastEnforced(false);
                window.setStatusBarContrastEnforced(false);
            }
            if (Build.VERSION.SDK_INT >= 28) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                window.setAttributes(params);
            }
            if (Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false);
            }
            // white status / navigation bar icons on the black viewer
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        transitionProgress = 0f;
        containerView.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                containerView.getViewTreeObserver().removeOnPreDrawListener(this);
                startOpenAnimation();
                return true;
            }
        });
        dialog.show();
        updatePage();
    }

    private void createBars() {
        // upstream actionBar: 0x7f000000, white back arrow, title = sender, subtitle = date
        actionBar = new FrameLayout(activity);
        actionBar.setBackgroundColor(0x7f000000);
        actionBar.setClickable(true);
        ImageView backButton = new ImageView(activity);
        backButton.setImageResource(R.drawable.ic_ab_back);
        backButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backButton.setBackground(Theme.createSelectorDrawable(0x40ffffff));
        backButton.setOnClickListener(v -> close(true));
        backButton.setContentDescription("Back");
        actionBar.addView(backButton, LayoutHelper.createFrame(54, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_VERTICAL);
        titleView = new TextView(activity);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        subtitleView = new TextView(activity);
        subtitleView.setTextColor(0xffd7d7d7);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));
        actionBar.addView(titles, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 72, 0, 16, 0));
        // PhotoViewer 5572: keep the transplanted menu item inside this viewer's existing top bar.
        actionsMenu = new ActionBarMenuItem(activity, null, Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, Color.WHITE);
        actionsMenu.setIcon(R.drawable.media_more);
        actionsMenu.setContentDescription(activity.getString(R.string.AccDescrMoreOptions));
        actionsMenu.setShowSubmenuByMove(false);
        actionsMenu.setLongClickEnabled(false);
        actionsMenu.setOnClickListener(view -> {
            if (actionsMenu.isSubMenuShowing()) dismissActionsMenu(true);
            else openActionsMenu();
        });
        actionsMenu.setVisibility(View.GONE);
        actionBar.addView(actionsMenu, LayoutHelper.createFrame(48, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP));
        playbackSpeedIcon = new SpeedIconDrawable(); playbackSpeedIcon.setColor(Color.WHITE); playbackSpeedIcon.setValue(1f, false);
        playbackMenu = new ActionBarMenuItem(activity, null, Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, Color.WHITE);
        playbackMenu.setIcon(playbackSpeedIcon);
        playbackMenu.setContentDescription(activity.getString(R.string.VideoPlayerSpeed));
        playbackMenu.setShowSubmenuByMove(false); playbackMenu.setLongClickEnabled(false);
        playbackMenu.setOnClickListener(view -> { if (playbackMenu.isSubMenuShowing()) dismissPlaybackMenu(true); else openPlaybackMenu(); });
        playbackMenu.setVisibility(View.GONE);
        actionBar.addView(playbackMenu, LayoutHelper.createFrame(48, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP));
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // bottomLayout: caption above the video controls, one 0x7f000000 panel down to the navigation bar
        bottomLayout = new LinearLayout(activity);
        bottomLayout.setOrientation(LinearLayout.VERTICAL);
        bottomLayout.setBackgroundColor(0x7f000000);
        captionView = new TextView(activity);
        captionView.setTextColor(Color.WHITE);
        captionView.setLinkTextColor(0xff79c4fc);
        captionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        captionView.setMaxLines(10);
        captionView.setEllipsize(TextUtils.TruncateAt.END);
        captionView.setPadding(dp(16), dp(8), dp(16), dp(8));
        bottomLayout.addView(captionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // PhotoViewer 5681: top padding 10dp, 68dp strip above the existing bottom controls.
        groupedPhotosListView = new GroupedPhotosListView(activity, dp(10));
        groupedPhotosListView.setDrawBackground(false); // bottomLayout already supplies 0x7f000000.
        groupedPhotosListView.setDelegate(new GroupedPhotosListView.Delegate() {
            @Override public void onIndexSelected(int index) {
                if (closing || transitionProgress < 1f) return;
                selectingFromThumbnails = groupedPhotosListView.isScrolling();
                try { setCurrentIndex(index); } finally { selectingFromThumbnails = false; }
            }
            @Override public void onHideAnimationEnd() { updateThumbnailVisibility(); }
            @Override public void onStopScrolling() {
                if (!closing && currentItem().video != null && !playerPrepared) preparePlayer(true);
            }
        });
        bottomLayout.addView(groupedPhotosListView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 68));

        videoControls = new FrameLayout(activity);
        videoControls.setClickable(true);
        playPauseView = new PlayPauseView(activity);
        playPauseView.setBackground(Theme.createSelectorDrawable(0x40ffffff, 1, dp(20)));
        playPauseView.setOnClickListener(v -> togglePlay());
        videoControls.addView(playPauseView, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL, 4, 0, 0, 0));
        seekBarView = new VideoSeekBarView(activity);
        videoControls.addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL, 52, 0, 104, 0));
        videoTimeView = new TextView(activity);
        videoTimeView.setTextColor(Color.WHITE);
        videoTimeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        videoTimeView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        videoTimeView.setSingleLine(true);
        videoControls.addView(videoTimeView, LayoutHelper.createFrame(96, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
        bottomLayout.addView(videoControls, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        containerView.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        containerView.setOnApplyWindowInsetsListener((v, insets) -> {
            insetTop = insets.getSystemWindowInsetTop();
            insetBottom = insets.getSystemWindowInsetBottom();
            insetLeft = insets.getSystemWindowInsetLeft();
            insetRight = insets.getSystemWindowInsetRight();
            if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                insetTop = Math.max(insetTop, insets.getDisplayCutout().getSafeInsetTop());
            }
            applyInsets();
            if (Build.VERSION.SDK_INT >= 30) {
                return WindowInsets.CONSUMED;
            }
            return insets.consumeSystemWindowInsets();
        });
    }

    private void applyInsets() {
        actionBar.setPadding(insetLeft, insetTop, insetRight, 0);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) actionBar.getLayoutParams();
        lp.height = insetTop + ActionBar.getCurrentActionBarHeight();
        actionBar.setLayoutParams(lp);
        bottomLayout.setPadding(insetLeft, 0, insetRight, insetBottom);
    }

    // ---------------------------------------------------------------- pages

    private Item currentItem() {
        return items.get(currentIndex);
    }

    private void updatePage() {
        dismissActionsMenu();
        Item item = currentItem();
        titleView.setText(item.sender);
        CharSequence subtitle = item.date;
        if (items.size() > 1) {
            String counter = (currentIndex + 1) + " of " + items.size();
            subtitle = TextUtils.isEmpty(subtitle) ? counter : subtitle + " · " + counter;
        }
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);
        if (TextUtils.isEmpty(item.caption)) {
            captionView.setVisibility(View.GONE);
        } else {
            captionView.setText(Emoji.replaceEmoji(item.caption, captionView.getPaint().getFontMetricsInt(), false));
            captionView.setVisibility(View.VISIBLE);
        }
        videoControls.setVisibility(item.video != null ? View.VISIBLE : View.GONE);
        updateThumbnailGroup();
        updateThumbnailVisibility();
        applyBarsAlpha();
        if (!closing) {
            if (hiddenIndex != currentIndex) {
                if (hiddenIndex >= 0) {
                    provider.setSourceVisible(hiddenIndex, true);
                }
                hiddenIndex = currentIndex;
                provider.setSourceVisible(currentIndex, false);
            }
        }
        releasePlayer();
        videoWidth = videoHeight = 0;
        playerFinished = false;
        updateVideoProgress();
        playPauseView.setPlaying(false);
        if (item.video != null && transitionProgress >= 1f && !selectingFromThumbnails) {
            preparePlayer(true);
        }
        containerView.invalidate();
        updateActionsButton();
    }

    private void updateThumbnailGroup() {
        int start = currentIndex, end = currentIndex;
        long group = currentItem().groupId;
        if (provider.forceAllInGroup()) {
            start = 0; end = items.size() - 1;
        } else if (group != 0 && groupStart >= 0 && currentIndex >= groupStart && currentIndex <= groupEnd
                && items.get(groupStart).groupId == group && items.get(groupEnd).groupId == group) {
            start = groupStart; end = groupEnd;
        } else if (group != 0) {
            // Upstream only examines adjacent members, at most ten on either side.
            int min = Math.max(0, currentIndex - 10), max = Math.min(items.size() - 1, currentIndex + 9);
            while (start > min && items.get(start - 1).groupId == group) start--;
            while (end < max && items.get(end + 1).groupId == group) end++;
        }
        if (start == end) {
            if (groupStart != -1) groupedPhotosListView.setItems(java.util.Collections.emptyList(), -1);
            groupStart = groupEnd = -1;
        } else if (start != groupStart || end != groupEnd) {
            ArrayList<GroupedPhotosListView.Entry> thumbnails = new ArrayList<>();
            for (int i = start; i <= end; i++) {
                Item item = items.get(i);
                thumbnails.add(new GroupedPhotosListView.Entry(i, item.photo, item.mediaWidth(), item.mediaHeight()));
            }
            groupStart = start; groupEnd = end;
            groupedPhotosListView.setItems(thumbnails, currentIndex);
        } else {
            groupedPhotosListView.setCurrentIndex(currentIndex);
        }
    }

    private void updateThumbnailVisibility() {
        boolean portrait = containerView.getHeight() == 0 || containerView.getHeight() > containerView.getWidth();
        boolean visible = groupedPhotosListView.getCount() > 1 && (AndroidUtilities.isTablet() || portrait);
        groupedPhotosListView.setVisibility(visible ? View.VISIBLE : View.GONE);
        bottomHasContent = captionView.getVisibility() == View.VISIBLE || currentItem().video != null || visible;
        applyBarsAlpha();
    }

    private void switchToIndex(int index) {
        currentIndex = index;
        scale = 1f;
        translationX = 0;
        translationY = 0;
        updateMinMax(scale);
        updatePage();
        provider.onPageSelected(index);
    }

    private void goToNext() {
        float extra = Math.max(0, fitWidth(currentItem()) * scale / 2f - containerWidth() / 2f);
        switchImageAfterAnimation = 1;
        animateTo(scale, minX - containerWidth() - extra - pageGap(), translationY, false);
    }

    private void goToPrev() {
        float extra = Math.max(0, fitWidth(currentItem()) * scale / 2f - containerWidth() / 2f);
        switchImageAfterAnimation = 2;
        animateTo(scale, maxX + containerWidth() + extra + pageGap(), translationY, false);
    }

    private static int pageGap() {
        return dp(30) / 2;
    }

    // ---------------------------------------------------------------- geometry

    private int containerWidth() {
        return containerView.getWidth();
    }

    private int containerHeight() {
        return containerView.getHeight();
    }

    private float fitScale(Item item) {
        return Math.min(containerWidth() / (float) item.mediaWidth(), containerHeight() / (float) item.mediaHeight());
    }

    private float fitWidth(Item item) {
        return item.mediaWidth() * fitScale(item);
    }

    private float fitHeight(Item item) {
        return item.mediaHeight() * fitScale(item);
    }

    /** updateMinMax: how far a zoomed image may move before its edges leave the screen. */
    private void updateMinMax(float s) {
        Item item = currentItem();
        float w = fitWidth(item) * s, h = fitHeight(item) * s;
        maxX = Math.max(0, (w - containerWidth()) / 2f);
        minX = -maxX;
        maxY = Math.max(0, (h - containerHeight()) / 2f);
        minY = -maxY;
    }

    /** The current image's rect on screen for scale / translation. */
    private void getDisplayRect(RectF out) {
        Item item = currentItem();
        float w = fitWidth(item) * scale, h = fitHeight(item) * scale;
        float cx = containerWidth() / 2f + translationX, cy = containerHeight() / 2f + translationY;
        out.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
    }

    private boolean readSourceRect(int index) {
        for (int i = 0; i < 8; i++) {
            sourceRadii[i] = 0;
        }
        if (!provider.getSourceRect(index, sourceRect, sourceRadii) || sourceRect.isEmpty()) {
            return false;
        }
        int[] location = new int[2];
        containerView.getLocationOnScreen(location);
        sourceRect.offset(-location[0], -location[1]);
        return true;
    }

    // ---------------------------------------------------------------- open / close

    private void startOpenAnimation() {
        transitionHasSource = readSourceRect(currentIndex);
        getDisplayRect(transitionTargetRect);
        transitionDrawable = currentItem().photo;
        transitionAnimator = ValueAnimator.ofFloat(0f, 1f);
        transitionAnimator.addUpdateListener(a -> {
            transitionProgress = (float) a.getAnimatedValue();
            applyBarsAlpha();
            containerView.invalidate();
        });
        transitionAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                transitionAnimator = null;
                transitionProgress = 1f;
                transitionDrawable = null;
                containerView.invalidate();
                if (currentItem().video != null && player == null) {
                    preparePlayer(true);
                }
            }
        });
        transitionAnimator.setDuration(OPEN_DURATION);
        transitionAnimator.setInterpolator(new DecelerateInterpolator());
        transitionAnimator.start();
        applyBarsAlpha();
    }

    /** closePhoto: back into the source rect when the provider still has it, else a fade. */
    public void close(boolean animated) {
        if (closing || dialog == null) {
            return;
        }
        closing = true;
        dismissActionsMenu();
        cancelMoveZoomAnimation();
        if (transitionAnimator != null) {
            transitionAnimator.cancel();
            transitionAnimator = null;
        }
        // the frame on screen right now animates, not the thumbnail
        Drawable frame = currentItem().photo;
        if (textureView != null && playerPrepared && videoWidth > 0) {
            try {
                Bitmap bitmap = textureView.getBitmap();
                if (bitmap != null) {
                    frame = new BitmapDrawable(activity.getResources(), bitmap);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        releasePlayer();
        if (!animated || containerWidth() == 0) {
            finish();
            return;
        }
        transitionDrawable = frame;
        closeStartBackgroundAlpha = backgroundAlpha() / 255f;
        getDisplayRect(transitionTargetRect);
        transitionHasSource = readSourceRect(currentIndex);
        transitionAnimator = ValueAnimator.ofFloat(1f, 0f);
        transitionAnimator.addUpdateListener(a -> {
            transitionProgress = (float) a.getAnimatedValue();
            applyBarsAlpha();
            containerView.invalidate();
        });
        transitionAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                finish();
            }
        });
        transitionAnimator.setDuration(OPEN_DURATION);
        transitionAnimator.setInterpolator(new DecelerateInterpolator());
        transitionAnimator.start();
    }

    private void finish() {
        dismissActionsMenu();
        groupedPhotosListView.destroy();
        if (hiddenIndex >= 0) {
            provider.setSourceVisible(hiddenIndex, true);
            hiddenIndex = -1;
        }
        releasePlayer();
        AndroidUtilities.cancelRunOnUIThread(progressUpdater);
        if (dialog != null) {
            try {
                dialog.dismiss();
            } catch (Exception e) {
                FileLog.e(e);
            }
            dialog = null;
        }
        provider.onClosed();
    }

    // ---------------------------------------------------------------- bars

    private void toggleBars() {
        dismissActionsMenu();
        barsVisible = !barsVisible;
        if (barsAnimator != null) {
            barsAnimator.cancel();
        }
        barsAnimator = ValueAnimator.ofFloat(barsAlpha, barsVisible ? 1f : 0f);
        barsAnimator.addUpdateListener(a -> {
            barsAlpha = (float) a.getAnimatedValue();
            applyBarsAlpha();
        });
        barsAnimator.setDuration(200);
        barsAnimator.start();
    }

    private void applyBarsAlpha() {
        float alpha = barsAlpha * transitionProgress;
        if (draggingDown || translationY != 0 && scale == 1f && !zoomAnimation) {
            alpha *= Math.max(0, 1f - Math.abs(translationY) / (containerWidth() / 4f));
        }
        actionBar.setAlpha(alpha);
        bottomLayout.setAlpha(alpha);
        actionBar.setVisibility(alpha > 0 ? View.VISIBLE : View.INVISIBLE);
        bottomLayout.setVisibility(alpha > 0 && bottomHasContent ? View.VISIBLE : View.INVISIBLE);
    }

    // ---------------------------------------------------------------- image animation

    private void animateTo(float newScale, float newTx, float newTy, boolean isZoom) {
        if (scale == newScale && translationX == newTx && translationY == newTy) {
            onMoveAnimationEnd();
            return;
        }
        cancelMoveZoomAnimation();
        zoomAnimation = isZoom;
        animateFromScale = scale;
        animateFromX = translationX;
        animateFromY = translationY;
        animateToScale = newScale;
        animateToX = newTx;
        animateToY = newTy;
        imageMoveAnimation = ValueAnimator.ofFloat(0f, 1f);
        imageMoveAnimation.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            scale = animateFromScale + (animateToScale - animateFromScale) * v;
            translationX = animateFromX + (animateToX - animateFromX) * v;
            translationY = animateFromY + (animateToY - animateFromY) * v;
            onTransformChanged();
        });
        imageMoveAnimation.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (imageMoveAnimation != animation) {
                    return;
                }
                imageMoveAnimation = null;
                if (!cancelled) {
                    scale = animateToScale;
                    translationX = animateToX;
                    translationY = animateToY;
                    onMoveAnimationEnd();
                }
            }
        });
        imageMoveAnimation.setInterpolator(interpolator);
        imageMoveAnimation.setDuration(250);
        imageMoveAnimation.start();
    }

    private void onMoveAnimationEnd() {
        zoomAnimation = false;
        if (switchImageAfterAnimation == 1) {
            switchImageAfterAnimation = 0;
            switchToIndex(currentIndex + 1);
        } else if (switchImageAfterAnimation == 2) {
            switchImageAfterAnimation = 0;
            switchToIndex(currentIndex - 1);
        }
        updateMinMax(scale);
        onTransformChanged();
    }

    private void cancelMoveZoomAnimation() {
        if (imageMoveAnimation == null) {
            return;
        }
        ValueAnimator animation = imageMoveAnimation;
        imageMoveAnimation = null;
        animation.cancel();
        switchImageAfterAnimation = 0;
        zoomAnimation = false;
        updateMinMax(scale);
    }

    private void checkMinMax(boolean zoom) {
        float moveToX = translationX;
        float moveToY = translationY;
        updateMinMax(scale);
        if (translationX < minX) {
            moveToX = minX;
        } else if (translationX > maxX) {
            moveToX = maxX;
        }
        if (translationY < minY) {
            moveToY = minY;
        } else if (translationY > maxY) {
            moveToY = maxY;
        }
        animateTo(scale, moveToX, moveToY, zoom);
    }

    private void onTransformChanged() {
        if (groupedPhotosListView != null && !zooming && !zoomAnimation && scale >= 1f) {
            float progress = translationX > maxX + dp(5) ? (translationX - maxX) / containerWidth()
                    : translationX < minX - dp(5) ? (translationX - minX) / containerWidth() : 0;
            groupedPhotosListView.setMoveProgress(Math.max(-1f, Math.min(1f, progress)));
        }
        updateTextureTransform();
        applyBarsAlpha();
        containerView.invalidate();
    }

    // ---------------------------------------------------------------- touches

    private boolean onSingleTap(MotionEvent e) {
        if (currentItem().video != null && isPlayButtonVisible() && scale == 1f) {
            float cx = containerWidth() / 2f, cy = containerHeight() / 2f;
            if (Math.hypot(e.getX() - cx, e.getY() - cy) < dp(40)) {
                togglePlay();
                return true;
            }
        }
        toggleBars();
        return true;
    }

    private boolean onDoubleTap(MotionEvent e) {
        if (scale == 1.0f && (translationY != 0 || translationX != 0) || transitionProgress < 1f) {
            return false;
        }
        if (scale == 1.0f) {
            float atx = (e.getX() - containerWidth() / 2f) - ((e.getX() - containerWidth() / 2f) - translationX) * (MAX_SCALE / scale);
            float aty = (e.getY() - containerHeight() / 2f) - ((e.getY() - containerHeight() / 2f) - translationY) * (MAX_SCALE / scale);
            updateMinMax(MAX_SCALE);
            atx = Math.max(minX, Math.min(maxX, atx));
            aty = Math.max(minY, Math.min(maxY, aty));
            animateTo(MAX_SCALE, atx, aty, true);
        } else {
            animateTo(1.0f, 0, 0, true);
        }
        doubleTap = true;
        return true;
    }

    private boolean processTouchEvent(MotionEvent ev) {
        if (closing || transitionProgress < 1f || switchImageAfterAnimation != 0) {
            return true;
        }
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            doubleTap = false;
        }
        gestureDetector.onTouchEvent(ev);
        if (doubleTap) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                doubleTap = false;
                moving = zooming = draggingDown = false;
            }
            return true;
        }
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(ev);

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (ev.getPointerCount() == 1) {
                cancelMoveZoomAnimation();
                velocityTracker.clear();
                velocityTracker.addMovement(ev);
                dragX = moveStartX = ev.getX();
                dragY = moveStartY = ev.getY();
                draggingDown = false;
                moving = false;
                zooming = false;
                canDragDown = true;
            } else if (ev.getPointerCount() == 2 && !draggingDown) {
                cancelMoveZoomAnimation();
                zooming = true;
                moving = false;
                pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));
                pinchStartScale = scale;
                pinchCenterX = (ev.getX(0) + ev.getX(1)) / 2.0f;
                pinchCenterY = (ev.getY(0) + ev.getY(1)) / 2.0f;
                pinchStartX = translationX;
                pinchStartY = translationY;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (zooming && ev.getPointerCount() >= 2 && pinchStartDistance > 0) {
                float distance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));
                float newScale = Math.max(0.5f, Math.min(MAX_SCALE * 2, distance / pinchStartDistance * pinchStartScale));
                translationX = (pinchCenterX - containerWidth() / 2f) - ((pinchCenterX - containerWidth() / 2f) - pinchStartX) * (newScale / pinchStartScale);
                translationY = (pinchCenterY - containerHeight() / 2f) - ((pinchCenterY - containerHeight() / 2f) - pinchStartY) * (newScale / pinchStartScale);
                scale = newScale;
                updateMinMax(scale);
                onTransformChanged();
            } else if (ev.getPointerCount() == 1 && !zooming) {
                float dx = Math.abs(ev.getX() - dragX);
                float dy = Math.abs(ev.getY() - dragY);
                if (canDragDown && !draggingDown && !moving && scale == 1 && dy >= dp(30) && dy / 2 > dx) {
                    draggingDown = true;
                    dragY = ev.getY();
                } else if (draggingDown) {
                    translationY = ev.getY() - dragY;
                    onTransformChanged();
                } else {
                    float moveDx = moveStartX - ev.getX();
                    float moveDy = moveStartY - ev.getY();
                    if (moving || scale == 1 && Math.abs(moveDy) + dp(12) < Math.abs(moveDx) || scale != 1 && (Math.abs(moveDx) > dp(3) || Math.abs(moveDy) > dp(3))) {
                        if (!moving) {
                            moveDx = 0;
                            moveDy = 0;
                            moving = true;
                            canDragDown = false;
                        }
                        moveStartX = ev.getX();
                        moveStartY = ev.getY();
                        updateMinMax(scale);
                        // rubber band past the edges unless a neighbour page is there
                        if (translationX < minX && currentIndex + 1 >= items.size() || translationX > maxX && currentIndex == 0) {
                            moveDx /= 3.0f;
                        }
                        if (scale == 1) {
                            moveDy = 0;
                        } else if (translationY < minY || translationY > maxY) {
                            moveDy /= 3.0f;
                        }
                        translationX -= moveDx;
                        translationY -= moveDy;
                        onTransformChanged();
                    }
                }
            }
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            if (zooming) {
                zooming = false;
                endZoom();
                // the remaining finger continues as a pan
                int remaining = ev.getActionIndex() == 0 ? 1 : 0;
                moveStartX = ev.getX(remaining);
                moveStartY = ev.getY(remaining);
                moving = scale != 1;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (zooming) {
                zooming = false;
                endZoom();
            } else if (draggingDown) {
                draggingDown = false;
                if (Math.abs(dragY - ev.getY()) > containerHeight() / 6.0f) {
                    close(true);
                } else {
                    animateTo(1, 0, 0, false);
                }
            } else if (moving) {
                moving = false;
                canDragDown = true;
                updateMinMax(scale);
                float velocity = 0;
                if (scale == 1) {
                    velocityTracker.computeCurrentVelocity(1000);
                    velocity = velocityTracker.getXVelocity();
                }
                if ((translationX < minX - containerWidth() / 3f || velocity < -dp(650)) && currentIndex + 1 < items.size()) {
                    goToNext();
                } else if ((translationX > maxX + containerWidth() / 3f || velocity > dp(650)) && currentIndex > 0) {
                    goToPrev();
                } else {
                    checkMinMax(false);
                }
            }
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
        }
        return true;
    }

    private void endZoom() {
        if (scale < 1.0f) {
            updateMinMax(1.0f);
            animateTo(1.0f, 0, 0, true);
        } else if (scale > MAX_SCALE) {
            float atx = (pinchCenterX - containerWidth() / 2f) - ((pinchCenterX - containerWidth() / 2f) - pinchStartX) * (MAX_SCALE / pinchStartScale);
            float aty = (pinchCenterY - containerHeight() / 2f) - ((pinchCenterY - containerHeight() / 2f) - pinchStartY) * (MAX_SCALE / pinchStartScale);
            updateMinMax(MAX_SCALE);
            atx = Math.max(minX, Math.min(maxX, atx));
            aty = Math.max(minY, Math.min(maxY, aty));
            animateTo(MAX_SCALE, atx, aty, true);
        } else {
            checkMinMax(true);
        }
    }

    // ---------------------------------------------------------------- drawing

    private int backgroundAlpha() {
        if (scale == 1 && !zoomAnimation && translationY != 0) {
            float maxValue = containerWidth() / 4.0f;
            return (int) Math.max(127, 255 * (1.0f - (Math.min(Math.abs(translationY), maxValue) / maxValue)));
        }
        return 255;
    }

    private void drawViewer(Canvas canvas) {
        int width = containerWidth(), height = containerHeight();
        if (width == 0 || height == 0) {
            return;
        }
        if (transitionAnimator != null || transitionProgress < 1f) {
            drawTransition(canvas);
            return;
        }
        backgroundPaint.setColor(Color.BLACK);
        backgroundPaint.setAlpha(backgroundAlpha());
        canvas.drawRect(0, 0, width, height, backgroundPaint);

        Item item = currentItem();
        getDisplayRect(tmpRect);
        if (textureView == null || videoWidth == 0) {
            drawMedia(canvas, item.photo, tmpRect);
        }

        // neighbours, scale 1, one container width (plus the gap) beside the current page's edge
        float currentHalf = Math.max(tmpRect.width() / 2f, width / 2f);
        if (currentIndex + 1 < items.size()) {
            float cx = width / 2f + translationX + currentHalf + pageGap() + width / 2f;
            drawNeighbour(canvas, items.get(currentIndex + 1), cx);
        }
        if (currentIndex > 0) {
            float cx = width / 2f + translationX - currentHalf - pageGap() - width / 2f;
            drawNeighbour(canvas, items.get(currentIndex - 1), cx);
        }
    }

    private void drawNeighbour(Canvas canvas, Item item, float cx) {
        int width = containerWidth();
        if (cx - width / 2f >= width || cx + width / 2f <= 0) {
            return;
        }
        float w = fitWidth(item), h = fitHeight(item);
        float cy = containerHeight() / 2f;
        tmpRect2.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
        drawMedia(canvas, item.photo, tmpRect2);
        if (item.video != null) {
            drawPlayButton(canvas, cx, cy);
        }
    }

    private void drawMedia(Canvas canvas, Drawable drawable, RectF rect) {
        if (drawable == null) {
            return;
        }
        android.graphics.Rect oldBounds = new android.graphics.Rect(drawable.getBounds());
        BitmapDrawable bitmap = drawable instanceof BitmapDrawable ? (BitmapDrawable) drawable : null;
        boolean oldFilter = bitmap != null && bitmap.getPaint().isFilterBitmap();
        try {
            drawable.setBounds((int) rect.left, (int) rect.top, (int) Math.ceil(rect.right), (int) Math.ceil(rect.bottom));
            if (bitmap != null) bitmap.setFilterBitmap(true);
            drawable.draw(canvas);
        } finally {
            drawable.setBounds(oldBounds);
            if (bitmap != null) bitmap.setFilterBitmap(oldFilter);
        }
    }

    /** openPhoto / closePhoto frame: clip and image interpolate between the source rect and the screen rect. */
    private void drawTransition(Canvas canvas) {
        float p = transitionProgress;
        int width = containerWidth(), height = containerHeight();
        float bgAlpha = closing ? closeStartBackgroundAlpha * p : p;
        backgroundPaint.setColor(Color.BLACK);
        backgroundPaint.setAlpha((int) (255 * bgAlpha));
        canvas.drawRect(0, 0, width, height, backgroundPaint);
        Drawable drawable = transitionDrawable;
        if (drawable == null) {
            return;
        }
        RectF target = transitionTargetRect;
        if (!transitionHasSource) {
            // no source on screen: fade with a slight shrink (and keep moving the way it was dragged)
            canvas.save();
            float s = 0.9f + 0.1f * p;
            canvas.scale(s, s, target.centerX(), target.centerY());
            int alpha = (int) (255 * p);
            canvas.saveLayerAlpha(target.left, target.top, target.right, target.bottom, alpha);
            drawMedia(canvas, drawable, target);
            canvas.restore();
            canvas.restore();
            return;
        }
        // the source shows the image center-cropped
        float iw = target.width(), ih = target.height();
        float cover = Math.max(sourceRect.width() / iw, sourceRect.height() / ih);
        float sw = iw * cover, sh = ih * cover;
        tmpRect2.set(sourceRect.centerX() - sw / 2f, sourceRect.centerY() - sh / 2f, sourceRect.centerX() + sw / 2f, sourceRect.centerY() + sh / 2f);
        AndroidUtilities.lerp(tmpRect2, target, p, tmpRect);
        AndroidUtilities.lerp(sourceRect, target, p, tmpRect2);
        for (int i = 0; i < 8; i++) {
            clipRadii[i] = sourceRadii[i] * (1f - p);
        }
        clipPath.rewind();
        clipPath.addRoundRect(tmpRect2, clipRadii, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);
        drawMedia(canvas, drawable, tmpRect);
        canvas.restore();
    }

    /** PhotoProgressView: 64dp 0x66000000 circle with the play triangle. */
    private void drawPlayButton(Canvas canvas, float cx, float cy) {
        playButtonPaint.setColor(0x66000000);
        canvas.drawCircle(cx, cy, dp(32), playButtonPaint);
        playButtonPaint.setColor(Color.WHITE);
        float s = dp(10);
        playPath.rewind();
        playPath.moveTo(cx - s * 0.7f, cy - s * 1.15f);
        playPath.lineTo(cx + s * 1.3f, cy);
        playPath.lineTo(cx - s * 0.7f, cy + s * 1.15f);
        playPath.close();
        canvas.drawPath(playPath, playButtonPaint);
    }

    private class ContainerView extends FrameLayout {

        ContainerView(android.content.Context context) {
            super(context);
            setWillNotDraw(false);
            setFitsSystemWindows(false);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            updateThumbnailVisibility();
            if (oldw != 0 && (w != oldw || h != oldh)) {
                cancelMoveZoomAnimation();
                scale = 1f;
                translationX = translationY = 0;
                updateMinMax(scale);
                post(MediaViewer.this::layoutTexture);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawViewer(canvas);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            // above the TextureView: the play button over a paused / finished video
            if (transitionProgress >= 1f && !closing && currentItem().video != null && isPlayButtonVisible() && scale == 1f) {
                float cx = getWidth() / 2f + translationX, cy = getHeight() / 2f + translationY;
                drawPlayButton(canvas, cx, cy);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return processTouchEvent(event);
        }
    }

    // ---------------------------------------------------------------- video

    private boolean isPlayButtonVisible() {
        return player == null || !playWhenReady;
    }

    private void preparePlayer(boolean autoplay) {
        Item item = currentItem();
        if (item.video == null || player != null) {
            return;
        }
        playWhenReady = autoplay;
        playerPrepared = false;
        final TextureView createdTexture = new TextureView(activity);
        textureView = createdTexture;
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                if (textureView != createdTexture || closing) return;
                if (player != null) {
                    surface = new Surface(texture);
                    player.setSurface(surface);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                if (textureView != createdTexture) return true;
                if (player != null) {
                    try {
                        player.setSurface(null);
                    } catch (Exception ignore) {
                    }
                }
                if (surface != null) {
                    surface.release();
                    surface = null;
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture texture) {
                if (textureView != createdTexture || closing) return;
                if (videoWidth == 0 && player != null && playerPrepared) {
                    videoWidth = Math.max(1, player.getVideoWidth());
                    videoHeight = Math.max(1, player.getVideoHeight());
                    containerView.invalidate();
                }
            }
        });
        // under the bars, above the drawn thumbnail
        containerView.addView(textureView, 0, new FrameLayout.LayoutParams(1, 1));
        player = new MediaPlayer();
        try {
            appliedPlaybackSpeed = 1f;
            player.setVolume(muted ? 0f : 1f, muted ? 0f : 1f);
            player.setDataSource(activity, item.video);
            player.setOnPreparedListener(mp -> {
                if (mp != player || closing) {
                    return;
                }
                playerPrepared = true;
                if (mp.getVideoWidth() > 0 && mp.getVideoHeight() > 0 && (item.width <= 0 || item.height <= 0)) {
                    item.width = mp.getVideoWidth();
                    item.height = mp.getVideoHeight();
                    updateMinMax(scale);
                }
                layoutTexture();
                if (playWhenReady) {
                    if (!startPlayer(mp)) return;
                    if (mp != player || closing) return;
                    playPauseView.setPlaying(true);
                    AndroidUtilities.runOnUIThread(progressUpdater);
                }
                updateVideoProgress();
                containerView.invalidate();
            });
            player.setOnCompletionListener(mp -> {
                if (mp != player || closing) {
                    return;
                }
                playWhenReady = false;
                playerFinished = true;
                playPauseView.setPlaying(false);
                updateVideoProgress();
                containerView.invalidate();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                if (mp != player || closing) return true;
                FileLog.e("MediaViewer: video error " + what + " / " + extra);
                releasePlayer();
                containerView.invalidate();
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            FileLog.e(e);
            releasePlayer();
        }
        layoutTexture();
    }

    private void layoutTexture() {
        if (textureView == null || containerWidth() == 0) {
            return;
        }
        Item item = currentItem();
        int w = (int) fitWidth(item), h = (int) fitHeight(item);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) textureView.getLayoutParams();
        lp.width = w;
        lp.height = h;
        lp.leftMargin = (containerWidth() - w) / 2;
        lp.topMargin = (containerHeight() - h) / 2;
        lp.gravity = Gravity.LEFT | Gravity.TOP;
        textureView.setLayoutParams(lp);
        updateTextureTransform();
    }

    private void updateTextureTransform() {
        if (textureView == null) {
            return;
        }
        textureView.setPivotX(textureView.getLayoutParams().width / 2f);
        textureView.setPivotY(textureView.getLayoutParams().height / 2f);
        textureView.setScaleX(scale);
        textureView.setScaleY(scale);
        textureView.setTranslationX(translationX);
        textureView.setTranslationY(translationY);
    }

    private void togglePlay() {
        if (currentItem().video == null) {
            return;
        }
        if (player == null) {
            preparePlayer(true);
            playPauseView.setPlaying(true);
            containerView.invalidate();
            return;
        }
        if (!playerPrepared) {
            playWhenReady = !playWhenReady;
            playPauseView.setPlaying(playWhenReady);
            containerView.invalidate();
            return;
        }
        if (player.isPlaying()) {
            player.pause();
            playWhenReady = false;
        } else {
            if (playerFinished) {
                player.seekTo(0);
                playerFinished = false;
            }
            MediaPlayer current = player;
            if (!startPlayer(current)) return;
            if (current != player || closing) return;
            playWhenReady = true;
            AndroidUtilities.runOnUIThread(progressUpdater);
        }
        playPauseView.setPlaying(playWhenReady);
        updateVideoProgress();
        containerView.invalidate();
    }

    private void seekTo(float progress) {
        if (player == null || !playerPrepared) {
            return;
        }
        int duration = player.getDuration();
        if (duration > 0) {
            player.seekTo((int) (progress * duration));
            playerFinished = false;
        }
        updateVideoProgress();
    }

    private void updateVideoProgress() {
        int position = 0, duration = 0;
        if (player != null && playerPrepared) {
            try {
                duration = Math.max(0, player.getDuration());
                position = playerFinished ? duration : player.getCurrentPosition();
            } catch (Exception ignore) {
            }
        }
        if (!seekDragging) {
            seekBarView.setProgress(duration > 0 ? position / (float) duration : 0);
        }
        videoTimeView.setText(AndroidUtilities.formatShortDuration(position / 1000) + " / " + AndroidUtilities.formatShortDuration(duration / 1000));
    }

    private void releasePlayer() {
        AndroidUtilities.cancelRunOnUIThread(progressUpdater);
        if (player != null) {
            MediaPlayer mp = player;
            player = null;
            try {
                mp.release();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        playerPrepared = false;
        playWhenReady = false;
        if (textureView != null) {
            containerView.removeView(textureView);
            textureView = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        videoWidth = videoHeight = 0;
    }

    /** The small play / pause glyph left of the seek bar. */
    private static class PlayPauseView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private boolean playing;

        PlayPauseView(android.content.Context context) {
            super(context);
            paint.setColor(Color.WHITE);
            setContentDescription("Play");
        }

        void setPlaying(boolean playing) {
            if (this.playing != playing) {
                this.playing = playing;
                setContentDescription(playing ? "Pause" : "Play");
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (playing) {
                float w = dp(3.5f), h = dp(14), gap = dp(4);
                canvas.drawRoundRect(cx - gap / 2f - w, cy - h / 2f, cx - gap / 2f, cy + h / 2f, dp(1), dp(1), paint);
                canvas.drawRoundRect(cx + gap / 2f, cy - h / 2f, cx + gap / 2f + w, cy + h / 2f, dp(1), dp(1), paint);
            } else {
                float s = dp(7);
                path.rewind();
                path.moveTo(cx - s * 0.7f, cy - s * 1.1f);
                path.lineTo(cx + s * 1.2f, cy);
                path.lineTo(cx - s * 0.7f, cy + s * 1.1f);
                path.close();
                canvas.drawPath(path, paint);
            }
        }
    }

    /** VideoPlayerSeekBar's look: 4dp line, 0x33ffffff behind white, a 6dp thumb that grows to 8dp while dragged. */
    private class VideoSeekBarView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float progress;
        private boolean pressed;

        VideoSeekBarView(android.content.Context context) {
            super(context);
        }

        void setProgress(float progress) {
            this.progress = Math.max(0, Math.min(1, progress));
            invalidate();
        }

        private float lineLeft() {
            return dp(8);
        }

        private float lineRight() {
            return getWidth() - dp(8);
        }

        private float progressAt(float x) {
            return Math.max(0, Math.min(1, (x - lineLeft()) / Math.max(1, lineRight() - lineLeft())));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (player == null || !playerPrepared) {
                    return false;
                }
                pressed = true;
                seekDragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                setProgress(progressAt(event.getX()));
                return true;
            } else if (action == MotionEvent.ACTION_MOVE && pressed) {
                setProgress(progressAt(event.getX()));
                return true;
            } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && pressed) {
                pressed = false;
                seekDragging = false;
                if (action == MotionEvent.ACTION_UP) {
                    seekTo(progressAt(event.getX()));
                }
                invalidate();
                return true;
            }
            return pressed;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cy = getHeight() / 2f;
            float left = lineLeft(), right = lineRight();
            float half = dp(2);
            paint.setColor(0x33ffffff);
            rect.set(left, cy - half, right, cy + half);
            canvas.drawRoundRect(rect, half, half, paint);
            float x = left + (right - left) * progress;
            paint.setColor(Color.WHITE);
            rect.set(left, cy - half, x, cy + half);
            canvas.drawRoundRect(rect, half, half, paint);
            canvas.drawCircle(x, cy, dp(pressed ? 8 : 6), paint);
        }
    }
}
