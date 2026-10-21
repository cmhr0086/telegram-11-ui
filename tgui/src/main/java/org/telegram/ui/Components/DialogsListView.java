package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.tgui.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Cells.DialogCell;

/**
 * tgui: the chat-list RecyclerListView, after upstream {@code DialogsActivity.DialogsRecyclerView}.
 * Wires up {@link DialogsSwipeController} (swipe actions, pinned reordering), the upstream
 * {@link DialogsItemAnimator} and, with {@link #setArchivePullDelegate}, upstream's hidden archive:
 * while {@link SharedConfig#archiveHidden} is set the archive row (adapter position 0) sits above the
 * list and is revealed by pulling down ({@link PullForegroundDrawable}). The touch handling, the
 * pull parallax / overscroll and the snap thresholds are upstream's.
 * <pre>
 * DialogsListView list = new DialogsListView(context);
 * list.setSwipeDelegate(delegate);
 * list.setArchivePullDelegate(() -&gt; adapterHasArchiveRow);
 * list.setAdapter(adapter);   // items are DialogCell
 * </pre>
 */
public class DialogsListView extends RecyclerListView {

    /** Hidden-archive state, upstream ARCHIVE_ITEM_STATE_*. */
    public static final int ARCHIVE_ITEM_STATE_PINNED = 0;
    public static final int ARCHIVE_ITEM_STATE_SHOWED = 1;
    public static final int ARCHIVE_ITEM_STATE_HIDDEN = 2;

    public interface ArchivePullDelegate {
        /** Whether adapter position 0 is the archive row (a DialogCell bound to a folder item). */
        boolean hasArchive();

        /** The archive was hidden or pinned by a swipe on its row; {@link SharedConfig#archiveHidden} is already updated. */
        default void onArchiveHiddenChanged(boolean hidden) {
        }
    }

    private final DialogsItemAnimator itemAnimator;
    private final LayoutManager layoutManager;
    private DialogsSwipeController swipeController;
    private ItemTouchHelper itemTouchHelper;
    boolean allowSwipeDuringCurrentTouch = true;

    private ArchivePullDelegate archivePullDelegate;
    private PullForegroundDrawable pullForegroundDrawable;
    private int archivePullViewState = ARCHIVE_ITEM_STATE_PINNED;
    private long startArchivePullingTime;
    private boolean canShowHiddenArchive;
    private float viewOffset;
    private boolean waitingForScrollFinished;
    private boolean updatePullAfterScroll;
    private boolean firstLayout = true;
    private boolean ignoreLayout;
    private int lastListPadding;

    public DialogsListView(Context context) {
        super(context);
        setLayoutManager(layoutManager = new LayoutManager(context));
        setVerticalScrollBarEnabled(true);
        setItemAnimator(itemAnimator = new DialogsItemAnimator(this) {
            @Override
            public void onRemoveStarting(RecyclerView.ViewHolder item) {
                super.onRemoveStarting(item);
                if (layoutManager.findFirstVisibleItemPosition() == 0) {
                    View v = layoutManager.findViewByPosition(0);
                    if (v != null) {
                        v.invalidate();
                    }
                    if (archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN) {
                        archivePullViewState = ARCHIVE_ITEM_STATE_SHOWED;
                    }
                    if (pullForegroundDrawable != null) {
                        pullForegroundDrawable.doNotShow();
                    }
                }
            }
        });
        setInstantClick(true);
    }

    public DialogsItemAnimator getDialogsItemAnimator() {
        return itemAnimator;
    }

    /** Enables upstream swipe actions and pinned-chat dragging; null disables them. */
    public void setSwipeDelegate(DialogsSwipeController.Delegate delegate) {
        if (itemTouchHelper != null) {
            itemTouchHelper.attachToRecyclerView(null);
            itemTouchHelper = null;
            swipeController = null;
        }
        if (delegate != null) {
            swipeController = new DialogsSwipeController(this, delegate);
            itemTouchHelper = new ItemTouchHelper(swipeController);
            itemTouchHelper.attachToRecyclerView(this);
        }
    }

    /** Starts dragging a row (upstream starts drags from long-press in selection mode). */
    public void startDrag(ViewHolder holder) {
        if (itemTouchHelper != null) {
            itemTouchHelper.startDrag(holder);
        }
    }

    // ---- hidden archive (upstream DialogsActivity pull-to-reveal) ----

