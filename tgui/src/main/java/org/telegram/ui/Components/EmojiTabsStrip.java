package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * tgui: upstream emoji category strip — recent, the collapsible pill of animated (Lottie) category
 * icons and the selection indicator. Custom emoji pack tabs, premium locks and the reaction /
 * avatar / topic picker variants are cut, since they need the account's sticker sets.
 */
public class EmojiTabsStrip extends ScrollableHorizontalScrollView {

    private int recentDrawableId = R.drawable.msg_emoji_recent;
    private static int[] emojiTabsDrawableIds = {
            R.drawable.msg_emoji_smiles,
            R.drawable.msg_emoji_cat,
            R.drawable.msg_emoji_food,
            R.drawable.msg_emoji_activities,
            R.drawable.msg_emoji_travel,
            R.drawable.msg_emoji_objects,
            R.drawable.msg_emoji_other,
            R.drawable.msg_emoji_flags
    };
    private static int[] emojiTabsAnimatedDrawableIds = {
            R.raw.msg_emoji_smiles,
            R.raw.msg_emoji_cat,
            R.raw.msg_emoji_food,
            R.raw.msg_emoji_activities,
            R.raw.msg_emoji_travel,
            R.raw.msg_emoji_objects,
            R.raw.msg_emoji_other,
            R.raw.msg_emoji_flags
    };
    private int settingsDrawableId = R.drawable.smiles_tab_settings;

    // upstream: !isPremium(); without premium emoji packs the category pill always stays open
    private boolean forceTabsShow = true;
    private boolean showSelected = true;
    private AnimatedFloat showSelectedAlpha;

    private Theme.ResourcesProvider resourcesProvider;
    private boolean includeAnimated;

    public EmojiTabButton toggleEmojiStickersTab;
    public EmojiTabButton recentTab;
    private EmojiTabButton settingsTab;
    private EmojiTabsView emojiTabs;
    private HashMap<View, Rect> removingViews = new HashMap<>();

    private int packsIndexStart;

    private ValueAnimator selectAnimator;
    private float selectT = 0f;
    private float selectAnimationT = 0f;
    private int selected = 0;
    private int selectedFullIndex = 0;
    private int wasIndex = 0;

    public boolean animateAppear = true;

    private final int accentColor;
    private Runnable onSettingsOpenRunnable;
    private boolean wasDrawn;
    private int currentType;
    public boolean updateButtonDrawables = true;

