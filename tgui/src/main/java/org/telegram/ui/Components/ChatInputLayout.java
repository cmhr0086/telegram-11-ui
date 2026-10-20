package org.telegram.ui.Components;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.INavigationLayout;

/**
 * tgui: root layout for a screen with a {@link ChatActivityEnterView}, carrying over the parts of
 * upstream ChatActivity.ChatActivityFragmentView that the input needs:
 * <ul>
 *     <li>while the emoji panel is open (keyboard hidden) every bottom-aligned child — the input,
 *     the record circle and its controls — is lifted by the panel height, and the panel itself is
 *     placed right below the input;</li>
 *     <li>content children ({@link #setContentView}) end above the input;</li>
 *     <li>the chat background (wallpaper) is drawn behind everything, as SizeNotifierFrameLayout does.</li>
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
    }

    public void setEnterView(ChatActivityEnterView enterView) {
        this.enterView = enterView;
    }

    /** The chat list (or any content) that should end where the input begins. */
    public void setContentView(View contentView) {
        this.contentView = contentView;
    }

    public void setActionBar(ActionBar actionBar) {
        this.actionBar = actionBar;
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
                // upstream keeps the list under the input (it draws over it with the compose shadow)
                int h = Math.max(AndroidUtilities.dp(10), heightSize - inputFieldHeight - emojiViewPadding + AndroidUtilities.dp(2));
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
        setBottomClip(paddingBottom);

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
                childTop = lp.topMargin + getPaddingTop();
            }
            child.layout(childLeft, childTop, childLeft + width, childTop + height);
        }
        notifyHeightChanged();
    }
}
