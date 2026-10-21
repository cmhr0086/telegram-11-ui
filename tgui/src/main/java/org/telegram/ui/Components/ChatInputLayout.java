package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;

/**
 * tgui: root layout for a screen with a {@link ChatActivityEnterView}, carrying over the parts of
 * upstream ChatActivity.ChatActivityFragmentView that the input needs:
 * <ul>
 *     <li>while the emoji panel is open (keyboard hidden) every bottom-aligned child — the input,
 *     the record circle and its controls — is lifted by the panel height, and the panel itself is
 *     placed right below the input;</li>
 *     <li>content children ({@link #setContentView}) end above the input;</li>
 *     <li>while the emoji panel slides in or out, the input and the content follow it (upstream
 *     ChatActivity's {@code bottomPanelTranslationYChanged}): the content keeps its height for the
 *     larger panel padding, extends upwards by the panel height and is translated with it;</li>
 *     <li>the chat background (wallpaper) is drawn behind everything, as SizeNotifierFrameLayout does;</li>
 *     <li>tgui's own keyboard motion (upstream animates the whole layout with
 *     {@link AdjustPanLayoutHelper}, which re-sizes the view chain and redraws the wallpaper every
 *     frame and is too heavy on older phones): when the field is tapped the input and the content
 *     are lifted right away to the remembered keyboard height, and whenever the window resizes for
 *     the keyboard they keep their on-screen position and ease into the new layout. Only
 *     {@code translationY} of those two views changes, so there is no relayout per frame. On
 *     Android 11+ the input instead follows the IME frame by frame through
 *     {@link WindowInsetsAnimationCompat}, staying on the keyboard's top edge (or on the emoji
 *     panel while the two swap).</li>
 * </ul>
 * Add the action bar last so it stays on top.
 */
public class ChatInputLayout extends SizeNotifierFrameLayout {

    private ChatActivityEnterView enterView;
    private View contentView;
    private ActionBar actionBar;
    private int emojiViewPadding;