    public EmojiTabsStrip(Context context, Theme.ResourcesProvider resourcesProvider, boolean includeRecent, boolean includeStandard, boolean includeAnimated, int type, Runnable onSettingsOpen) {
        this(context, resourcesProvider, includeRecent, includeStandard, includeAnimated, type, onSettingsOpen, Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider));
    }

    public EmojiTabsStrip(Context context, Theme.ResourcesProvider resourcesProvider, boolean includeRecent, boolean includeStandard, boolean includeAnimated, int type, Runnable onSettingsOpen, int accentColor) {
        super(context);
        this.includeAnimated = includeAnimated;
        this.resourcesProvider = resourcesProvider;
        this.onSettingsOpenRunnable = onSettingsOpen;
        this.currentType = type;
        this.accentColor = accentColor;

        contentView = new LinearLayout(context) {

            private final LongSparseArray<Integer> lastX = new LongSparseArray<>();

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                int cy = (b - t) / 2;
                if (includeAnimated) {
                    int x = getPaddingLeft() - (!recentIsShown ? AndroidUtilities.dp(30 + 3) : 0);
                    for (int i = 0; i < getChildCount(); ++i) {
                        View child = getChildAt(i);
                        if (child == settingsTab || removingViews.containsKey(child)) {
                            continue;
                        }
                        if (child != null) {
                            child.layout(x, cy - child.getMeasuredHeight() / 2, x + child.getMeasuredWidth(), cy + child.getMeasuredHeight() / 2);
                            Long id = child instanceof EmojiTabButton ? ((EmojiTabButton) child).id() : (child instanceof EmojiTabsView ? (Long) ((EmojiTabsView) child).id : null);
                            if (animateAppear && child instanceof EmojiTabButton && ((EmojiTabButton) child).newly) {
                                ((EmojiTabButton) child).newly = false;
                                child.setScaleX(0);
                                child.setScaleY(0);
                                child.setAlpha(0);
                                child.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                            }
                            if (id != null) {
                                Integer lx = lastX.get(id);
                                if (lx != null && lx != x && Math.abs(lx - x) < AndroidUtilities.dp(45)) {
                                    child.setTranslationX(lx - x);
                                    child.animate().translationX(0).setDuration(250).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                                }
                                lastX.put(id, x);
                            }
                            x += child.getMeasuredWidth() + AndroidUtilities.dp(3);
                        }
                    }
                    if (settingsTab != null) {
                        x += (!recentIsShown ? AndroidUtilities.dp(30 + 3) : 0);
                        Long id = settingsTab.id;
                        if (x + settingsTab.getMeasuredWidth() + getPaddingRight() <= EmojiTabsStrip.this.getMeasuredWidth()) {
                            settingsTab.layout(x = (r - l - getPaddingRight() - settingsTab.getMeasuredWidth()), cy - settingsTab.getMeasuredHeight() / 2, r - l - getPaddingRight(), cy + settingsTab.getMeasuredHeight() / 2);
                        } else {
                            settingsTab.layout(x, cy - settingsTab.getMeasuredHeight() / 2, x + settingsTab.getMeasuredWidth(), cy + settingsTab.getMeasuredHeight() / 2);
                        }
                        if (id != null) {
                            if (lastX.get(id) != null && lastX.get(id) != x) {
                                settingsTab.setTranslationX(lastX.get(id) - x);
                                settingsTab.animate().translationX(0).setDuration(350).start();
                            }
                            lastX.put(id, x);
                        }
                    }
                } else {
                    final int childCount = getChildCount() - (!recentIsShown ? 1 : 0);
                    int margin = (int) ((r - l - getPaddingLeft() - getPaddingRight() - childCount * AndroidUtilities.dp(30)) / (float) Math.max(1, childCount - 1));
                    int x = getPaddingLeft();
                    for (int i = 0; i < childCount; ++i) {
                        View child = getChildAt((!recentIsShown ? 1 : 0) + i);
                        if (child != null) {
                            child.layout(x, cy - child.getMeasuredHeight() / 2, x + child.getMeasuredWidth(), cy + child.getMeasuredHeight() / 2);
                            x += child.getMeasuredWidth() + margin;
                        }
                    }
                }
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int AT_MOST = MeasureSpec.makeMeasureSpec(99999999, MeasureSpec.AT_MOST);
                int width = getPaddingLeft() + getPaddingRight() - (int) (recentIsShown ? 0 : recentTab.getAlpha() * AndroidUtilities.dp(30 + 3));
                for (int i = 0; i < getChildCount(); ++i) {
                    View child = getChildAt(i);
                    if (child != null) {
                        child.measure(AT_MOST, heightMeasureSpec);
                        width += child.getMeasuredWidth() + (i + 1 < getChildCount() ? AndroidUtilities.dp(3) : 0);
                    }
                }
                if (!includeAnimated) {
                    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
                    return;
                }
                setMeasuredDimension(Math.max(width, MeasureSpec.getSize(widthMeasureSpec)), MeasureSpec.getSize(heightMeasureSpec));
            }

            private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private RectF from = new RectF();
            private RectF to = new RectF();
            private RectF rect = new RectF();
            private Path path = new Path();

            @Override
            protected void dispatchDraw(Canvas canvas) {
                for (Map.Entry<View, Rect> entry : removingViews.entrySet()) {
                    View view = entry.getKey();
                    if (view != null) {
                        Rect bounds = entry.getValue();
                        canvas.save();
                        canvas.translate(bounds.left, bounds.top);
                        canvas.scale(view.getScaleX(), view.getScaleY(), bounds.width() / 2f, bounds.height() / 2f);
                        view.draw(canvas);
                        canvas.restore();
                    }
                }

                if (showSelectedAlpha == null) {
                    showSelectedAlpha = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
                }
                float alpha = showSelectedAlpha.set(showSelected ? 1 : 0);

                int selectFrom = (int) Math.floor(selectT), selectTo = (int) Math.ceil(selectT);
                getChildBounds(selectFrom, from);
                getChildBounds(selectTo, to);
                AndroidUtilities.lerp(from, to, selectT - selectFrom, rect);
                float isEmojiTabs = emojiTabs == null ? 0 : MathUtils.clamp(1f - Math.abs(selectT - 1), 0, 1);
                float isMiddle = 4f * selectAnimationT * (1f - selectAnimationT);
                float hw = rect.width() / 2 * (1f + isMiddle * .3f);
                float hh = rect.height() / 2 * (1f - isMiddle * .05f);
                rect.set(rect.centerX() - hw, rect.centerY() - hh, rect.centerX() + hw, rect.centerY() + hh);
                float r = AndroidUtilities.dp(AndroidUtilities.lerp(8f, 16f, isEmojiTabs));
                paint.setColor(selectorColor());
                if (forceTabsShow) {
                    paint.setAlpha((int) (paint.getAlpha() * alpha * (1f - isEmojiTabs * .5f)));
                } else {
                    paint.setAlpha((int) (paint.getAlpha() * alpha));
                }

                path.rewind();
                path.addRoundRect(rect, r, r, Path.Direction.CW);
                canvas.drawPath(path, paint);

                if (forceTabsShow) {
                    path.rewind();
                    getChildBounds(1, rect);
                    path.addRoundRect(rect, AndroidUtilities.dpf2(16), AndroidUtilities.dpf2(16), Path.Direction.CW);
                    paint.setColor(selectorColor());
                    paint.setAlpha((int) (paint.getAlpha() * .5f));
                    canvas.drawPath(path, paint);
                }

                if (emojiTabs != null) {
                    path.addCircle(emojiTabs.getLeft() + AndroidUtilities.dp(15), (emojiTabs.getTop() + emojiTabs.getBottom()) / 2f, AndroidUtilities.dp(15), Path.Direction.CW);
                }

                super.dispatchDraw(canvas);
                wasDrawn = true;
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == emojiTabs) {
                    canvas.save();
                    canvas.clipPath(path);
                    boolean res = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return res;
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            private void getChildBounds(int i, RectF out) {
                View child = getChildAt(MathUtils.clamp(i, 0, getChildCount() - 1));
                if (child == null) {
                    return;
                }
                out.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
                out.set(
                        out.centerX() - out.width() / 2f * child.getScaleX(),
                        out.centerY() - out.height() / 2f * child.getScaleY(),
                        out.centerX() + out.width() / 2f * child.getScaleX(),
                        out.centerY() + out.height() / 2f * child.getScaleY()
                );
//                out.offset(recentIsShown ? recentTab.getTranslationX() : AndroidUtilities.dp(30 + 3) - recentTab.getTranslationX(), 0);
            }
        };
        contentView.setClipToPadding(false);
        contentView.setOrientation(LinearLayout.HORIZONTAL);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        addView(contentView);

        if (includeRecent) {
            contentView.addView(recentTab = new EmojiTabButton(context, recentDrawableId, false, false));
            recentTab.id = (long) "recent".hashCode();
        }
        if (!includeAnimated) {
            for (int i = 0; i < emojiTabsDrawableIds.length; ++i) {
                contentView.addView(new EmojiTabButton(context, emojiTabsDrawableIds[i], false, i == 0));
            }
            updateClickListeners();
        } else {
            if (includeStandard) {
                contentView.addView(emojiTabs = new EmojiTabsView(context));
                emojiTabs.id = (long) "tabs".hashCode();
            }
            packsIndexStart = contentView.getChildCount();
            if (onSettingsOpen != null) {
                contentView.addView(settingsTab = new EmojiTabButton(context, settingsDrawableId, false, true));
                settingsTab.id = (long) "settings".hashCode();
                settingsTab.setAlpha(0);
            }
            updateClickListeners();
        }
    }

    public void showRecentTabStub(boolean show) {
        if (recentTab == null) {
            return;
        }
        if (show) {
            recentTab.setBackground(new StabDrawable(selectorColor()));
        } else {
            recentTab.setBackground(null);
        }
    }

    public void showSelected(boolean show) {
        this.showSelected = show;
        this.contentView.invalidate();
    }

    private boolean recentFirstChange = true;
    private boolean recentIsShown = true;

    public void showRecent(boolean show) {
        if (recentIsShown == show) {
            return;
        }
        recentIsShown = show;
        if (recentFirstChange) {
            recentTab.setAlpha(show ? 1f : 0f);
        } else {
            recentTab.animate().alpha(show ? 1f : 0f).setDuration(200).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        }
        if (!show && selected == 0 || show && selected == 1) {
            select(0, !recentFirstChange);
        }
        contentView.requestLayout();
        recentFirstChange = false;
    }

    protected boolean doIncludeFeatured() {
        return true;
    }

    private static class StabDrawable extends Drawable {
        private final Paint paint = new Paint();
        private final RectF rectF = new RectF();

        public StabDrawable(int color){
            paint.setAlpha(45);
            paint.setColor(color);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            rectF.set(0, 0, AndroidUtilities.dp(30), AndroidUtilities.dp(30));
            canvas.drawRoundRect(rectF, AndroidUtilities.dpf2(8), AndroidUtilities.dpf2(8), paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    boolean first = true;

    /**
     * tgui: upstream adds a tab per custom emoji pack here; the packs are cut, so this only ends
     * the initial (non-animated) state and shows the settings tab, as upstream does once packs load.
     */
    public void updateEmojiPacks(ArrayList<?> emojiPacks) {
        if (!includeAnimated) {
            return;
        }
        first = false;
        if (settingsTab != null) {
            settingsTab.bringToFront();
            if (settingsTab.getAlpha() < 1) {
                settingsTab.animate().alpha(1f).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }
        }
        updateClickListeners();
    }

    public void updateClickListeners() {
        for (int i = 0, j = 0; i < contentView.getChildCount(); ++i, ++j) {
            View child = contentView.getChildAt(i);
            if (child instanceof EmojiTabsView) {
                EmojiTabsView tabsView = (EmojiTabsView) child;
                for (int a = 0; a < tabsView.contentView.getChildCount(); ++a, ++j) {
                    final int index = j;
                    tabsView.contentView.getChildAt(a).setOnClickListener(e -> {
                        onTabClick(index);
                    });
                }
                --j;
            } else if (child != null) {
                final int index = j;
                child.setOnClickListener(e -> {
                    onTabClick(index);
                });
            }
        }
        if (settingsTab != null) {
            settingsTab.setOnClickListener(e -> {
                if (onSettingsOpenRunnable != null) {
                    onSettingsOpenRunnable.run();
                }
            });
        }
    }

    protected boolean onTabClick(int index) {
        return true;
    }

    private float paddingLeftDp = 5 + 6;

    public void setPaddingLeft(float paddingLeftDp) {
        this.paddingLeftDp = paddingLeftDp;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        contentView.setPadding(AndroidUtilities.dp(paddingLeftDp), 0, AndroidUtilities.dp(5 + 6), 0);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void updateColors() {
        if (recentTab != null) {
            recentTab.updateColor();
        }
    }

    public void select(int index) {
        select(index, true);
    }

    public void select(int index, boolean animated) {
        animated = animated && !first;
        if (toggleEmojiStickersTab != null) {
            index++;
        }
        if (!recentIsShown || toggleEmojiStickersTab != null) {
            index = Math.max(1, index);
        }
        selectedFullIndex = index;
        final int wasSelected = selected;
        for (int i = 0, j = 0; i < contentView.getChildCount(); ++i, ++j) {
            View child = contentView.getChildAt(i);
            int from = j;
            if (child instanceof EmojiTabsView) {
                EmojiTabsView tabs = (EmojiTabsView) child;
                for (int a = 0; a < tabs.contentView.getChildCount(); ++a, ++j) {
                    View child2 = tabs.contentView.getChildAt(a);
                    if (child2 instanceof EmojiTabButton) {
                        ((EmojiTabButton) child2).updateSelect(index == j, animated);
                    }
                }
                --j;
            } else if (child instanceof EmojiTabButton) {
                ((EmojiTabButton) child).updateSelect(index == j, animated);
            }
            if (index >= from && index <= j) {
                selected = i;
            }
        }
        if (wasSelected != selected) {
            if (selectAnimator != null) {
                selectAnimator.cancel();
            }
            final float from = selectT, to = selected;
            if (animated) {
                selectAnimator = ValueAnimator.ofFloat(0, 1);
                selectAnimator.addUpdateListener(a -> {
                    selectAnimationT = (float) a.getAnimatedValue();
                    selectT = AndroidUtilities.lerp(from, to, selectAnimationT);
                    contentView.invalidate();
                });
                selectAnimator.setDuration(350);
                selectAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                selectAnimator.start();
            } else {
                selectAnimationT = 1f;
                selectT = AndroidUtilities.lerp(from, to, selectAnimationT);
                contentView.invalidate();
            }

            if (emojiTabs != null) {
                emojiTabs.show(selected == 1 || forceTabsShow, animated);
            }

            View child = contentView.getChildAt(selected);
            if (selected >= 2) {
                scrollToVisible(child.getLeft(), child.getRight());
            } else {
                scrollTo(0);
            }
        }

        if (wasIndex != index) {
            if (emojiTabs != null && selected == 1 && index >= 1 && index <= 1 + emojiTabs.contentView.getChildCount()) {
                emojiTabs.scrollToVisible(AndroidUtilities.dp(36 * (index - 1) - 6), AndroidUtilities.dp(36 * (index - 1) - 6 + 30));
            }
            wasIndex = index;
        }
    }

    private int selectorColor() {
        return Theme.multAlpha(Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider), .18f);
    }

    public class EmojiTabButton extends ViewGroup {
        public boolean shown = true;

        public Long id;
        public boolean newly;

        // tgui: upstream uses BackupImageView (ImageReceiver) to show these resource icons
        private ImageView imageView;
        private RLottieDrawable lottieDrawable;
        private boolean round, forceSelector;

        public Long id() {
            return id;
        }

        public EmojiTabButton(Context context, int drawableId, int lottieId, boolean roundSelector, boolean forceSelector) {
            super(context);
            this.round = roundSelector;
            this.forceSelector = forceSelector;
            if (round) {
                setBackground(Theme.createCircleSelectorDrawable(selectorColor(), 0, 0));
            } else if (forceSelector) {
                setBackground(Theme.createRadSelectorDrawable(selectorColor(), 8, 8));
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                lottieDrawable = new RLottieDrawable(lottieId, "" + lottieId, AndroidUtilities.dp(24), AndroidUtilities.dp(24), false, null);
                lottieDrawable.setBounds(AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(27), AndroidUtilities.dp(27));
                lottieDrawable.setMasterParent(this);
                lottieDrawable.setAllowDecodeSingleFrame(true);
                lottieDrawable.start();
            } else {
                imageView = new ImageView(context);
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imageView.setImageDrawable(context.getResources().getDrawable(drawableId).mutate());
                addView(imageView);
            }
            setColor(Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider));
        }

        public EmojiTabButton(Context context, int drawableId, boolean roundSelector, boolean forceSelector) {
            super(context);
            this.round = roundSelector;
            this.forceSelector = forceSelector;
            if (round) {
                setBackground(Theme.createCircleSelectorDrawable(selectorColor(), 0, 0));
            } else if (forceSelector) {
                setBackground(Theme.createRadSelectorDrawable(selectorColor(), 8, 8));
            }

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setImageDrawable(context.getResources().getDrawable(drawableId).mutate());
            setColor(Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider));

            addView(imageView);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (lottieDrawable != null && isVisible) {
                lottieDrawable.draw(canvas);
            }
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (!isVisible) {
                return true;
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!isVisible) {
                return;
            }
            super.onDraw(canvas);
        }

        public void setDrawable(Drawable drawable) {
            imageView.setImageDrawable(drawable);
        }

        private boolean isVisible;

        public void updateVisibilityInbounds(boolean visible, boolean ignore) {
            if (!isVisible && visible) {
                if (lottieDrawable != null && !lottieDrawable.isRunning() && !ignore) {
                    lottieDrawable.setProgress(0);
                    lottieDrawable.start();
                }
            }
            if (isVisible != visible) {
                isVisible = visible;
                if (visible) {
                    invalidate();
                    if (imageView != null) {
                        imageView.invalidate();
                    }
                }
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(AndroidUtilities.dp(30), AndroidUtilities.dp(30));
            if (imageView != null) {
                imageView.measure(
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY)
                );
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            if (imageView != null) {
                int cx = (r - l) / 2, cy = (b - t) / 2;
                imageView.layout(cx - imageView.getMeasuredWidth() / 2, cy - imageView.getMeasuredHeight() / 2, cx + imageView.getMeasuredWidth() / 2, cy + imageView.getMeasuredHeight() / 2);
            }
        }

        public Drawable getDrawable() {
            return imageView != null ? imageView.getDrawable() : null;
        }

        private float selectT;
        private boolean selected;
        private ValueAnimator selectAnimator;

        public void updateSelect(boolean selected, boolean animated) {
            if (imageView != null && imageView.getDrawable() == null) {
                return;
            }
            if (this.selected == selected) {
                return;
            }
            this.selected = selected;
            if (selectAnimator != null) {
                selectAnimator.cancel();
                selectAnimator = null;
            }

            if (animated) {
                selectAnimator = ValueAnimator.ofFloat(selectT, selected ? 1f : 0f);
                selectAnimator.addUpdateListener(a -> {
                    selectT = (float) a.getAnimatedValue();
                    setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider), Theme.getColor(Theme.key_chat_emojiPanelIconSelected, resourcesProvider), selectT));
                });
                selectAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (updateButtonDrawables && !round) {
                            if (selected || forceSelector) {
                                if (getBackground() == null) {
                                    setBackground(Theme.createRadSelectorDrawable(selectorColor(), 8, 8));
                                }
                            } else {
                                setBackground(null);
                            }
                        }
                    }
                });
                selectAnimator.setDuration(350);
                selectAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                selectAnimator.start();
            } else {
                selectT = selected ? 1f : 0f;
                updateColor();
            }
        }

        public void updateColor() {
            Theme.setSelectorDrawableColor(getBackground(), selectorColor(), false);
            setColor(
                ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider),
                    Theme.getColor(Theme.key_chat_emojiPanelIconSelected, resourcesProvider),
                    selectT
                )
            );
        }

        private void setColor(int color) {
            PorterDuffColorFilter colorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
            if (imageView != null) {
                imageView.setColorFilter(colorFilter);
                imageView.invalidate();
            }
            if (lottieDrawable != null) {
                lottieDrawable.setColorFilter(colorFilter);
                invalidate();
            }
        }
    }

    private class EmojiTabsView extends ScrollableHorizontalScrollView {
        public long id;

        public EmojiTabsView(Context context) {
            super(context);
            setSmoothScrollingEnabled(true);
            setHorizontalScrollBarEnabled(false);
            setVerticalScrollBarEnabled(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setNestedScrollingEnabled(true);
            }
            contentView = new LinearLayout(context) {
                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    int x = getPaddingLeft(), cy = (b - t) / 2;
                    for (int i = 0; i < getChildCount(); ++i) {
                        View child = getChildAt(i);
                        if (child == settingsTab) {
                            continue;
                        }
                        if (child != null) {
                            child.layout(x, cy - child.getMeasuredHeight() / 2, x + child.getMeasuredWidth(), cy + child.getMeasuredHeight() / 2);
                            x += child.getMeasuredWidth() + AndroidUtilities.dp(2);
                        }
                    }
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(
                            Math.max(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp((30 + 2) * contentView.getChildCount()), MeasureSpec.EXACTLY)),
                            heightMeasureSpec
                    );
                }
            };
            contentView.setOrientation(LinearLayout.HORIZONTAL);
            addView(contentView, new LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

            for (int i = 0; i < emojiTabsDrawableIds.length; ++i) {
                contentView.addView(new EmojiTabButton(context, emojiTabsDrawableIds[i], emojiTabsAnimatedDrawableIds[i], true, false) {
                    @Override
                    public boolean onTouchEvent(MotionEvent ev) {
                        intercept(ev);
                        return super.onTouchEvent(ev);
                    }
                });
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.lerp(AndroidUtilities.dp(30), maxWidth(), showT), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(30), MeasureSpec.EXACTLY)
            );
        }

        public int maxWidth() {
//            return AndroidUtilities.dp((30 + 2) * (clip() ? Math.min(5.7f, contentView.getChildCount()) : contentView.getChildCount()));
            return AndroidUtilities.dp((30 + 2) * Math.min(5.7f, contentView.getChildCount()));
        }

        private void intercept(MotionEvent ev) {
            if (shown && !scrollingAnimation) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_UP:
                        touching = false;
                        break;
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        touching = true;
                        if (!scrollingAnimation) {
                            resetScrollTo();
                        }
                        EmojiTabsStrip.this.requestDisallowInterceptTouchEvent(true);
                        break;
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            intercept(ev);
            return super.onTouchEvent(ev);
        }

        private boolean shown = forceTabsShow;
        private float showT = forceTabsShow ? 1f : 0f;

        public void show(boolean show, boolean animated) {
            if (show == shown) {
                return;
            }
            shown = show;
            if (!show) {
                scrollTo(0);
            }

            if (showAnimator != null) {
                showAnimator.cancel();
            }
            if (animated) {
                showAnimator = ValueAnimator.ofFloat(showT, show ? 1f : 0f);
                showAnimator.addUpdateListener(a -> {
                    showT = (float) a.getAnimatedValue();
                    invalidate();
                    requestLayout();
                    updateButtonsVisibility();
                    EmojiTabsStrip.this.contentView.invalidate();
                });
                showAnimator.setDuration(475);
                showAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                showAnimator.start();
            } else {
                showT = show ? 1f : 0f;
                invalidate();
                requestLayout();
                updateButtonsVisibility();
                EmojiTabsStrip.this.contentView.invalidate();
            }
        }
    }
}

