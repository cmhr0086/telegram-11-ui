package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Property;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.tgui.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

/**
 * tgui: the message long-press menu of Telegram 11.4.2, as a component on plain models.
 * <p>
 * Transplanted from upstream (numbers, keys, durations and interpolators kept verbatim):
 * <ul>
 *   <li>ChatActivity.createMenu(): ActionBarPopupWindowLayout built from ActionBarMenuSubItem rows,
 *       ChatScrimPopupContainerLayout with the reactions bar above the popup, popup flags and its
 *       x / y positioning (above or below the touch point, clamped to the screen);</li>
 *   <li>ChatActivity.dimBehindView(float, boolean, boolean): scrim alpha 0.2, EASE_OUT_QUINT, 320 ms;</li>
 *   <li>ChatActivity contentView.drawChild(): the scrim and the long-pressed cell drawn above it,
 *       clipped to the list top;</li>
 *   <li>ReactionsContainerLayout.dispatchDraw() / drawBubbles() / startEnterAnimation(): the pill,
 *       its shadow and the two "thought" bubbles with the 250 ms OvershootInterpolator(0.5) enter.
 *       Reactions are static Telegram emoji drawn through {@link Emoji}; animated (lottie) reactions
 *       and the expand-to-all-reactions button are not ported.</li>
 * </ul>
 * Usage: create, {@link #addItem} rows, optionally {@link #setReactions}, then {@link #show(View, float, float)}
 * with the tapped cell and the touch point inside it.
 * <p>
 * Like ChatActivity, the host opens this menu on a <b>tap</b> (onItemClickListener → createMenu(single = true));
 * a long press starts multi-select instead (createMenu(single = false) → action mode, see the demo's
 * DemoChatFragment). Upstream also calls {@code listView.setInstantClick(true)} and
 * {@code listView.setDisableHighlightState(true)} so message rows never show the list selector ripple.
 */
public class MessageContextMenu {

    /** Upstream's default reaction set, in its order (👍 👎 ❤ 🔥 🥰 👏). */
    public static final String[] DEFAULT_REACTIONS = {"👍", "👎", "❤", "🔥", "🥰", "👏"};

    public interface Delegate {
        default void onReactionClick(String emoji) {
        }

        default void onDismiss() {
        }
    }

    public static class Item {
        public final int icon;
        public final CharSequence text;
        public final boolean destructive;
        public final Runnable onClick;

        public Item(int icon, CharSequence text, boolean destructive, Runnable onClick) {
            this.icon = icon;
            this.text = text;
            this.destructive = destructive;
            this.onClick = onClick;
        }
    }

    private final Context context;
    private final Theme.ResourcesProvider resourcesProvider;
    private final ArrayList<Item> items = new ArrayList<>();
    private String[] reactions;
    private final java.util.HashSet<String> selectedReactions = new java.util.HashSet<>();
    private Delegate delegate;

    private ActionBarPopupWindow popupWindow;
    private ScrimView scrimView;
    private boolean dismissed;

    public MessageContextMenu(Context context, Theme.ResourcesProvider resourcesProvider) {
        this.context = context;
        this.resourcesProvider = resourcesProvider;
    }

    public MessageContextMenu addItem(int icon, CharSequence text, Runnable onClick) {
        return addItem(icon, text, false, onClick);
    }

    public MessageContextMenu addItem(int icon, CharSequence text, boolean destructive, Runnable onClick) {
        items.add(new Item(icon, text, destructive, onClick));
        return this;
    }

    /** Emoji shown in the reactions bar; null or empty hides the bar. */
    public MessageContextMenu setReactions(String... reactions) {
        this.reactions = reactions;
        return this;
    }

    /**
     * Reactions the user already chose on this message (ReactionsContainerLayout.setSelectedReactions):
     * drawn smaller on a key_listSelector circle in the bar.
     */
    public MessageContextMenu setSelectedReactions(java.util.Collection<String> emoji) {
        selectedReactions.clear();
        if (emoji != null) {
            selectedReactions.addAll(emoji);
        }
        return this;
    }