    /** Enables the hidden archive; null disables it (the archive row, if any, is a plain row). */
    public void setArchivePullDelegate(ArchivePullDelegate delegate) {
        archivePullDelegate = delegate;
        if (delegate != null && pullForegroundDrawable == null) {
            pullForegroundDrawable = new PullForegroundDrawable(LocaleController.getString(R.string.AccSwipeForArchive), LocaleController.getString(R.string.AccReleaseForArchive)) {
                @Override
                protected float getViewOffset() {
                    return viewOffset;
                }
            };
        } else if (delegate == null) {
            pullForegroundDrawable = null;
        }
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof DialogCell) {
                ((DialogCell) child).setArchivedPullAnimation(pullForegroundDrawable);
            }
        }
        updatePullState();
    }

    public PullForegroundDrawable getPullForegroundDrawable() {
        return pullForegroundDrawable;
    }

    /** Upstream MessagesController.hasHiddenArchive(): archive hidden in settings and present in the list. */
    public boolean hasHiddenArchive() {
        return archivePullDelegate != null && SharedConfig.archiveHidden && archivePullDelegate.hasArchive();
    }

    public int getArchivePullViewState() {
        return archivePullViewState;
    }

    /** Re-reads {@link SharedConfig#archiveHidden} and whether the archive row exists (call after changing either). */
    public void updatePullState() {
        archivePullViewState = SharedConfig.archiveHidden ? ARCHIVE_ITEM_STATE_HIDDEN : ARCHIVE_ITEM_STATE_PINNED;
        if (pullForegroundDrawable != null) {
            if (hasHiddenArchive()) {
                pullForegroundDrawable.showHidden();
            } else {
                pullForegroundDrawable.doNotShow();
            }
            pullForegroundDrawable.setWillDraw(archivePullViewState != ARCHIVE_ITEM_STATE_PINNED);
        }
    }

    /** Upstream: theme change. */
    public void updatePullColors() {
        if (pullForegroundDrawable != null) {
            pullForegroundDrawable.updateColors();
        }
    }

    /** The first chat row: position 1 while the archive is hidden above the list, else 0. */
    public int getTopPosition() {
        return hasHiddenArchive() && archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN ? 1 : 0;
    }

    /** Upstream scrollToTop / switchToCurrentSelectedMode: scrolls to the first chat, keeping a hidden archive hidden. */
    public void scrollToTop(boolean animated) {
        if (animated) {
            smoothScrollToPosition(getTopPosition());
        } else {
            layoutManager.scrollToPositionWithOffset(getTopPosition(), 0);
        }
    }

    /**
     * Upstream DialogsRecyclerView.toggleArchiveHidden: the archive row's swipe (hide / pin). Hiding scrolls
     * the row away above the list.
     */
    public void toggleArchiveHidden(boolean action, DialogCell dialogCell) {
        SharedConfig.toggleArchiveHidden();
        if (SharedConfig.archiveHidden) {
            if (dialogCell != null) {
                waitingForScrollFinished = true;
                int offset = dialogCell.getMeasuredHeight() + (dialogCell.getTop() - getPaddingTop());
                smoothScrollBy(0, offset, CubicBezierInterpolator.EASE_OUT);
                if (action) {
                    updatePullAfterScroll = true;
                } else {
                    updatePullState();
                }
            } else {
                updatePullState();
            }
        } else {
            updatePullState();
            if (action && dialogCell != null) {
                dialogCell.resetPinnedArchiveState();
                dialogCell.invalidate();
            }
        }
        if (archivePullDelegate != null) {
            archivePullDelegate.onArchiveHiddenChanged(SharedConfig.archiveHidden);
        }
    }

    public void setViewsOffset(float viewOffset) {
        this.viewOffset = viewOffset;
        int n = getChildCount();
        for (int i = 0; i < n; i++) {
            getChildAt(i).setTranslationY(viewOffset);
        }
        if (selectorPosition != NO_POSITION) {
            View v = getLayoutManager().findViewByPosition(selectorPosition);
            if (v != null) {
                selectorRect.set(v.getLeft(), (int) (v.getTop() + viewOffset), v.getRight(), (int) (v.getBottom() + viewOffset));
                selectorDrawable.setBounds(selectorRect);
            }
        }
        invalidate();
    }

    public float getViewOffset() {
        return viewOffset;
    }

    private DialogCell findArchiveDialogCell() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof DialogCell && ((DialogCell) child).isFolderCell()) {
                return (DialogCell) child;
            }
        }
        return null;
    }

    @Override
    public void onChildAttachedToWindow(View child) {
        super.onChildAttachedToWindow(child);
        if (child instanceof DialogCell) {
            ((DialogCell) child).setArchivedPullAnimation(pullForegroundDrawable);
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        child.setTranslationY(viewOffset);
        child.setTranslationX(0);
        child.setAlpha(1f);
    }

    @Override
    public void removeView(View view) {
        super.removeView(view);
        view.setTranslationY(0);
        view.setTranslationX(0);
        view.setAlpha(1f);
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (pullForegroundDrawable != null && viewOffset != 0) {
            int pTop = getPaddingTop();
            if (pTop != 0) {
                canvas.save();
                canvas.translate(0, pTop);
            }
            pullForegroundDrawable.drawOverScroll(canvas);
            if (pTop != 0) {
                canvas.restore();
            }
        }
        super.onDraw(canvas);
    }

    @Override
    public void setAdapter(RecyclerView.Adapter adapter) {
        super.setAdapter(adapter);
        firstLayout = true;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // upstream: keep the first visible row across relayouts, and never let a relayout reveal a hidden archive
        int pos = layoutManager.findFirstVisibleItemPosition();
        if (pos != RecyclerView.NO_POSITION && (itemTouchHelper == null || itemTouchHelper.isIdle()) && !layoutManager.hasPendingScrollPosition() && getScrollState() != RecyclerView.SCROLL_STATE_DRAGGING) {
            RecyclerView.ViewHolder holder = findViewHolderForAdapterPosition(pos);
            if (holder != null) {
                int top = holder.itemView.getTop();
                if (hasHiddenArchive() && archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN) {
                    pos = Math.max(1, pos);
                }
                ignoreLayout = true;
                layoutManager.scrollToPositionWithOffset(pos, top - lastListPadding);
                ignoreLayout = false;
            }
        } else if (pos == RecyclerView.NO_POSITION && firstLayout) {
            layoutManager.scrollToPositionWithOffset(hasHiddenArchive() ? 1 : 0, 0);
            firstLayout = false;
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        lastListPadding = getPaddingTop();
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    public void onScrollStateChanged(int state) {
        super.onScrollStateChanged(state);
        if (state == RecyclerView.SCROLL_STATE_IDLE && waitingForScrollFinished) {
            waitingForScrollFinished = false;
            if (updatePullAfterScroll) {
                updatePullState();
                updatePullAfterScroll = false;
            }
            if (getAdapter() != null) {
                getAdapter().notifyDataSetChanged();
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (waitingForScrollFinished || itemAnimator.isRunning()) {
            return false;
        }
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            allowSwipeDuringCurrentTouch = true;
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (waitingForScrollFinished) {
            return false;
        }
        int action = e.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        }
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && swipeController != null) {
            swipeController.onTouchUp(itemTouchHelper);
        }
        boolean result = super.onTouchEvent(e);
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN && hasHiddenArchive()) {
            int currentPosition = layoutManager.findFirstVisibleItemPosition();
            if (currentPosition == 0) {
                int pTop = getPaddingTop();
                DialogCell view = findArchiveDialogCell();
                if (view != null) {
                    int height = (int) (dp(SharedConfig.useThreeLinesLayout ? 78 : 72) * PullForegroundDrawable.SNAP_HEIGHT);
                    int diff = (view.getTop() - pTop) + view.getMeasuredHeight();

                    long pullingTime = System.currentTimeMillis() - startArchivePullingTime;
                    if (diff < height || pullingTime < PullForegroundDrawable.minPullingTime) {
                        smoothScrollBy(0, diff, CubicBezierInterpolator.EASE_OUT_QUINT);
                        archivePullViewState = ARCHIVE_ITEM_STATE_HIDDEN;
                    } else {
                        if (archivePullViewState != ARCHIVE_ITEM_STATE_SHOWED) {
                            if (getViewOffset() == 0) {
                                smoothScrollBy(0, (view.getTop() - pTop), CubicBezierInterpolator.EASE_OUT_QUINT);
                            }
                            if (!canShowHiddenArchive) {
                                canShowHiddenArchive = true;
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                                if (pullForegroundDrawable != null) {
                                    pullForegroundDrawable.colorize(true);
                                }
                            }
                            view.startOutAnimation();
                            archivePullViewState = ARCHIVE_ITEM_STATE_SHOWED;
                        }
                    }

                    if (getViewOffset() != 0) {
                        ValueAnimator valueAnimator = ValueAnimator.ofFloat(getViewOffset(), 0f);
                        valueAnimator.addUpdateListener(animation -> setViewsOffset((float) animation.getAnimatedValue()));
                        valueAnimator.setDuration(Math.max(100, (long) (350f - 120f * (getViewOffset() / PullForegroundDrawable.getMaxOverscroll()))));
                        valueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                        setScrollEnabled(false);
                        valueAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                setScrollEnabled(true);
                            }
                        });
                        valueAnimator.start();
                    }
                }
            }
        }
        return result;
    }

    /** Upstream DialogsActivity ViewPage layout manager: the pull parallax, overscroll and snap state. */
    private class LayoutManager extends LinearLayoutManager {

        LayoutManager(Context context) {
            super(context, LinearLayoutManager.VERTICAL, false);
        }

        @Override
        protected int firstPosition() {
            return getTopPosition();
        }

        @Override
        public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
            boolean isDragging = getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING;
            int measuredDy = dy;
            int pTop = getPaddingTop();
            boolean hasHidenArchive = archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN && hasHiddenArchive();
            if (hasHidenArchive && dy < 0) {
                setOverScrollMode(View.OVER_SCROLL_ALWAYS);
                int currentPosition = findFirstVisibleItemPosition();
                if (currentPosition == 0) {
                    View view = findViewByPosition(currentPosition);
                    if (view != null && (view.getBottom() - pTop) <= dp(1)) {
                        currentPosition = 1;
                    }
                }
                if (!isDragging) {
                    // a fling stops at the first chat instead of revealing the archive
                    View view = findViewByPosition(currentPosition);
                    if (view != null && currentPosition < 10) {
                        // upstream sums DialogsAdapter.getItemHeight(1..currentPosition-1); chat rows share one height
                        int viewsH = Math.max(0, currentPosition - 1) * view.getMeasuredHeight();
                        int canScrollDy = -(view.getTop() - pTop) + viewsH;
                        int positiveDy = Math.abs(dy);
                        if (canScrollDy < positiveDy) {
                            measuredDy = -canScrollDy;
                        }
                    }
                } else if (currentPosition == 0) {
                    View v = findViewByPosition(currentPosition);
                    float k = 1f + ((v.getTop() - pTop) / (float) v.getMeasuredHeight());
                    if (k > 1f) {
                        k = 1f;
                    }
                    setOverScrollMode(View.OVER_SCROLL_NEVER);
                    measuredDy *= PullForegroundDrawable.startPullParallax - PullForegroundDrawable.endPullParallax * k;
                    if (measuredDy > -1) {
                        measuredDy = -1;
                    }
                }
            }

            if (viewOffset != 0 && dy > 0 && isDragging) {
                float ty = (int) viewOffset;
                ty -= dy;
                if (ty < 0) {
                    measuredDy = (int) ty;
                    ty = 0;
                } else {
                    measuredDy = 0;
                }
                setViewsOffset(ty);
            }

            if (archivePullViewState != ARCHIVE_ITEM_STATE_PINNED && hasHiddenArchive()) {
                int usedDy = super.scrollVerticallyBy(measuredDy, recycler, state);
                if (pullForegroundDrawable != null) {
                    pullForegroundDrawable.scrollDy = usedDy;
                }
                int currentPosition = findFirstVisibleItemPosition();
                View firstView = null;
                if (currentPosition == 0) {
                    firstView = findViewByPosition(currentPosition);
                }
                if (currentPosition == 0 && firstView != null && (firstView.getBottom() - pTop) >= dp(4)) {
                    if (startArchivePullingTime == 0) {
                        startArchivePullingTime = System.currentTimeMillis();
                    }
                    if (archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN) {
                        if (pullForegroundDrawable != null) {
                            pullForegroundDrawable.showHidden();
                        }
                    }
                    float k = 1f + ((firstView.getTop() - pTop) / (float) firstView.getMeasuredHeight());
                    if (k > 1f) {
                        k = 1f;
                    }
                    long pullingTime = System.currentTimeMillis() - startArchivePullingTime;
                    boolean canShowInternal = k > PullForegroundDrawable.SNAP_HEIGHT && pullingTime > PullForegroundDrawable.minPullingTime + 20;
                    if (canShowHiddenArchive != canShowInternal) {
                        canShowHiddenArchive = canShowInternal;
                        if (archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN) {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                            if (pullForegroundDrawable != null) {
                                pullForegroundDrawable.colorize(canShowInternal);
                            }
                        }
                    }
                    if (archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN && measuredDy - usedDy != 0 && dy < 0 && isDragging) {
                        float ty;
                        float tk = (viewOffset / PullForegroundDrawable.getMaxOverscroll());
                        tk = 1f - tk;
                        ty = (viewOffset - dy * PullForegroundDrawable.startPullOverScroll * tk);
                        setViewsOffset(ty);
                    }
                    if (pullForegroundDrawable != null) {
                        pullForegroundDrawable.pullProgress = k;
                        pullForegroundDrawable.setListView(DialogsListView.this);
                    }
                } else {
                    startArchivePullingTime = 0;
                    canShowHiddenArchive = false;
                    archivePullViewState = ARCHIVE_ITEM_STATE_HIDDEN;
                    if (pullForegroundDrawable != null) {
                        pullForegroundDrawable.resetText();
                        pullForegroundDrawable.pullProgress = 0f;
                        pullForegroundDrawable.setListView(DialogsListView.this);
                    }
                }
                if (firstView != null) {
                    firstView.invalidate();
                }
                return usedDy;
            }
            return super.scrollVerticallyBy(measuredDy, recycler, state);
        }
    }
}