    public ChatInputLayout(Context context, INavigationLayout parentLayout) {
        super(context, parentLayout);
        if (Build.VERSION.SDK_INT >= 30) {
            ViewCompat.setWindowInsetsAnimationCallback(this, new WindowInsetsAnimationCompat.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                @Override
                public void onPrepare(@NonNull WindowInsetsAnimationCompat animation) {
                    if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0) {
                        imeAnimating = true;
                        // the emoji panel and the keyboard swap: the input keeps the panel height
                        panelAtImeStart = enterView != null && enterView.isPopupShowing() ? enterView.getEmojiPadding() : 0;
                        AndroidUtilities.cancelRunOnUIThread(keyboardLiftTimeout);
                        if (keyboardMotionAnimator != null) {
                            keyboardMotionAnimator.cancel();
                            keyboardMotionAnimator = null;
                        }
                        keyboardMotionActive = false;
                    }
                }

                @NonNull
                @Override
                public WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets, @NonNull java.util.List<WindowInsetsAnimationCompat> runningAnimations) {
                    if (imeAnimating) {
                        Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                        Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                        imeBottom = ime.bottom;
                        navBottom = nav.bottom;
                        followIme();
                    }
                    return insets;
                }

                @Override
                public void onEnd(@NonNull WindowInsetsAnimationCompat animation) {
                    if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0) {
                        imeAnimating = false;
                        panelAtImeStart = 0;
                        // the layout may still have the old size (DrawerLayoutContainer applies the
                        // grown IME inset only now): keep the input where it is on screen, the next
                        // layout brings the translation to 0 without any visible movement
                        settleOnIme();
                    }
                }
            });
        }
    }

    // Android 11+: an IME show / hide animation is running, the input follows its current inset
    private boolean imeAnimating;
    private int imeBottom, navBottom;
    private int panelAtImeStart;
    private final int[] tmpLocation = new int[2];
    /**
     * Puts the input on the IME's current top edge: the translation is the difference between
     * where the last layout put it and where the keyboard (or, while the emoji panel and the
     * keyboard swap, the panel) is right now. The layout itself catches up whenever the window
     * resizes; {@link #onLayout} re-applies this so there is no jump.
     */
    private void followIme() {
        if (enterView == null || enterView.panelAnimationInProgress() || enterView.getHeight() == 0) {
            return;
        }
        enterView.getLocationInWindow(tmpLocation);
        float laidBottom = tmpLocation[1] - enterView.getTranslationY() + enterView.getHeight();
        int windowHeight = getRootView().getHeight();
        int panel = Math.max(enterView.isPopupShowing() ? enterView.getEmojiPadding() : 0, panelAtImeStart);
        float desiredBottom = windowHeight - Math.max(imeBottom, navBottom + panel);
        setKeyboardMotion(desiredBottom - laidBottom);
    }

    /**
     * Android 11+, no IME animation running: the input belongs where the current insets put it.
     * The translation covers only the gap until the layout catches up, so it is 0 at rest.
     */
    private void settleOnIme() {
        if (enterView == null || enterView.panelAnimationInProgress()) {
            return;
        }
        if (enterView.isPopupShowing()) {
            // the emoji panel owns the space under the input; following a zero IME inset here
            // pushed the panel (and the input) off screen, leaving an empty area
            setKeyboardMotion(0);
            return;
        }
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
        if (insets == null) {
            setKeyboardMotion(0);
            return;
        }
        imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
        followIme();
        if (Math.abs(enterView.getTranslationY()) < 1) {
            setKeyboardMotion(0);
        }
    }

    private Theme.ResourcesProvider resourcesProvider;

    /** Colours for the keyboard-area fill; the same provider as the input. */
    public void setResourcesProvider(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
    }

    public void setEnterView(ChatActivityEnterView enterView) {
        this.enterView = enterView;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        AndroidUtilities.cancelRunOnUIThread(keyboardLiftTimeout);
        if (keyboardMotionAnimator != null) {
            keyboardMotionAnimator.cancel();
            keyboardMotionAnimator = null;
        }
    }

    /** The chat list (or any content) that should end where the input begins. */
    public void setContentView(View contentView) {
        this.contentView = contentView;
    }

    public void setActionBar(ActionBar actionBar) {
        this.actionBar = actionBar;
    }

    private ValueAnimator keyboardMotionAnimator;
    // the keyboard motion is running: the space under the raised input is filled like a keyboard area
    private boolean keyboardMotionActive;
    private final Paint keyboardFillPaint = new Paint();
    private final Runnable keyboardLiftTimeout = () -> animateKeyboardMotion(currentKeyboardMotion(), 0);

    private float currentKeyboardMotion() {
        return enterView == null ? 0 : enterView.getTranslationY();
    }

    private void setKeyboardMotion(float translation) {
        enterView.setTranslationY(translation);
        if (contentView != null) {
            contentView.setTranslationY(translation);
        }
        invalidate();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == enterView && (keyboardMotionActive || imeAnimating && panelAtImeStart > 0) && enterView.getTranslationY() < 0) {
            // tgui: instead of the input hovering over the wallpaper while the IME is still fading
            // in (or already gone), show the keyboard area in the emoji panel colour, as upstream
            // does when it keeps the emoji panel as the keyboard placeholder
            keyboardFillPaint.setColor(Theme.getColor(Theme.key_chat_emojiPanelBackground, resourcesProvider));
            float top = enterView.getBottom() + enterView.getTranslationY();
            canvas.drawRect(0, top, getMeasuredWidth(), getMeasuredHeight(), keyboardFillPaint);
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    // uptime when the current lift (tap → keyboard expected) started, 0 when none runs
    private long keyboardLiftStart;

    private void animateKeyboardMotion(float from, float to) {
        animateKeyboardMotion(from, to, AdjustPanLayoutHelper.keyboardDuration);
    }

    private void animateKeyboardMotion(float from, float to, long duration) {
        if (keyboardMotionAnimator != null) {
            keyboardMotionAnimator.cancel();
            keyboardMotionAnimator = null;
        }
        if (enterView == null) {
            return;
        }
        if (Math.abs(from - to) < 1) {
            keyboardMotionActive = to != 0;
            setKeyboardMotion(to);
            return;
        }
        keyboardMotionActive = true;
        setKeyboardMotion(from);
        keyboardMotionAnimator = ValueAnimator.ofFloat(from, to);
        keyboardMotionAnimator.addUpdateListener(a -> setKeyboardMotion((float) a.getAnimatedValue()));
        keyboardMotionAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (keyboardMotionAnimator == animation) {
                    keyboardMotionAnimator = null;
                    if (enterView != null && enterView.getTranslationY() == 0) {
                        keyboardMotionActive = false;
                        invalidate();
                    }
                }
            }
        });
        keyboardMotionAnimator.setDuration(duration);
        keyboardMotionAnimator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
        keyboardMotionAnimator.start();
    }

    /**
     * Called by {@link ChatActivityEnterView} when the field is touched and the keyboard is about
     * to open: lift the input and the content to where the keyboard will put them. If the window
     * has not resized within a second (hardware keyboard, IME refused), they go back down.
     */
    public void onKeyboardRequested(int expectedKeyboardHeight) {
        if (Build.VERSION.SDK_INT >= 30) {
            return; // the IME animation callback moves the input
        }
        if (enterView == null || expectedKeyboardHeight <= 0 || measureKeyboardHeight() > AndroidUtilities.dp(20)
                || enterView.isPopupShowing() || enterView.panelAnimationInProgress() || AndroidUtilities.isInMultiwindow) {
            return;
        }
        animateKeyboardMotion(currentKeyboardMotion(), -expectedKeyboardHeight);
        keyboardLiftStart = android.os.SystemClock.uptimeMillis();
        AndroidUtilities.cancelRunOnUIThread(keyboardLiftTimeout);
        AndroidUtilities.runOnUIThread(keyboardLiftTimeout, 1000);
    }

    /**
     * Called by {@link ChatActivityEnterView} for every frame of the emoji panel show / hide
     * animation, with the panel's translation (0 when it ends).
     */
    public void onBottomPanelTranslationYChanged(float translation) {
        if (enterView == null) {
            return;
        }
        boolean inProgress = enterView.panelAnimationInProgress();
        enterView.setTranslationY(translation);
        if (contentView != null) {
            contentView.setTranslationY(translation + enterView.getTopViewTranslation());
        }
        setEmojiOffset(inProgress, inProgress ? enterView.getEmojiPadding() - translation : 0);
        invalidate();
    }

    /** Extra height above the content while the panel animates, see {@link #onBottomPanelTranslationYChanged}. */
    private int contentExtraTop() {
        if (enterView == null || !enterView.panelAnimationInProgress() || measureKeyboardHeight() > AndroidUtilities.dp(20)) {
            return 0;
        }
        return enterView.getEmojiPadding();
    }

    /** Height taken by the emoji panel below the input while the keyboard is hidden. */
    public int getEmojiViewPadding() {
        return emojiViewPadding;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(widthSize, heightSize);
        heightSize -= getPaddingTop();

        if (enterView == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int keyboardSize = measureKeyboardHeight();
        if (keyboardSize <= AndroidUtilities.dp(20)) {
            emojiViewPadding = enterView.isPopupShowing() ? enterView.getEmojiPadding() : 0;
        } else {
            emojiViewPadding = 0;
        }
        setEmojiKeyboardHeight(emojiViewPadding);
        enterView.runEmojiPanelAnimation();

        measureChildWithMargins(enterView, widthMeasureSpec, 0, heightMeasureSpec, 0);
        int inputFieldHeight = enterView.getMeasuredHeight();

        for (int i = 0, n = getChildCount(); i < n; i++) {
            View child = getChildAt(i);
            if (child == null || child.getVisibility() == GONE || child == enterView) {
                continue;
            }
            if (child == contentView) {
                // upstream keeps the list under the input (it draws over it with the compose shadow);
                // during the panel animation it is as tall as without the panel and starts above the top
                int extraTop = contentExtraTop();
                int padding = extraTop > 0 ? extraTop : emojiViewPadding;
                int h = Math.max(AndroidUtilities.dp(10), heightSize - inputFieldHeight - padding + extraTop + AndroidUtilities.dp(2));
                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            } else if (enterView.isPopupView(child)) {
                int height = child.getLayoutParams().height;
                if (height < 0) {
                    height = enterView.getEmojiPadding();
                }
                if (enterView.isStickersExpanded()) {
                    int maxHeight = heightSize - inputFieldHeight + (actionBar != null ? actionBar.getMeasuredHeight() : 0) - AndroidUtilities.statusBarHeight + getPaddingTop();
                    height = Math.min(Math.max(height, 0), Math.max(0, maxHeight));
                }
                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.max(0, height), MeasureSpec.EXACTLY));
            } else {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (enterView == null) {
            super.onLayout(changed, l, t, r, b);
            notifyHeightChanged();
            return;
        }
        int oldInputBottom = enterView.getBottom();
        int keyboardSize = measureKeyboardHeight();
        int paddingBottom = keyboardSize <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow ? enterView.getEmojiPadding() : 0;
        // no setBottomClip(): upstream ChatActivity lets the wallpaper run under the (opaque) panel,
        // so the area the panel uncovers while sliding away is never blank

        for (int i = 0, n = getChildCount(); i < n; i++) {
            View child = getChildAt(i);
            if (child == null || child.getVisibility() == GONE) {
                continue;
            }
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
            int width = child.getMeasuredWidth();
            int height = child.getMeasuredHeight();
            int gravity = lp.gravity;
            if (gravity == -1) {
                gravity = Gravity.TOP | Gravity.LEFT;
            }
            int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
            int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

            int childLeft;
            switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                case Gravity.CENTER_HORIZONTAL:
                    childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                    break;
                case Gravity.RIGHT:
                    childLeft = r - width - lp.rightMargin;
                    break;
                case Gravity.LEFT:
                default:
                    childLeft = lp.leftMargin;
            }

            int childTop;
            switch (verticalGravity) {
                case Gravity.TOP:
                    childTop = lp.topMargin + getPaddingTop();
                    break;
                case Gravity.CENTER_VERTICAL:
                    childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                    break;
                case Gravity.BOTTOM:
                    childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                    break;
                default:
                    childTop = lp.topMargin;
            }

            if (enterView.isPopupView(child)) {
                childTop = enterView.isStickersExpanded() ? enterView.getTop() - height + AndroidUtilities.dp(1) : enterView.getBottom();
            } else if (child == contentView) {
                childTop = lp.topMargin + getPaddingTop() - contentExtraTop();
            }
            child.layout(childLeft, childTop, childLeft + width, childTop + height);
        }
        // keyboard opened or closed: keep the input where it is on screen and ease it into place
        int newInputBottom = enterView.getBottom();
        if (imeAnimating) {
            followIme();
        } else if (Build.VERSION.SDK_INT >= 30) {
            // the IME animation callback alone moves the input; a late resize only removes the gap
            if (enterView.getTranslationY() != 0 && !enterView.panelAnimationInProgress()) {
                settleOnIme();
            }
        } else if (oldInputBottom > 0 && newInputBottom != oldInputBottom && !enterView.panelAnimationInProgress()) {
            AndroidUtilities.cancelRunOnUIThread(keyboardLiftTimeout);
            // the keyboard usually arrives while the lift is still running (~250 ms after the tap on
            // a Mate 9): finish in the lift's remaining time instead of starting a new ease, so the
            // input is not left behind the keyboard
            long duration = AdjustPanLayoutHelper.keyboardDuration;
            if (keyboardLiftStart != 0 && keyboardMotionAnimator != null) {
                long elapsed = android.os.SystemClock.uptimeMillis() - keyboardLiftStart;
                duration = Math.max(80, AdjustPanLayoutHelper.keyboardDuration - elapsed);
            }
            keyboardLiftStart = 0;
            animateKeyboardMotion(currentKeyboardMotion() + oldInputBottom - newInputBottom, 0, duration);
        }
        notifyHeightChanged();
    }
}
