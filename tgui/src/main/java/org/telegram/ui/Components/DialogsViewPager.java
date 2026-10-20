package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;

/**
 * tgui: the two chat-list pages of upstream 11.4.2 {@code DialogsActivity} ({@code viewPages[0..1]})
 * and the {@code ContentView} touch handling that drags them sideways between folders, keeping
 * {@link FilterTabsView} in step through {@code selectTabWithId(id, progress)}. Thresholds (0.3 cm to
 * start, a third of the width or 3500 px/s to switch, 150–600 ms settle) are upstream's.
 * <p>
 * The host owns the page views (typically one {@link DialogsListView} each) and fills a page for a
 * folder in {@link Delegate#onBindPage}. It also forwards its {@link FilterTabsView.FilterTabsViewDelegate}
 * {@code onPageSelected} / {@code onPageScrolled} to {@link #onTabSelected} / {@link #onTabScrolled}, so
 * tapping a tab slides the pages too:
 * <pre>
 * DialogsViewPager pager = new DialogsViewPager(context, pageA, pageB, filterTabsView, delegate);
 * // FilterTabsViewDelegate:
 * public void onPageSelected(Tab tab, boolean forward) { pager.onTabSelected(tab, forward); }
 * public void onPageScrolled(float progress) { pager.onTabScrolled(progress); }
 * </pre>
 */
public class DialogsViewPager extends FrameLayout {

    public interface Delegate {
        /** Fill {@code page} with folder {@code tabId} (upstream switchToCurrentSelectedMode), before it slides in. */
        void onBindPage(View page, int tabId);

        /** {@code page} became the visible page (swipe or tab tap finished). */
        default void onPageChanged(View page, int tabId) {
        }

        /** Whether a sideways drag may switch folders now (upstream: swipe gesture "folders", no action mode, drawer closed). */
        default boolean canSwipe() {
            return true;
        }
    }

    private static final Interpolator interpolator = t -> {
        --t;
        return t * t * t * t * t + 1.0F;
    };

    private final View[] viewPages = new View[2];
    private final int[] selectedTypes = new int[2];
    private final FilterTabsView filterTabsView;
    private final Delegate delegate;

    private AnimatorSet tabsAnimation;
    private boolean tabsAnimationInProgress;
    private boolean animatingForward;
    private float additionalOffset;
    private boolean backAnimation;
    private final int maximumVelocity;
    private boolean startedTracking;
    private boolean maybeStartTracking;
    private int startedTrackingPointerId;
    private int startedTrackingX;
    private int startedTrackingY;
    private VelocityTracker velocityTracker;

    public DialogsViewPager(Context context, View page0, View page1, FilterTabsView filterTabsView, Delegate delegate) {
        super(context);
        this.filterTabsView = filterTabsView;
        this.delegate = delegate;
        maximumVelocity = ViewConfiguration.get(context).getScaledMaximumFlingVelocity();
        viewPages[0] = page0;
        viewPages[1] = page1;
        addView(page0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addView(page1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        page1.setVisibility(View.GONE);
        selectedTypes[0] = filterTabsView.getCurrentTabId();
        selectedTypes[1] = -1;
        delegate.onBindPage(page0, selectedTypes[0]);
    }

    /** The visible page. */
    public View getCurrentPage() {
        return viewPages[0];
    }

    /** The page sliding in during a drag or tab animation (GONE otherwise). */
    public View getOtherPage() {
        return viewPages[1];
    }

    public int getCurrentTabId() {
        return selectedTypes[0];
    }

    /** Whether the pages are being dragged or settling. */
    public boolean isSwitching() {
        return startedTracking || tabsAnimationInProgress || viewPages[1].getVisibility() == View.VISIBLE;
    }

    /** Re-binds the visible page (after the host changed the folder contents). */
    public void rebindCurrentPage() {
        delegate.onBindPage(viewPages[0], selectedTypes[0]);
    }

    private void showScrollbars(boolean show) {
        for (View page : viewPages) {
            page.setVerticalScrollBarEnabled(show);
        }
    }

    private void bindPage(int index) {
        delegate.onBindPage(viewPages[index], selectedTypes[index]);
    }

    private void swapPages() {
        View page = viewPages[0];
        viewPages[0] = viewPages[1];
        viewPages[1] = page;
        int type = selectedTypes[0];
        selectedTypes[0] = selectedTypes[1];
        selectedTypes[1] = type;
    }

    private void pageChanged() {
        delegate.onPageChanged(viewPages[0], selectedTypes[0]);
    }

    // ---- FilterTabsView taps (upstream filterTabsView delegate) ----

    /** Forward of {@link FilterTabsView.FilterTabsViewDelegate#onPageSelected}. */
    public void onTabSelected(FilterTabsView.Tab tab, boolean forward) {
        if (selectedTypes[0] == tab.id) {
            return;
        }
        selectedTypes[1] = tab.id;
        viewPages[1].setVisibility(View.VISIBLE);
        viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth());
        showScrollbars(false);
        bindPage(1);
        animatingForward = forward;
    }

    /** Forward of {@link FilterTabsView.FilterTabsViewDelegate#onPageScrolled}. */
    public void onTabScrolled(float progress) {
        if (progress == 1 && viewPages[1].getVisibility() != View.VISIBLE) {
            return;
        }
        if (animatingForward) {
            viewPages[0].setTranslationX(-progress * viewPages[0].getMeasuredWidth());
            viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() - progress * viewPages[0].getMeasuredWidth());
        } else {
            viewPages[0].setTranslationX(progress * viewPages[0].getMeasuredWidth());
            viewPages[1].setTranslationX(progress * viewPages[0].getMeasuredWidth() - viewPages[0].getMeasuredWidth());
        }
        if (progress == 1) {
            swapPages();
            viewPages[1].setVisibility(View.GONE);
            showScrollbars(true);
            filterTabsView.stopAnimatingIndicator();
            pageChanged();
        }
    }

