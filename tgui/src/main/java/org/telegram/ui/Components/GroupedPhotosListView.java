/*
 * This is the source code of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov and contributors.
 * Adapted from release-11.4.2-5469 GroupedPhotosListView.
 */
package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Scroller;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.HashSet;

public class GroupedPhotosListView extends View implements GestureDetector.OnGestureListener {

    private Paint backgroundPaint = new Paint();
    private ArrayList<DrawableReceiver> unusedReceivers = new ArrayList<>();
    private ArrayList<DrawableReceiver> imagesToDraw = new ArrayList<>();
    private final ArrayList<Entry> currentPhotos = new ArrayList<>();
    private List<Entry> items = Collections.emptyList();
    private int selectedIndex = -1;
    private boolean destroyed, drawBackground = true;
    private long dataGeneration;
    private int currentImage;
    private int itemWidth;
    private int itemHeight;
    private int itemY;
    private int itemSpacing;
    private int drawDx;
    private int lastLayoutWidth, lastLayoutHeight;
    private float moveLineProgress;
    private float currentItemProgress = 1.0f;
    private float nextItemProgress = 0.0f;
    private int nextImage;
    private long lastUpdateTime;
    private boolean moving;
    private boolean animateAllLine;
    private int animateToDX;
    private int animateToDXStart;
    private int animateToItem = -1;
    private boolean animateToItemFast;
    private android.widget.Scroller scroll;
    private GestureDetector gestureDetector;
    private boolean scrolling;
    private boolean stopedScrolling;
    private boolean ignoreChanges;
    private boolean animationsEnabled = true;
    private int nextPhotoScrolling = -1;
    private boolean hasPhotos;
    private boolean animateBackground = true;

    private Delegate delegate;

    private ValueAnimator showAnimator;
    private ValueAnimator hideAnimator;
    private float drawAlpha;

    /** One thumbnail in the album supplied by the host; index is the absolute viewer index.
     * Drawable ownership stays with the host. Bounds/alpha are not retained or changed after draw.
     * Use a static drawable, or invalidate this view when a host-managed thumbnail changes. */
    public static final class Entry {
        public final int index, width, height;
        public final Drawable drawable;
        public Entry(int index, Drawable drawable, int width, int height) {
            if (index < 0 || width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid thumbnail index or dimensions");
            this.index = index; this.drawable = drawable; this.width = width; this.height = height;
        }
    }

    public interface Delegate {
        void onIndexSelected(int index);
        default void onShowAnimationStart() { }
        /** The last retained thumbnails have finished fading out; getCount() is now zero. */
        default void onHideAnimationEnd() { }
        default void onStopScrolling() { }
    }

    public GroupedPhotosListView(Context context) {
        this(context, AndroidUtilities.dp(3));
    }

    public GroupedPhotosListView(Context context, int paddingTop) {
        super(context);
        gestureDetector = new GestureDetector(context, this);
        scroll = new Scroller(context);
        itemWidth = AndroidUtilities.dp(42);
        itemHeight = AndroidUtilities.dp(56);
        itemSpacing = AndroidUtilities.dp(1);
        itemY = paddingTop;
        backgroundPaint.setColor(0x7f000000);
    }

    /** Replaces the current album. Selection and callbacks use absolute Entry.index values. */
    public void setItems(List<Entry> values, int currentIndex) {
        if (destroyed) return;
        ArrayList<Entry> copy = new ArrayList<>();
        HashSet<Integer> ids = new HashSet<>();
        if (values != null) for (Entry entry : values) {
            if (entry == null || !ids.add(entry.index)) throw new IllegalArgumentException("Thumbnail indices must be unique");
            copy.add(entry);
        }
        if (!copy.isEmpty() && !ids.contains(currentIndex)) throw new IllegalArgumentException("Current index is outside the supplied album");
        boolean same = sameItems(items, copy);
        if (!same) { dataGeneration++; ignoreChanges = false; abortMotion(); }
        items = Collections.unmodifiableList(copy);
        selectedIndex = copy.isEmpty() ? -1 : currentIndex;
        fillList();
    }

