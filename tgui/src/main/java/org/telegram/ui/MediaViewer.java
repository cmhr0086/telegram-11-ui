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
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

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

    /** The host side (upstream PhotoViewerProvider). */
    public interface Provider {
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
    private TextView titleView, subtitleView;
    private LinearLayout bottomLayout;
    private TextView captionView;
    private FrameLayout videoControls;
    private PlayPauseView playPauseView;
    private VideoSeekBarView seekBarView;
    private TextView videoTimeView;

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
        bottomHasContent = captionView.getVisibility() == View.VISIBLE || item.video != null;
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
        if (item.video != null && transitionProgress >= 1f) {
            preparePlayer(true);
        }
        containerView.invalidate();
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
        drawable.setBounds((int) rect.left, (int) rect.top, (int) Math.ceil(rect.right), (int) Math.ceil(rect.bottom));
        if (drawable instanceof BitmapDrawable) {
            ((BitmapDrawable) drawable).setFilterBitmap(true);
        }
        drawable.draw(canvas);
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
        textureView = new TextureView(activity);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
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
            player.setDataSource(activity, item.video);
            player.setOnPreparedListener(mp -> {
                if (mp != player) {
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
                    mp.start();
                    playPauseView.setPlaying(true);
                    AndroidUtilities.runOnUIThread(progressUpdater);
                }
                updateVideoProgress();
                containerView.invalidate();
            });
            player.setOnCompletionListener(mp -> {
                if (mp != player) {
                    return;
                }
                playWhenReady = false;
                playerFinished = true;
                playPauseView.setPlaying(false);
                updateVideoProgress();
                containerView.invalidate();
            });
            player.setOnErrorListener((mp, what, extra) -> {
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
            player.start();
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