    // ---- drag between folders (upstream DialogsActivity.ContentView) ----

    private boolean prepareForMoving(MotionEvent ev, boolean forward) {
        int id = filterTabsView.getNextPageId(forward);
        if (id < 0) {
            return false;
        }
        getParent().requestDisallowInterceptTouchEvent(true);
        maybeStartTracking = false;
        startedTracking = true;
        startedTrackingX = (int) (ev.getX() + additionalOffset);
        filterTabsView.setEnabled(false);
        selectedTypes[1] = id;
        viewPages[1].setVisibility(View.VISIBLE);
        animatingForward = forward;
        showScrollbars(false);
        bindPage(1);
        if (forward) {
            viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth());
        } else {
            viewPages[1].setTranslationX(-viewPages[0].getMeasuredWidth());
        }
        return true;
    }

    public boolean checkTabsAnimationInProgress() {
        if (tabsAnimationInProgress) {
            boolean cancel = false;
            if (backAnimation) {
                if (Math.abs(viewPages[0].getTranslationX()) < 1) {
                    viewPages[0].setTranslationX(0);
                    viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() * (animatingForward ? 1 : -1));
                    cancel = true;
                }
            } else if (Math.abs(viewPages[1].getTranslationX()) < 1) {
                viewPages[0].setTranslationX(viewPages[0].getMeasuredWidth() * (animatingForward ? -1 : 1));
                viewPages[1].setTranslationX(0);
                cancel = true;
            }
            if (cancel) {
                showScrollbars(true);
                if (tabsAnimation != null) {
                    tabsAnimation.cancel();
                    tabsAnimation = null;
                }
                tabsAnimationInProgress = false;
            }
            return tabsAnimationInProgress;
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return checkTabsAnimationInProgress() || filterTabsView.isAnimatingIndicator() || onTouchEvent(ev);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // upstream's pages sit in a ViewPage FrameLayout, which swallows the redundant "false" that
        // RecyclerListView sends on every touch; the pages here are direct children, so only "true" counts
        if (disallowIntercept && maybeStartTracking && !startedTracking) {
            onTouchEvent(null);
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (filterTabsView.isEditing() || filterTabsView.getVisibility() != View.VISIBLE || !(startedTracking || tabsAnimationInProgress || delegate.canSwipe())) {
            return false;
        }
        if (ev != null) {
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
            velocityTracker.addMovement(ev);
        }
        if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && checkTabsAnimationInProgress()) {
            startedTracking = true;
            startedTrackingPointerId = ev.getPointerId(0);
            startedTrackingX = (int) ev.getX();
            if (animatingForward) {
                if (startedTrackingX < viewPages[0].getMeasuredWidth() + viewPages[0].getTranslationX()) {
                    additionalOffset = viewPages[0].getTranslationX();
                } else {
                    swapPages();
                    animatingForward = false;
                    additionalOffset = viewPages[0].getTranslationX();
                    filterTabsView.selectTabWithId(selectedTypes[0], 1f);
                    filterTabsView.selectTabWithId(selectedTypes[1], additionalOffset / viewPages[0].getMeasuredWidth());
                    pageChanged();
                }
            } else {
                if (startedTrackingX < viewPages[1].getMeasuredWidth() + viewPages[1].getTranslationX()) {
                    swapPages();
                    animatingForward = true;
                    additionalOffset = viewPages[0].getTranslationX();
                    filterTabsView.selectTabWithId(selectedTypes[0], 1f);
                    filterTabsView.selectTabWithId(selectedTypes[1], -additionalOffset / viewPages[0].getMeasuredWidth());
                    pageChanged();
                } else {
                    additionalOffset = viewPages[0].getTranslationX();
                }
            }
            tabsAnimation.removeAllListeners();
            tabsAnimation.cancel();
            tabsAnimationInProgress = false;
        } else if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN) {
            additionalOffset = 0;
        }
        if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
            startedTrackingPointerId = ev.getPointerId(0);
            maybeStartTracking = true;
            startedTrackingX = (int) ev.getX();
            startedTrackingY = (int) ev.getY();
            velocityTracker.clear();
        } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
            int dx = (int) (ev.getX() - startedTrackingX + additionalOffset);
            int dy = Math.abs((int) ev.getY() - startedTrackingY);
            if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
                if (!prepareForMoving(ev, dx < 0)) {
                    maybeStartTracking = true;
                    startedTracking = false;
                    viewPages[0].setTranslationX(0);
                    viewPages[1].setTranslationX(animatingForward ? viewPages[0].getMeasuredWidth() : -viewPages[0].getMeasuredWidth());
                    filterTabsView.selectTabWithId(selectedTypes[1], 0);
                }
            }
            if (maybeStartTracking && !startedTracking) {
                float touchSlop = AndroidUtilities.getPixelsInCM(0.3f, true);
                int dxLocal = (int) (ev.getX() - startedTrackingX);
                if (Math.abs(dxLocal) >= touchSlop && Math.abs(dxLocal) > dy) {
                    prepareForMoving(ev, dx < 0);
                }
            } else if (startedTracking) {
                viewPages[0].setTranslationX(dx);
                if (animatingForward) {
                    viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() + dx);
                } else {
                    viewPages[1].setTranslationX(dx - viewPages[0].getMeasuredWidth());
                }
                float scrollProgress = Math.abs(dx) / (float) viewPages[0].getMeasuredWidth();
                filterTabsView.selectTabWithId(selectedTypes[1], scrollProgress);
            }
        } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
            float velX;
            float velY;
            if (velocityTracker != null) {
                velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
            }
            if (ev != null && ev.getAction() != MotionEvent.ACTION_CANCEL && velocityTracker != null) {
                velX = velocityTracker.getXVelocity();
                velY = velocityTracker.getYVelocity();
                if (!startedTracking) {
                    if (Math.abs(velX) >= 3000 && Math.abs(velX) > Math.abs(velY)) {
                        prepareForMoving(ev, velX < 0);
                    }
                }
            } else {
                velX = 0;
                velY = 0;
            }
            if (startedTracking) {
                float x = viewPages[0].getX();
                tabsAnimation = new AnimatorSet();
                if (additionalOffset != 0) {
                    if (Math.abs(velX) > 1500) {
                        backAnimation = animatingForward ? velX > 0 : velX < 0;
                    } else {
                        if (animatingForward) {
                            backAnimation = (viewPages[1].getX() > (viewPages[0].getMeasuredWidth() >> 1));
                        } else {
                            backAnimation = (viewPages[0].getX() < (viewPages[0].getMeasuredWidth() >> 1));
                        }
                    }
                } else {
                    backAnimation = Math.abs(x) < viewPages[0].getMeasuredWidth() / 3.0f && (Math.abs(velX) < 3500 || Math.abs(velX) < Math.abs(velY));
                }
                float dx;
                ObjectAnimator page0Animator;
                if (backAnimation) {
                    dx = Math.abs(x);
                    page0Animator = ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, 0);
                    tabsAnimation.playTogether(
                            page0Animator,
                            ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, animatingForward ? viewPages[1].getMeasuredWidth() : -viewPages[1].getMeasuredWidth())
                    );
                } else {
                    dx = viewPages[0].getMeasuredWidth() - Math.abs(x);
                    page0Animator = ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, animatingForward ? -viewPages[0].getMeasuredWidth() : viewPages[0].getMeasuredWidth());
                    tabsAnimation.playTogether(
                            page0Animator,
                            ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, 0)
                    );
                }
                // upstream ViewPage.setTranslationX: the tab indicator follows the settling page
                page0Animator.addUpdateListener(animation -> {
                    if (tabsAnimationInProgress) {
                        float scrollProgress = Math.abs(viewPages[0].getTranslationX()) / (float) viewPages[0].getMeasuredWidth();
                        filterTabsView.selectTabWithId(selectedTypes[1], scrollProgress);
                    }
                });
                tabsAnimation.setInterpolator(interpolator);

                int width = getMeasuredWidth();
                int halfWidth = width / 2;
                float distanceRatio = Math.min(1.0f, 1.0f * dx / (float) width);
                float distance = (float) halfWidth + (float) halfWidth * AndroidUtilities.distanceInfluenceForSnapDuration(distanceRatio);
                velX = Math.abs(velX);
                int duration;
                if (velX > 0) {
                    duration = 4 * Math.round(1000.0f * Math.abs(distance / velX));
                } else {
                    float pageDelta = dx / getMeasuredWidth();
                    duration = (int) ((pageDelta + 1.0f) * 100.0f);
                }
                duration = Math.max(150, Math.min(duration, 600));

                tabsAnimation.setDuration(duration);
                tabsAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        tabsAnimation = null;
                        if (!backAnimation) {
                            swapPages();
                            filterTabsView.selectTabWithId(selectedTypes[0], 1.0f);
                            pageChanged();
                        }
                        viewPages[1].setVisibility(View.GONE);
                        showScrollbars(true);
                        tabsAnimationInProgress = false;
                        maybeStartTracking = false;
                        filterTabsView.setEnabled(true);
                    }
                });
                tabsAnimation.start();
                tabsAnimationInProgress = true;
                startedTracking = false;
            } else {
                maybeStartTracking = false;
                filterTabsView.setEnabled(true);
            }
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
        }
        return startedTracking;
    }
}