class ScrollableHorizontalScrollView extends HorizontalScrollView {

    boolean touching;
    public LinearLayout contentView;
    ValueAnimator showAnimator;

    public ScrollableHorizontalScrollView(Context context) {
        super(context);
    }

    protected boolean scrollingAnimation;

    public boolean isScrolling() {
        return scrollingAnimation;
    }

    public boolean scrollToVisible(int left, int right) {
        if (getChildCount() <= 0) {
            return false;
        }
        final int padding = AndroidUtilities.dp(50);
        int to;
        if (left < getScrollX() + padding) {
            to = left - padding;
        } else if (right > getScrollX() + (getMeasuredWidth() - padding)) {
            to = right - getMeasuredWidth() + padding;
        } else {
            return false;
        }

        scrollTo(MathUtils.clamp(to, 0, getChildAt(0).getMeasuredWidth() - getMeasuredWidth()));
        return true;
    }

    private int scrollingTo = -1;
    private ValueAnimator scrollAnimator;

    public void scrollTo(int x) {
        if (scrollingTo == x) {
            return;
        }
        scrollingTo = x;
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
        }
        if (this.getScrollX() == x) {
            return;
        }
        scrollAnimator = ValueAnimator.ofFloat(this.getScrollX(), x);
        scrollAnimator.addUpdateListener(a -> this.setScrollX((int) (float) a.getAnimatedValue()));
        scrollAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        scrollAnimator.setDuration(250);
        scrollAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                scrollingAnimation = false;
            }

            @Override
            public void onAnimationStart(Animator animation) {
                scrollingAnimation = true;
                if (getParent() instanceof HorizontalScrollView) {
                    ((HorizontalScrollView) getParent()).requestDisallowInterceptTouchEvent(false);
                }
            }
        });
        scrollAnimator.start();
    }

    public void resetScrollTo() {
        scrollingTo = -1;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateButtonsVisibility();
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (Math.abs(t - oldt) < 2 || t >= getMeasuredHeight() || t == 0) {
            if (!touching) {
                requestDisallowInterceptTouchEvent(false);
            }
        }
        updateButtonsVisibility();
    }

    void updateButtonsVisibility() {
        final int count = contentView.getChildCount();
        for (int i = 0; i < count; ++i) {
            View child = contentView.getChildAt(i);
            if (child instanceof EmojiTabsStrip.EmojiTabButton) {
                ((EmojiTabsStrip.EmojiTabButton) child).updateVisibilityInbounds(child.getRight() - getScrollX() > 0 && child.getLeft() - getScrollX() < getMeasuredWidth(), scrollingAnimation && !(showAnimator != null && showAnimator.isRunning()));
            }
        }
    }

    private boolean touch;

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            touch = true;
        } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            touch = false;
        }
        return super.onTouchEvent(ev);
    }

    public boolean isTouch() {
        return touch;
    }
}