    /** {@link #setSelectedReactions} with the reactions {@code message} has chosen. */
    public MessageContextMenu setSelectedReactions(org.telegram.ui.Cells.TextBubbleMessage message) {
        selectedReactions.clear();
        if (message != null) {
            for (int i = 0; i < message.reactions.size(); i++) {
                if (message.reactions.get(i).chosen) {
                    selectedReactions.add(message.reactions.get(i).emoji);
                }
            }
        }
        return this;
    }

    public MessageContextMenu setDelegate(Delegate delegate) {
        this.delegate = delegate;
        return this;
    }

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    /**
     * @param anchor the tapped cell; it is drawn above the scrim while the menu is open
     * @param x      touch x inside the anchor
     * @param y      touch y inside the anchor
     */
    @SuppressLint("ClickableViewAccessibility")
    public void show(View anchor, float x, float y) {
        Activity activity = AndroidUtilities.findActivity(context);
        if (activity == null || anchor.getWindowToken() == null || popupWindow != null) {
            return;
        }
        ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
        View list = anchor.getParent() instanceof View ? (View) anchor.getParent() : root;
        final boolean isReactionsAvailable = reactions != null && reactions.length > 0;

        // --- ChatActivity.createMenu(): popup layout and rows
        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, R.drawable.popup_fixed_alert, resourcesProvider, 0);
        popupLayout.setMinimumWidth(dp(200));
        Rect backgroundPaddings = new Rect();
        Drawable shadowDrawable = ContextCompat.getDrawable(context, R.drawable.popup_fixed_alert).mutate();
        shadowDrawable.getPadding(backgroundPaddings);
        popupLayout.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

        for (int a = 0, N = items.size(); a < N; a++) {
            Item item = items.get(a);
            ActionBarMenuSubItem cell = new ActionBarMenuSubItem(context, a == 0, a == N - 1, resourcesProvider);
            cell.setMinimumWidth(dp(200));
            cell.setTextAndIcon(item.text, item.icon);
            if (item.destructive) {
                cell.setTextColor(getThemedColor(Theme.key_text_RedBold));
                cell.setIconColor(getThemedColor(Theme.key_text_RedRegular));
                cell.setSelectorColor(Theme.multAlpha(getThemedColor(Theme.key_text_RedRegular), .12f));
            }
            popupLayout.addView(cell);
            cell.setOnClickListener(v -> {
                dismiss();
                if (item.onClick != null) {
                    item.onClick.run();
                }
            });
        }

