package org.telegram.ui.Components;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.INavigationLayout;

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
 *     <li>when the keyboard opens or closes, the window resize is animated with the keyboard
 *     ({@link AdjustPanLayoutHelper}, as upstream ChatActivityFragmentView): the layout slides while
 *     the action bar and the wallpaper stay in place.</li>
 * </ul>
 * Add the action bar last so it stays on top.
 */
public class ChatInputLayout extends SizeNotifierFrameLayout {

    private ChatActivityEnterView enterView;
    private View contentView;
    private ActionBar actionBar;
    private final INavigationLayout navigationLayout;
    private int emojiViewPadding;

    public ChatInputLayout(Context context, INavigationLayout parentLayout) {
        super(context, parentLayout);
        navigationLayout = parentLayout;
    }

    public void setEnterView(ChatActivityEnterView enterView) {
        this.enterView = enterView;
        if (enterView != null && adjustPanLayoutHelper != null) {
            enterView.setAdjustPanLayoutHelper(adjustPanLayoutHelper);
        }
    }

    /** upstream ChatActivityFragmentView's keyboard animation, minus the chat-only views. */
    @Override
    protected AdjustPanLayoutHelper createAdjustPanLayoutHelper() {
        return new AdjustPanLayoutHelper(this) {
            @Override
            protected void onTransitionStart(boolean keyboardVisible, int contentHeight) {
                if (enterView != null) {
                    enterView.onAdjustPanTransitionStart(keyboardVisible, contentHeight);
                }
            }

            @Override
            protected void onTransitionEnd() {
                if (enterView != null) {
                    enterView.onAdjustPanTransitionEnd();
                }
            }

            @Override
            protected void onPanTranslationUpdate(float y, float progress, boolean keyboardVisible) {
                if (navigationLayout != null && navigationLayout.isPreviewOpenAnimationInProgress()) {
                    return;
                }
                if (actionBar != null) {
                    actionBar.setTranslationY(y);
                }
                setBackgroundTranslation((int) y);
                if (contentView != null) {
                    contentView.invalidate();
                }
                if (enterView != null) {
                    enterView.onAdjustPanTransitionUpdate(y, progress, keyboardVisible);
                }
            }

            @Override
            protected boolean heightAnimationEnabled() {
                if (AndroidUtilities.isInMultiwindow || navigationLayout == null || enterView == null) {
                    return false;
                }
                return !navigationLayout.isTransitionAnimationInProgress() && !navigationLayout.isPreviewOpenAnimationInProgress();
            }
        };
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (adjustPanLayoutHelper != null) {
            adjustPanLayoutHelper.onAttach();
            if (enterView != null) {
                enterView.setAdjustPanLayoutHelper(adjustPanLayoutHelper);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (adjustPanLayoutHelper != null) {
            adjustPanLayoutHelper.onDetach();
        }
    }

    /** The chat list (or any content) that should end where the input begins. */
    public void setContentView(View contentView) {
        this.contentView = contentView;
    }

    public void setActionBar(ActionBar actionBar) {
        this.actionBar = actionBar;
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
        notifyHeightChanged();
    }
}
