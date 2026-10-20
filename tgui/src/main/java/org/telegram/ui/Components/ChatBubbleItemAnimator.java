package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import android.view.ViewPropertyAnimator;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

/**
 * tgui: simplified take on upstream ChatListItemAnimator for a bottom-anchored message list.
 * New bubbles slide up from below (from behind the input panel) while the bubbles above move up
 * at the same time; rebinding a cell (status / grouping change) is not cross-faded.
 */
public class ChatBubbleItemAnimator extends DefaultItemAnimator {

    public ChatBubbleItemAnimator() {
        setDelayAnimations(false);
        setSupportsChangeAnimations(false);
        setInterpolator(CubicBezierInterpolator.DEFAULT);
        setAddDuration(220);
        setMoveDuration(220);
        setRemoveDuration(180);
    }

    @Override
    public boolean animateAdd(RecyclerView.ViewHolder holder) {
        resetAnimation(holder);
        View view = holder.itemView;
        int height = view.getMeasuredHeight();
        if (view.getParent() instanceof View) {
            View parent = (View) view.getParent();
            // start right below the visible list area so the bubble comes out from under the input panel
            height = Math.max(height, parent.getHeight() - parent.getPaddingBottom() - view.getTop());
        }
        view.setTranslationY(height);
        view.setAlpha(0f);
        mPendingAdditions.add(holder);
        checkIsRunning();
        return true;
    }

    @Override
    public void animateAddImpl(RecyclerView.ViewHolder holder, long delay) {
        final View view = holder.itemView;
        final ViewPropertyAnimator animation = view.animate();
        mAddAnimations.add(holder);
        animation.alpha(1f)
                .translationY(0)
                .setDuration(getAddDuration())
                .setStartDelay(delay)
                .setInterpolator(getAddInterpolator())
                .setUpdateListener(a -> onAddAnimationUpdate(holder))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        dispatchAddStarting(holder);
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {
                        view.setAlpha(1f);
                        view.setTranslationY(0);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        animation.setListener(null);
                        animation.setUpdateListener(null);
                        dispatchAddFinished(holder);
                        mAddAnimations.remove(holder);
                        dispatchFinishedWhenDone();
                    }
                }).start();
    }

    @Override
    protected void onAddAnimationUpdate(RecyclerView.ViewHolder holder) {
        invalidateParent(holder);
    }

    @Override
    protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
        invalidateParent(holder);
    }

    private static void invalidateParent(RecyclerView.ViewHolder holder) {
        // bubbles with a gradient depend on their on-screen position
        holder.itemView.invalidate();
    }
}