    public void setCurrentIndex(int currentIndex) {
        if (destroyed || items.isEmpty()) return;
        if (selectedIndex == currentIndex) return;
        if (indexOf(items, currentIndex) < 0) throw new IllegalArgumentException("Current index is outside the supplied album");
        selectedIndex = currentIndex;
        fillList();
    }
    private static int indexOf(List<Entry> list, int index) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).index == index) return i;
        return -1;
    }
    private static boolean sameItems(List<Entry> a, List<Entry> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Entry x = a.get(i), y = b.get(i);
            if (x.index != y.index || x.drawable != y.drawable || x.width != y.width || x.height != y.height) return false;
        }
        return true;
    }
    private void cancelFades() {
        if (showAnimator != null) { showAnimator.removeAllListeners(); showAnimator.removeAllUpdateListeners(); showAnimator.cancel(); showAnimator = null; }
        if (hideAnimator != null) { hideAnimator.removeAllListeners(); hideAnimator.removeAllUpdateListeners(); hideAnimator.cancel(); hideAnimator = null; }
    }
    private void abortMotion() {
        scroll.abortAnimation(); scrolling = stopedScrolling = moving = animateAllLine = false;
        animateToItem = nextPhotoScrolling = -1; animateToItemFast = false;
        drawDx = 0; currentItemProgress = moveLineProgress = 1f; nextItemProgress = 0;
    }
    private void releaseReceivers() {
        for (DrawableReceiver receiver : imagesToDraw) receiver.setEntry(null);
        for (DrawableReceiver receiver : unusedReceivers) receiver.setEntry(null);
        imagesToDraw.clear(); unusedReceivers.clear();
    }
    public void clear() {
        dataGeneration++; cancelFades(); abortMotion(); releaseReceivers();
        items = Collections.emptyList(); currentPhotos.clear(); selectedIndex = -1;
        currentImage = 0; ignoreChanges = false; hasPhotos = false; drawAlpha = 0;
        invalidate();
    }
    public void destroy() { if (!destroyed) { clear(); destroyed = true; delegate = null; } }
    public void setDrawBackground(boolean draw) { drawBackground = draw; invalidate(); }

    private void fillList() {
        if (destroyed) return;
        boolean changed = !sameItems(items, currentPhotos);
        if (ignoreChanges && !changed) { ignoreChanges = false; return; }
        ignoreChanges = false;
        hasPhotos = items.size() > 1;
        int newCount = hasPhotos ? items.size() : 0;
        int newImageIndex = indexOf(items, selectedIndex);
        if (animationsEnabled) {
            if (!hasPhotos) {
                if (showAnimator != null) {
                    showAnimator.cancel();
                    showAnimator = null;
                }
                if (drawAlpha > 0f && currentPhotos.size() > 1) {
                    if (hideAnimator == null) {
                        hideAnimator = ValueAnimator.ofFloat(drawAlpha, 0f);
                        hideAnimator.setDuration((long) (200 * drawAlpha));
                        hideAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (hideAnimator == animation) {
                                    hideAnimator = null;
                                    fillList();
                                }
                            }
                        });
                        hideAnimator.addUpdateListener(a -> {
                            drawAlpha = (float) a.getAnimatedValue();
                            invalidate();
                        });
                        hideAnimator.start();
                    }
                    return;
                }
            } else {
                if (hideAnimator != null) {
                    final Animator a = hideAnimator;
                    hideAnimator = null;
                    a.cancel();
                }
                if (drawAlpha < 1f && showAnimator == null) {
                    showAnimator = ValueAnimator.ofFloat(drawAlpha, 1f);
                    showAnimator.setDuration((long) (200 * (1f - drawAlpha)));
                    showAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            if (delegate != null) {
                                delegate.onShowAnimationStart();
                            }
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (showAnimator == animation) {
                                showAnimator = null;
                            }
                        }
                    });
                    showAnimator.addUpdateListener(a -> {
                        drawAlpha = (float) a.getAnimatedValue();
                        invalidate();
                    });
                }
            }
        }
        else {
            cancelFades(); drawAlpha = hasPhotos ? 1f : 0f;
        }
        if (!hasPhotos) {
            finishHiding(); return;
        }
        if (!changed) {
            if (newCount != currentPhotos.size() || newImageIndex < 0) {
                changed = true;
            } else {
                if (currentImage != newImageIndex && newImageIndex != -1) {
                    boolean animate = animateAllLine;
                    if (!animate && !moving && (newImageIndex == currentImage - 1 || newImageIndex == currentImage + 1)) {
                        animate = true;
                        animateToItemFast = true;
                    }
                    if (animate) {
                        nextImage = animateToItem = newImageIndex;
                        animateToDX = (currentImage - newImageIndex) * (itemWidth + itemSpacing);
                        moving = true;
                        animateAllLine = false;
                        lastUpdateTime = System.currentTimeMillis();
                        invalidate();
                    } else {
                        fillImages(true, (currentImage - newImageIndex) * (itemWidth + itemSpacing));
                        currentImage = newImageIndex;
                        moving = false;
                    }
                    drawDx = 0;
                }
            }
        }
        if (changed) {
            int oldCount = currentPhotos.size();
            abortMotion(); releaseReceivers(); currentPhotos.clear();
            currentPhotos.addAll(items); currentImage = newImageIndex;
            if (currentPhotos.size() != oldCount) requestLayout();
            fillImages(false, 0);
        }
    }

    private void finishHiding() {
        boolean hadPhotos = !currentPhotos.isEmpty();
        releaseReceivers(); currentPhotos.clear(); currentImage = 0;
        drawAlpha = 0f; invalidate();
        if (hadPhotos && !destroyed && delegate != null) delegate.onHideAnimationEnd();
    }

    /** Drawn album count, including retained thumbnails during the 200ms fade-out. */
    public int getCount() {
        return currentPhotos.size();
    }

    /** True during drag/fling selection callbacks; false for a normal thumbnail tap. */
    public boolean isScrolling() {
        return scrolling || nextPhotoScrolling >= 0 || !scroll.isFinished();
    }

    public int getIndex() {
        return selectedIndex;
    }

    public void setMoveProgress(float progress) {
        if (destroyed || !hasPhotos || Float.isNaN(progress) || Float.isInfinite(progress)) return;
        progress = Math.max(-1f, Math.min(1f, progress));
        if (scrolling || animateToItem >= 0) {
            return;
        }
        if (progress > 0) {
            nextImage = currentImage - 1;
        } else {
            nextImage = currentImage + 1;
        }
        if (nextImage >= 0 && nextImage < currentPhotos.size()) {
            currentItemProgress = 1.0f - Math.abs(progress);
        } else {
            currentItemProgress = 1.0f;
        }
        nextItemProgress = 1.0f - currentItemProgress;
        moving = progress != 0;
        invalidate();
        if (currentPhotos.isEmpty() || progress < 0 && currentImage == currentPhotos.size() - 1 || progress > 0 && currentImage == 0) {
            return;
        }
        drawDx = (int) (progress * (itemWidth + itemSpacing));
        fillImages(true, drawDx);
    }

    private DrawableReceiver getFreeReceiver() {
        DrawableReceiver receiver;
        if (unusedReceivers.isEmpty()) {
            receiver = new DrawableReceiver();
        } else {
            receiver = unusedReceivers.get(0);
            unusedReceivers.remove(0);
        }
        imagesToDraw.add(receiver);
        return receiver;
    }

    private void fillImages(boolean move, int dx) {
        if (!move && !imagesToDraw.isEmpty()) {
            for (DrawableReceiver receiver : imagesToDraw) receiver.setEntry(null);
            unusedReceivers.addAll(imagesToDraw);
            imagesToDraw.clear();
            moving = false;
            moveLineProgress = 1.0f;
            currentItemProgress = 1.0f;
            nextItemProgress = 0.0f;
        }
        invalidate();
        if (getMeasuredWidth() == 0 || currentPhotos.isEmpty()) {
            return;
        }
        int width = getMeasuredWidth();
        int startX = getMeasuredWidth() / 2 - itemWidth / 2;

        int addRightIndex;
        int addLeftIndex;
        if (move) {
            addRightIndex = Integer.MIN_VALUE;
            addLeftIndex = Integer.MAX_VALUE;
            int count = imagesToDraw.size();
            for (int a = 0; a < count; a++) {
                DrawableReceiver receiver = imagesToDraw.get(a);
                int num = receiver.getParam();
                int x = startX + (num - currentImage) * (itemWidth + itemSpacing) + dx;
                if (x > width || x + itemWidth < 0) {
                    receiver.setEntry(null);
                    unusedReceivers.add(receiver);
                    imagesToDraw.remove(a);
                    count--;
                    a--;
                }
                addLeftIndex = Math.min(addLeftIndex, num - 1);
                addRightIndex = Math.max(addRightIndex, num + 1);
            }
        } else {
            addRightIndex = currentImage;
            addLeftIndex = currentImage - 1;
        }

        if (addRightIndex != Integer.MIN_VALUE) {
            int count = currentPhotos.size();
            for (int a = addRightIndex; a < count; a++) {
                int x = startX + (a - currentImage) * (itemWidth + itemSpacing) + dx;
                if (x < width) {
                    Entry location = currentPhotos.get(a);
                    DrawableReceiver receiver = getFreeReceiver();
                    receiver.setImageCoords(x, itemY, itemWidth, itemHeight);
                    receiver.setEntry(location);
                    receiver.setParam(a);
                } else {
                    break;
                }
            }
        }
        if (addLeftIndex != Integer.MAX_VALUE) {
            for (int a = addLeftIndex; a >= 0; a--) {
                int x = startX + (a - currentImage) * (itemWidth + itemSpacing) + dx + itemWidth;
                if (x > 0) {
                    Entry location = currentPhotos.get(a);
                    DrawableReceiver receiver = getFreeReceiver();
                    receiver.setImageCoords(x, itemY, itemWidth, itemHeight);
                    receiver.setEntry(location);
                    receiver.setParam(a);
                } else {
                    break;
                }
            }
        }
        if (showAnimator != null && !showAnimator.isStarted()) {
            showAnimator.start();
        }
    }

    @Override
    public boolean onDown(MotionEvent e) {
        if (!scroll.isFinished()) {
            scroll.abortAnimation();
        }
        animateToItem = -1;
        animateToItemFast = false;
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        if (destroyed || !hasPhotos) return false;
        stopScrolling();
        for (DrawableReceiver receiver : imagesToDraw) {
            if (receiver.isInsideImage(e.getX(), e.getY())) {
                int num = receiver.getParam();
                if (num < 0 || num >= currentPhotos.size()) return true;
                int index = currentPhotos.get(num).index;
                if (selectedIndex == index) return true;
                moveLineProgress = 1f; animateAllLine = true;
                setCurrentIndex(index);
                if (delegate != null) delegate.onIndexSelected(index);
                return true;
            }
        }
        return false;
    }

    private void updateAfterScroll() {
        int indexChange = 0;
        int dx = drawDx;
        if (Math.abs(dx) > itemWidth / 2 + itemSpacing) {
            if (dx > 0) {
                dx -= itemWidth / 2 + itemSpacing;
                indexChange++;
            } else {
                dx += itemWidth / 2 + itemSpacing;
                indexChange--;
            }
            indexChange += dx / (itemWidth + itemSpacing * 2);
        }
        nextPhotoScrolling = currentImage - indexChange;

        if (nextPhotoScrolling >= 0 && nextPhotoScrolling < currentPhotos.size()) {
            int index = currentPhotos.get(nextPhotoScrolling).index;
            if (selectedIndex != index) {
                selectedIndex = index;
                ignoreChanges = true;
                long generation = dataGeneration;
                if (delegate != null) delegate.onIndexSelected(index);
                ignoreChanges = false;
                if (destroyed || generation != dataGeneration) return;
            }
        }
        if (!scrolling) {
            scrolling = true;
            stopedScrolling = false;
        }
        fillImages(true, drawDx);
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        drawDx -= distanceX;
        int min = getMinScrollX();
        int max = getMaxScrollX();
        if (drawDx < min) {
            drawDx = min;
        } else if (drawDx > max) {
            drawDx = max;
        }
        updateAfterScroll();
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        scroll.abortAnimation();
        if (currentPhotos.size() >= 10) {
            scroll.fling(drawDx, 0, Math.round(velocityX), 0, getMinScrollX(), getMaxScrollX(), 0, 0);
        }
        return false;
    }

    private void stopScrolling() {
        scrolling = false;
        if (!scroll.isFinished()) {
            scroll.abortAnimation();
        }
        if (nextPhotoScrolling >= 0 && nextPhotoScrolling < currentPhotos.size()) {
            stopedScrolling = true;
            animateToItemFast = false;
            nextImage = animateToItem = nextPhotoScrolling;
            animateToDX = (currentImage - nextPhotoScrolling) * (itemWidth + itemSpacing);
            animateToDXStart = drawDx;
            moveLineProgress = 1.0f;
            nextPhotoScrolling = -1;
            if (delegate != null) {
                delegate.onStopScrolling();
            }
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (destroyed || !hasPhotos || currentPhotos.isEmpty() || getAlpha() != 1.0f) {
            return false;
        }
        boolean result = gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
        if (scrolling && (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP && scroll.isFinished())) {
            stopScrolling();
        }
        return result;
    }

    private int getMinScrollX() {
        return -(currentPhotos.size() - currentImage - 1) * (itemWidth + itemSpacing * 2);
    }

    private int getMaxScrollX() {
        return currentImage * (itemWidth + itemSpacing * 2);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        boolean sizeChanged = lastLayoutWidth != right - left || lastLayoutHeight != bottom - top;
        lastLayoutWidth = right - left; lastLayoutHeight = bottom - top;
        if (sizeChanged) {
            abortMotion();
            currentImage = Math.max(0, indexOf(currentPhotos, selectedIndex));
        }
        if (sizeChanged || imagesToDraw.isEmpty()) fillImages(false, 0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (destroyed || currentPhotos.isEmpty() || currentImage < 0 || currentImage >= currentPhotos.size() || !hasPhotos && imagesToDraw.isEmpty()) {
            return;
        }
        float bgAlpha = drawAlpha;
        if (!animateBackground) {
            bgAlpha = hasPhotos ? 1f : 0f;
        }
        backgroundPaint.setAlpha((int) (0x7F * bgAlpha));
        if (drawBackground) canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
        if (imagesToDraw.isEmpty()) {
            return;
        }
        int count = imagesToDraw.size();

        int moveX = drawDx;

        int maxItemWidth = (int) (itemWidth * 2.0f);
        int padding = AndroidUtilities.dp(8);

        Entry object = currentPhotos.get(currentImage);
        int trueWidth;
        int currentPaddings;
        if (object != null && object.height > 0) {
            trueWidth = Math.max(itemWidth, (int) (object.width * (itemHeight / (float) object.height)));
        } else {
            trueWidth = itemHeight;
        }
        trueWidth = Math.min(maxItemWidth, trueWidth);
        currentPaddings = (int) (padding * 2 * currentItemProgress);
        trueWidth = itemWidth + (int) ((trueWidth - itemWidth) * currentItemProgress) + currentPaddings;

        int nextTrueWidth;
        int nextPaddings;
        if (nextImage >= 0 && nextImage < currentPhotos.size()) {
            object = currentPhotos.get(nextImage);
            if (object != null && object.height > 0) {
                nextTrueWidth = Math.max(itemWidth, (int) (object.width * (itemHeight / (float) object.height)));
            } else {
                nextTrueWidth = itemHeight;
            }
        } else {
            nextTrueWidth = itemWidth;
        }
        nextTrueWidth = Math.min(maxItemWidth, nextTrueWidth);
        nextPaddings = (int) (padding * 2 * nextItemProgress);
        moveX += (nextTrueWidth + nextPaddings - itemWidth) / 2 * nextItemProgress * (nextImage > currentImage ? -1 : 1);
        nextTrueWidth = itemWidth + (int) ((nextTrueWidth - itemWidth) * nextItemProgress) + nextPaddings;

        int startX = (getMeasuredWidth() - trueWidth) / 2;
        for (int a = 0; a < count; a++) {
            DrawableReceiver receiver = imagesToDraw.get(a);
            int num = receiver.getParam();
            if (num == currentImage) {
                receiver.setImageX(startX + moveX + currentPaddings / 2);
                receiver.setImageWidth(trueWidth - currentPaddings);
            } else {
                if (nextImage < currentImage) {
                    if (num < currentImage) {
                        if (num <= nextImage) {
                            receiver.setImageX(startX + (receiver.getParam() - currentImage + 1) * (itemWidth + itemSpacing) - (nextTrueWidth + itemSpacing) + moveX);
                        } else {
                            receiver.setImageX(startX + (receiver.getParam() - currentImage) * (itemWidth + itemSpacing) + moveX);
                        }
                    } else {
                        receiver.setImageX(startX + trueWidth + itemSpacing + (receiver.getParam() - currentImage - 1) * (itemWidth + itemSpacing) + moveX);
                    }
                } else {
                    if (num < currentImage) {
                        receiver.setImageX(startX + (receiver.getParam() - currentImage) * (itemWidth + itemSpacing) + moveX);
                    } else {
                        if (num <= nextImage) {
                            receiver.setImageX(startX + trueWidth + itemSpacing + (receiver.getParam() - currentImage - 1) * (itemWidth + itemSpacing) + moveX);
                        } else {
                            receiver.setImageX(startX + trueWidth + itemSpacing + (receiver.getParam() - currentImage - 2) * (itemWidth + itemSpacing) + (nextTrueWidth + itemSpacing) + moveX);
                        }
                    }
                }
                if (num == nextImage) {
                    receiver.setImageWidth(nextTrueWidth - nextPaddings);
                    receiver.setImageX((int) (receiver.getImageX() + nextPaddings / 2));
                } else {
                    receiver.setImageWidth(itemWidth);
                }
            }
            receiver.setAlpha(drawAlpha);
            receiver.setRoundRadius(AndroidUtilities.dp(2));
            receiver.draw(canvas);
        }

        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        if (dt > 17) {
            dt = 17;
        }
        lastUpdateTime = newTime;
        if (animateToItem >= 0) {
            if (moveLineProgress > 0.0f) {
                moveLineProgress -= dt / (animateToItemFast ? 100.0f : 200.0f);
                if (animateToItem == currentImage) {
                    if (currentItemProgress < 1.0f) {
                        currentItemProgress += dt / 200.0f;
                        if (currentItemProgress > 1.0f) {
                            currentItemProgress = 1.0f;
                        }
                    }
                    drawDx = animateToDXStart + (int) Math.ceil(currentItemProgress * (animateToDX - animateToDXStart));
                } else {
                    nextItemProgress = CubicBezierInterpolator.EASE_OUT.getInterpolation(1.0f - moveLineProgress);
                    if (stopedScrolling) {
                        if (currentItemProgress > 0.0f) {
                            currentItemProgress -= dt / 200.0f;
                            if (currentItemProgress < 0.0f) {
                                currentItemProgress = 0.0f;
                            }
                        }
                        drawDx = animateToDXStart + (int) Math.ceil(nextItemProgress * (animateToDX - animateToDXStart));
                    } else {
                        currentItemProgress = CubicBezierInterpolator.EASE_OUT.getInterpolation(moveLineProgress);
                        drawDx = (int) Math.ceil(nextItemProgress * animateToDX);
                    }
                }
                if (moveLineProgress <= 0) {
                    currentImage = animateToItem;
                    moveLineProgress = 1.0f;
                    currentItemProgress = 1.0f;
                    nextItemProgress = 0.0f;
                    moving = false;
                    stopedScrolling = false;
                    drawDx = 0;
                    animateToItem = -1;
                    animateToItemFast = false;
                }
            }
            fillImages(true, drawDx);
            invalidate();
        }
        if (scrolling && currentItemProgress > 0.0f) {
            currentItemProgress -= dt / 200.0f;
            if (currentItemProgress < 0.0f) {
                currentItemProgress = 0.0f;
            }
            invalidate();
        }
        if (!scroll.isFinished()) {
            if (scroll.computeScrollOffset()) {
                drawDx = scroll.getCurrX();
                updateAfterScroll();
                invalidate();
            }
            if (scroll.isFinished()) {
                stopScrolling();
            }
        }
    }

    public void setDelegate(Delegate delegate) { if (!destroyed) this.delegate = delegate; }

    public boolean hasPhotos() {
        return hasPhotos && hideAnimator == null && (drawAlpha > 0f || !animateBackground || (showAnimator != null && showAnimator.isStarted()));
    }

    public boolean isAnimationsEnabled() {
        return animationsEnabled;
    }

    public void setAnimationsEnabled(boolean enabled) {
        if (animationsEnabled == enabled || destroyed) return;
        animationsEnabled = enabled; cancelFades();
        drawAlpha = hasPhotos ? 1f : 0f;
        if (!hasPhotos) finishHiding();
        invalidate();
    }

    public void setAnimateBackground(boolean animateBackground) {
        this.animateBackground = animateBackground;
    }

    public void reset() { clear(); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthSpec), resolveSize(itemY + itemHeight + AndroidUtilities.dp(2), heightSpec));
    }
    @Override protected void onDetachedFromWindow() {
        cancelFades(); abortMotion(); releaseReceivers();
        currentImage = Math.max(0, indexOf(currentPhotos, selectedIndex));
        drawAlpha = hasPhotos ? 1f : 0f;
        if (!hasPhotos) finishHiding();
        super.onDetachedFromWindow();
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!destroyed) fillImages(false, 0);
    }

    /** Local replacement for ImageReceiver's coordinates/crop/alpha only; never owns a host Drawable. */
    private static final class DrawableReceiver {
        private Entry entry;
        private int param;
        private float imageX, imageY, imageWidth, imageHeight, alpha = 1f, radius;
        private final Rect oldBounds = new Rect();
        private final RectF imageRect = new RectF();
        private final Path clip = new Path();
        void setEntry(Entry entry) { this.entry = entry; }
        void setParam(int param) { this.param = param; }
        int getParam() { return param; }
        void setImageCoords(float x, float y, float width, float height) { imageX=x; imageY=y; imageWidth=width; imageHeight=height; }
        void setImageX(float x) { imageX=x; }
        float getImageX() { return imageX; }
        void setImageWidth(int width) { imageWidth=width; }
        void setAlpha(float alpha) { this.alpha=alpha; }
        void setRoundRadius(int radius) { this.radius=radius; }
        boolean isInsideImage(float x, float y) { return x>=imageX && x<=imageX+imageWidth && y>=imageY && y<=imageY+imageHeight; }
        void draw(Canvas canvas) {
            if (entry == null || entry.drawable == null || imageWidth <= 0 || imageHeight <= 0 || alpha <= 0) return;
            Drawable drawable = entry.drawable;
            imageRect.set(imageX,imageY,imageX+imageWidth,imageY+imageHeight);
            int save = canvas.saveLayerAlpha(imageRect, Math.round(255*Math.min(1f,alpha)));
            oldBounds.set(drawable.getBounds());
            try {
                clip.rewind(); clip.addRoundRect(imageRect,radius,radius,Path.Direction.CW); canvas.clipPath(clip);
                float scale = Math.max(imageWidth/entry.width,imageHeight/entry.height);
                float width=entry.width*scale, height=entry.height*scale;
                int left=Math.round(imageX+(imageWidth-width)/2), top=Math.round(imageY+(imageHeight-height)/2);
                drawable.setBounds(left,top,Math.round(left+width),Math.round(top+height)); drawable.draw(canvas);
            } finally { drawable.setBounds(oldBounds); canvas.restoreToCount(save); }
        }
    }
}