        ContainerLayout container = new ContainerLayout(context);
        Rect rect = new Rect();
        container.setOnTouchListener(new View.OnTouchListener() {
            private final int[] pos = new int[2];

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (popupWindow != null && popupWindow.isShowing()) {
                        View contentView = popupWindow.getContentView();
                        contentView.getLocationInWindow(pos);
                        rect.set(pos[0], pos[1], pos[0] + contentView.getMeasuredWidth(), pos[1] + contentView.getMeasuredHeight());
                        if (!rect.contains((int) event.getX(), (int) event.getY())) {
                            dismiss();
                        }
                    }
                } else if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                    dismiss();
                }
                return false;
            }
        });

        ReactionsBar reactionsLayout = null;
        if (isReactionsAvailable) {
            int pad = 22;
            int sPad = 24;
            reactionsLayout = new ReactionsBar(context, reactions);
            reactionsLayout.setPadding(dp(4) + (LocaleController.isRTL ? 0 : dp(sPad)), dp(4), dp(4) + (LocaleController.isRTL ? dp(sPad) : 0), dp(pad));
            LinearLayout.LayoutParams params = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 52 + pad, Gravity.RIGHT, 0, 50, 0, -20);
            container.addView(reactionsLayout, params);
            container.reactionsLayout = reactionsLayout;
            container.setClipChildren(false);
            reactionsLayout.setTransitionProgress(0);
        }
        container.addView(popupLayout, LayoutHelper.createLinearRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, isReactionsAvailable ? 16 : 0, 0, isReactionsAvailable ? 36 : 0, 0));
        container.popupWindowLayout = popupLayout;

        popupWindow = new ActionBarPopupWindow(container, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popupWindow.setPauseNotifications(true);
        popupWindow.setDismissAnimationDuration(220);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(true);
        popupWindow.setAnimationStyle(isReactionsAvailable ? 0 : R.style.PopupContextAnimation);
        popupWindow.setFocusable(true);
        container.measure(View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST));
        popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        popupWindow.getContentView().setFocusableInTouchMode(true);
        popupLayout.setFitItems(true);
        popupWindow.setOnDismissListener(this::onPopupDismissed);

        // --- positioning (ChatActivity.createMenu), in window coordinates
        int[] listLocation = new int[2];
        int[] anchorLocation = new int[2];
        list.getLocationInWindow(listLocation);
        anchor.getLocationInWindow(anchorLocation);
        int popupX = anchorLocation[0] - listLocation[0] + (int) x - container.getMeasuredWidth() + backgroundPaddings.left - dp(28);
        if (popupX < dp(6)) {
            popupX = dp(6);
        } else if (popupX > list.getMeasuredWidth() - dp(6) - container.getMeasuredWidth()) {
            popupX = list.getMeasuredWidth() - dp(6) - container.getMeasuredWidth();
        }
        popupX += listLocation[0];
        int totalHeight = root.getHeight();
        if (Build.VERSION.SDK_INT >= 23 && root.getRootWindowInsets() != null) {
            totalHeight -= root.getRootWindowInsets().getStableInsetBottom();
        }
        int height = container.getMeasuredHeight() + dp(48);
        int popupY;
        int minY = listLocation[1] + dp(24);
        int maxY = totalHeight - height - dp(8);
        if (height < totalHeight) {
            popupY = (int) (anchorLocation[1] + y);
            if (height - backgroundPaddings.top - backgroundPaddings.bottom > dp(240)) {
                popupY += dp(240) - height;
            }
            popupY = Utilities.clamp(popupY, maxY, minY);
        } else {
            popupY = AndroidUtilities.statusBarHeight;
        }
        container.maxHeight = maxY + height - popupY;

        // --- scrim + the long-pressed cell above it (ChatActivity.dimBehindView / contentView.drawChild)
        scrimView = new ScrimView(context, anchor, list);
        root.addView(scrimView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrimView.animateTo(0.2f);

        popupWindow.showAtLocation(list, Gravity.LEFT | Gravity.TOP, popupX, popupY);
        if (reactionsLayout != null) {
            reactionsLayout.startEnterAnimation();
        }
    }

    /** Animated close, like ChatActivity.closeMenu(). */
    public void dismiss() {
        if (popupWindow != null) {
            popupWindow.dismiss();
        }
    }

    private void onPopupDismissed() {
        if (dismissed) {
            return;
        }
        dismissed = true;
        if (scrimView != null) {
            scrimView.animateTo(0f);
        }
        if (delegate != null) {
            delegate.onDismiss();
        }
    }

    private void onReactionClick(String emoji) {
        dismiss();
        if (delegate != null) {
            delegate.onReactionClick(emoji);
        }
    }

    /** ChatScrimPopupContainerLayout: reactions bar above the popup, max height, back key and outside taps. */
    private class ContainerLayout extends LinearLayout {

        ReactionsBar reactionsLayout;
        ActionBarPopupWindow.ActionBarPopupWindowLayout popupWindowLayout;
        int maxHeight;

        ContainerLayout(Context context) {
            super(context);
            setOrientation(LinearLayout.VERTICAL);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (maxHeight != 0) {
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            maxHeight = getMeasuredHeight();
        }

        void setReactionsTransitionProgress(float v) {
            if (popupWindowLayout != null) {
                popupWindowLayout.setReactionsTransitionProgress(v);
            }
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
                dismiss();
            }
            return super.dispatchKeyEvent(event);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            boolean b = super.dispatchTouchEvent(ev);
            if (ev.getAction() == MotionEvent.ACTION_DOWN && !b) {
                dismiss();
            }
            return b;
        }
    }

    /** Full-window overlay: the scrim, then the anchor cell drawn on top of it (clipped to the list top). */
    private class ScrimView extends View {

        private final View anchor;
        private final View list;
        private final Paint scrimPaint = new Paint();
        private final int[] location = new int[2];
        private final int[] anchorLocation = new int[2];
        private float scrimPaintAlpha;
        private ValueAnimator animator;

        ScrimView(Context context, View anchor, View list) {
            super(context);
            this.anchor = anchor;
            this.list = list;
        }

        void animateTo(float value) {
            if (animator != null) {
                animator.removeAllListeners();
                animator.cancel();
            }
            final boolean enable = value > 0;
            animator = ValueAnimator.ofFloat(scrimPaintAlpha, value);
            animator.addUpdateListener(a -> {
                scrimPaintAlpha = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            animator.setDuration(320);
            if (!enable) {
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        AndroidUtilities.removeFromParent(ScrimView.this);
                        if (scrimView == ScrimView.this) {
                            scrimView = null;
                        }
                        anchor.invalidate();
                    }
                });
            }
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            scrimPaint.setAlpha((int) (0xFF * scrimPaintAlpha));
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), scrimPaint);
            if (anchor.isAttachedToWindow() && anchor.getVisibility() == VISIBLE) {
                getLocationInWindow(location);
                int x = -location[0], y = -location[1];
                list.getLocationInWindow(anchorLocation);
                float listTop = y + anchorLocation[1] + list.getPaddingTop() - dp(4);
                float listBottom = y + anchorLocation[1] + list.getMeasuredHeight();
                anchor.getLocationInWindow(anchorLocation);
                canvas.save();
                canvas.clipRect(0, listTop, getMeasuredWidth(), listBottom);
                canvas.translate(x + anchorLocation[0], y + anchorLocation[1]);
                anchor.draw(canvas);
                canvas.restore();
            }
            // the cell may animate (send state, gradient) while the menu is open
            postInvalidateOnAnimation();
        }
    }

    private static final Property<ReactionsBar, Float> TRANSITION_PROGRESS_VALUE = new Property<ReactionsBar, Float>(Float.class, "transitionProgress") {
        @Override
        public Float get(ReactionsBar view) {
            return view.transitionProgress;
        }

        @Override
        public void set(ReactionsBar view, Float value) {
            view.setTransitionProgress(value);
        }
    };

    /**
     * The look of ReactionsContainerLayout (TYPE_DEFAULT) with static emoji: 36dp-wide items, 8dp
     * edge insets, a 48dp pill with radius height / 2, reactions_bubble_shadow, two bubbles at
     * bigCircleOffset = 36dp and the enter transition (scale around 87.5% x, pill grows leftwards,
     * items pop in with upstream's per-item delay).
     */
    private class ReactionsBar extends View {

        private final static float CLIP_PROGRESS = 0.25f;
        private final static int ITEM_WIDTH_DP = 36;
        private final static int EDGE_DP = 8;
        private final static int EMOJI_SIZE_DP = 28;

        private final String[] emoji;
        private final Drawable[] drawables;
        private final float[] itemProgress;
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect shadowPad = new Rect();
        private final Drawable shadow;
        private final RectF rect = new RectF();
        private final Path mPath = new Path();
        private final float bigCircleRadius = dp(8);
        private final float smallCircleRadius = bigCircleRadius / 2;
        private final int bigCircleOffset = dp(36);
        private final OvershootInterpolator itemInterpolator = new OvershootInterpolator(1.6f);
        float transitionProgress = 1f;
        private long enterStartTime;
        private int pressedIndex = -1;

        ReactionsBar(Context context, String[] emoji) {
            super(context);
            this.emoji = emoji;
            drawables = new Drawable[emoji.length];
            itemProgress = new float[emoji.length];
            for (int i = 0; i < emoji.length; i++) {
                Drawable d = Emoji.getEmojiDrawable(emoji[i]);
                if (d == null) {
                    d = Emoji.getEmojiDrawable(Emoji.fixEmoji(emoji[i]));
                }
                drawables[i] = d;
                itemProgress[i] = 1f;
            }
            shadow = ContextCompat.getDrawable(context, R.drawable.reactions_bubble_shadow).mutate();
            shadowPad.left = shadowPad.top = shadowPad.right = shadowPad.bottom = dp(7);
            shadow.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelShadow), PorterDuff.Mode.MULTIPLY));
            bgPaint.setColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));
            selectedPaint.setColor(getThemedColor(Theme.key_listSelector));
            setContentDescription("Reactions");
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = dp(ITEM_WIDTH_DP) * emoji.length + dp(EDGE_DP) * 2 + getPaddingLeft() + getPaddingRight();
            setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec));
        }

        void setTransitionProgress(float transitionProgress) {
            this.transitionProgress = transitionProgress;
            if (getParent() instanceof ContainerLayout) {
                ((ContainerLayout) getParent()).setReactionsTransitionProgress(transitionProgress);
            }
            invalidate();
        }

        void startEnterAnimation() {
            setTransitionProgress(0);
            setAlpha(1f);
            enterStartTime = System.currentTimeMillis();
            ObjectAnimator animator = ObjectAnimator.ofFloat(this, TRANSITION_PROGRESS_VALUE, 0f, 1f).setDuration(250);
            animator.setInterpolator(new OvershootInterpolator(0.5f));
            animator.start();
        }

        private float itemLeft(int i) {
            return getPaddingLeft() + dp(EDGE_DP) + i * dp(ITEM_WIDTH_DP);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int w = getWidth(), h = getHeight();
            float cPr = (Math.max(CLIP_PROGRESS, Math.min(transitionProgress, 1f)) - CLIP_PROGRESS) / (1f - CLIP_PROGRESS);
            float br = bigCircleRadius * cPr, sr = smallCircleRadius * cPr;
            float pivotX = LocaleController.isRTL ? w * 0.125f : w * 0.875f;

            float lt = 0, rt = 1;
            if (LocaleController.isRTL) {
                rt = Math.max(CLIP_PROGRESS, transitionProgress);
            } else {
                lt = (1f - Math.max(CLIP_PROGRESS, transitionProgress));
            }
            float transitionLeftOffset = (w - getPaddingRight()) * Math.min(1f, lt);
            rect.set(getPaddingLeft() + transitionLeftOffset, getPaddingTop(), (w - getPaddingRight()) * rt, h - getPaddingBottom());
            float radius = rect.height() / 2f;

            // pill shadow and background
            int s = canvas.save();
            if (transitionProgress != 1f) {
                canvas.scale(transitionProgress, transitionProgress, pivotX, h / 2f);
            }
            shadow.setAlpha(255);
            shadow.setBounds((int) (getPaddingLeft() + (w - getPaddingRight() + shadowPad.right) * lt - shadowPad.left), getPaddingTop() - shadowPad.top, (int) ((w - getPaddingRight() + shadowPad.right) * rt), h - getPaddingBottom() + shadowPad.bottom);
            shadow.draw(canvas);
            canvas.drawRoundRect(rect, radius, radius, bgPaint);

            // items, clipped to the pill
            mPath.rewind();
            mPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
            int s2 = canvas.save();
            canvas.clipPath(mPath);
            canvas.translate((LocaleController.isRTL ? -1 : 1) * w * (1f - transitionProgress) - transitionLeftOffset, 0);
            long elapsed = System.currentTimeMillis() - enterStartTime;
            boolean animating = false;
            int delay = 0;
            float contentWidth = dp(ITEM_WIDTH_DP) * emoji.length + dp(EDGE_DP) * 2;
            int size = dp(EMOJI_SIZE_DP);
            for (int i = 0; i < emoji.length; i++) {
                float left = itemLeft(i);
                float cx = left + dp(ITEM_WIDTH_DP) / 2f;
                float cy = getPaddingTop() + (h - getPaddingTop() - getPaddingBottom()) / 2f;
                // ReactionsContainerLayout: delay = 200 * |childCenterX / width - 0.8|, +30 ms per started item
                int itemDelay = (int) (200 * Math.abs((cx - getPaddingLeft()) / contentWidth - 0.8f)) + delay;
                delay += 30;
                float p = enterStartTime == 0 ? 1f : Utilities.clamp((elapsed - itemDelay) / 200f, 1f, 0f);
                if (p < 1f) {
                    animating = true;
                }
                float scale = itemInterpolator.getInterpolation(p) * (i == pressedIndex ? 0.85f : 1f);
                Drawable d = drawables[i];
                if (d == null || scale <= 0) {
                    continue;
                }
                // ReactionHolderView: a chosen reaction sits on a selector circle at 0.76 scale
                boolean selected = selectedReactions.contains(emoji[i]);
                canvas.save();
                canvas.scale(scale, scale, cx, cy);
                if (selected) {
                    canvas.drawCircle(cx, cy, dp(ITEM_WIDTH_DP) / 2f - dp(1), selectedPaint);
                    canvas.scale(0.76f, 0.76f, cx, cy);
                }
                d.setBounds((int) (cx - size / 2f), (int) (cy - size / 2f), (int) (cx + size / 2f), (int) (cy + size / 2f));
                d.draw(canvas);
                canvas.restore();
            }
            canvas.restoreToCount(s2);

            // drawBubbles(): the two small circles under the pill's right end
            canvas.save();
            canvas.clipRect(0, rect.bottom - (int) Math.ceil(rect.height() / 2f * (1f - transitionProgress)) - 1, w, h + dp(8));
            float bx = LocaleController.isRTL ? bigCircleOffset : w - bigCircleOffset;
            float by = h - getPaddingBottom();
            int sPad = dp(3);
            shadow.setBounds((int) (bx - br - sPad * cPr), (int) (by - br - sPad * cPr), (int) (bx + br + sPad * cPr), (int) (by + br + sPad * cPr));
            shadow.draw(canvas);
            canvas.drawCircle(bx, by, br, bgPaint);

            bx = LocaleController.isRTL ? bigCircleOffset - bigCircleRadius : w - bigCircleOffset + bigCircleRadius;
            by = h - smallCircleRadius - sPad;
            sPad = -dp(1);
            shadow.setBounds((int) (bx - br - sPad * cPr), (int) (by - br - sPad * cPr), (int) (bx + br + sPad * cPr), (int) (by + br + sPad * cPr));
            shadow.draw(canvas);
            canvas.drawCircle(bx, by, sr, bgPaint);
            canvas.restore();

            canvas.restoreToCount(s);
            if (animating) {
                invalidate();
            }
        }

        private int findItem(float x, float y) {
            if (y < getPaddingTop() || y > getHeight() - getPaddingBottom()) {
                return -1;
            }
            for (int i = 0; i < emoji.length; i++) {
                float left = itemLeft(i);
                if (x >= left && x < left + dp(ITEM_WIDTH_DP)) {
                    return i;
                }
            }
            return -1;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int index = findItem(event.getX(), event.getY());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (index < 0) {
                        return false;
                    }
                    pressedIndex = index;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (index != pressedIndex && pressedIndex >= 0) {
                        pressedIndex = -1;
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (pressedIndex >= 0 && index == pressedIndex) {
                        playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        onReactionClick(emoji[index]);
                    }
                    pressedIndex = -1;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    pressedIndex = -1;
                    invalidate();
                    return true;
            }
            return false;
        }
    }
}
