/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScrollerCustom;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.CompoundEmoji;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.EmojiData;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.StickerSetNameCell;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Objects;

/**
 * tgui: the emoji page of upstream EmojiView (Telegram 11.4.2) — the grid of the bundled emoji
 * (recently used + the 8 {@link EmojiData} categories), the {@link EmojiTabsStrip} category tabs
 * with tab ↔ scroll sync, the skin-tone popup ({@link EmojiColorPickerWindow}), the bottom bar
 * with the backspace button (long-press repeats) and recent emoji / skin tones persisted by
 * {@link Emoji}. The sticker and GIF pages, custom emoji packs, premium checks and emoji search
 * need the Telegram account and server and are cut; the methods the input bar calls for them
 * stay as no-ops.
 */
public class EmojiView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public interface EmojiViewDelegate {
        default boolean onBackspace() {
            return false;
        }

        default void onEmojiSelected(String emoji) {
        }

        default void onCustomEmojiSelected(long documentId, TLRPC.Document document, String emoticon, boolean isRecent) {
        }

        default void onTabOpened(int type) {
        }

        default void onClearEmojiRecent() {
        }

        default void onSearchOpenClose(int type) {
        }

        default boolean isSearchOpened() {
            return false;
        }

        default boolean isExpanded() {
            return false;
        }

        default boolean canSchedule() {
            return false;
        }

        default boolean isInScheduleMode() {
            return false;
        }

        default long getDialogId() {
            return 0;
        }

        default int getThreadId() {
            return 0;
        }

        default void invalidateEnterView() {
        }

        default float getProgressToSearchOpened() {
            return 0f;
        }
    }

    public interface DragListener {
        void onDragStart();

        void onDragEnd(float velocity);

        void onDragCancel();

        void onDrag(int offset);
    }

    private final static int TAB_EMOJI = 0;

    private ArrayList<Tab> allTabs = new ArrayList<>();
    private ArrayList<Tab> currentTabs = new ArrayList<>();
    private boolean ignorePagerScroll;
    private ViewPager pager;
    private FrameLayout bottomTabContainer;
    private View bottomTabContainerBackground;
    private ImageView backspaceButton;
    private View shadowLine;
    private AnimatorSet backspaceButtonAnimation;
    private float lastBottomScrollDy;

    private EmojiTabsStrip emojiTabs;
    private FrameLayout emojiContainer;
    private View emojiTabsShadow;
    private EmojiGridView emojiGridView;
    private GridLayoutManager emojiLayoutManager;
    private EmojiGridAdapter emojiAdapter;
    private AnimatorSet emojiTabShadowAnimator;
    private RecyclerAnimationScrollHelper emojiScrollHelper;
    private boolean firstEmojiAttach = true;
    // tgui: emoji search needs the server's keyword lists; the search row and field are cut
    private final boolean needEmojiSearch = false;
    private boolean emojiSmoothScrolling;
    private boolean ignoreStickersScroll;
    private boolean shouldDrawBackground = true;
    public boolean shouldLightenBackground = true;

    EmojiPagesAdapter emojiPagerAdapter;

    private DragListener dragListener;
    private boolean showing;

    private final int[] tabsMinusDy = new int[3];
    private ObjectAnimator[] tabsYAnimators = new ObjectAnimator[3];

    // No @IntDef: AGP strips non-public typedefs from the AAR, but the NestMembers attribute still names them and D8 fails in consumers.
    @Retention(RetentionPolicy.SOURCE)
    private @interface Type {
        int STICKERS = 0;
        int EMOJIS = 1;
        int GIFS = 2;
    }

    private String[] emojiTitles;

    private int searchFieldHeight;

    public int currentAccount = UserConfig.selectedAccount;

    private EmojiViewDelegate delegate;

    boolean emojiBanned;
    boolean stickersBanned;

    private int currentPage;

    private EmojiColorPickerWindow colorPickerView;
    private int emojiSize;
    private int location[] = new int[2];

    private boolean isLayout;
    private int currentBackgroundType = -1;
    private Object outlineProvider;
    private boolean forseMultiwindowLayout;

    private boolean backspacePressed;
    private boolean backspaceOnce;

    private ImageViewEmoji emojiTouchedView;
    private float emojiLastX;
    private float emojiLastY;
    private float emojiTouchedX;
    private float emojiTouchedY;
    private final Theme.ResourcesProvider resourcesProvider;

    public void setAllow(boolean allowStickers, boolean allowGifs, boolean animated) {
        // tgui: only the emoji page exists
        currentTabs.clear();
        for (int i = 0; i < allTabs.size(); i++) {
            if (allTabs.get(i).type == TAB_EMOJI) {
                currentTabs.add(allTabs.get(i));
            }
        }
        if (pager != null) {
            pager.setAdapter(null);
            pager.setAdapter(emojiPagerAdapter);
        }
    }

    public void setShouldDrawBackground(boolean shouldDrawBackground) {
        if (this.shouldDrawBackground != shouldDrawBackground) {
            this.shouldDrawBackground = shouldDrawBackground;
            updateColors();
        }
    }

    private void sendEmoji(ImageViewEmoji imageViewEmoji, String override) {
        if (imageViewEmoji == null) {
            return;
        }
        shownBottomTabAfterClick = SystemClock.elapsedRealtime();
        showBottomTab(true, true);
        String code = override != null ? override : (String) imageViewEmoji.getTag();
        if (override == null) {
            if (!imageViewEmoji.isRecent) {
                String color = Emoji.emojiColor.get(code);
                if (color != null) {
                    code = addColorToCode(code, color);
                }
            }
            addEmojiToRecent(code);
            if (delegate != null) {
                delegate.onEmojiSelected(Emoji.fixEmoji(code));
            }
        } else {
            if (delegate != null) {
                delegate.onEmojiSelected(Emoji.fixEmoji(override));
            }
        }
    }

    public static class ImageViewEmoji extends ImageView {
        public int position;

        public boolean ignoring;
        private boolean isRecent;
        float pressedProgress;
        ValueAnimator backAnimator;

        public ImageViewEmoji(Context context) {
            super(context);
            setScaleType(ImageView.ScaleType.CENTER);
            setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector), AndroidUtilities.dp(2), AndroidUtilities.dp(2)));
        }

        public void setImageDrawable(Drawable drawable, boolean recent) {
            super.setImageDrawable(drawable);
            isRecent = recent;
        }

        @Override
        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.getSize(widthMeasureSpec));
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.view.View");
        }

        @Override
        public void setPressed(boolean pressed) {
            if (isPressed() != pressed) {
                super.setPressed(pressed);
                invalidate();
                if (pressed) {
                    if (backAnimator != null) {
                        backAnimator.removeAllListeners();
                        backAnimator.cancel();
                    }
                }
                if (!pressed && pressedProgress != 0) {
                    backAnimator = ValueAnimator.ofFloat(pressedProgress, 0);
                    backAnimator.addUpdateListener(animation -> {
                        pressedProgress = (float) animation.getAnimatedValue();
                        invalidate();
                    });
                    backAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            backAnimator = null;
                        }
                    });
                    backAnimator.setInterpolator(new OvershootInterpolator(5.0f));
                    backAnimator.setDuration(350);
                    backAnimator.start();
                }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (isPressed() && pressedProgress != 1f) {
                pressedProgress += (float) Math.min(40, 1000f / AndroidUtilities.screenRefreshRate) / 100f;
                pressedProgress = Utilities.clamp(pressedProgress, 1f, 0);
                invalidate();
            }
            float s = 0.8f + 0.2f * (1f - pressedProgress);
            canvas.save();
            canvas.scale(s, s, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
            super.onDraw(canvas);
            canvas.restore();
        }
    }

    public EmojiView(Context context, boolean shouldDrawBackground, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.shouldDrawBackground = shouldDrawBackground;
        this.resourcesProvider = resourcesProvider;

        int color = getThemedColor(Theme.key_chat_emojiBottomPanelIcon);
        color = Color.argb(30, Color.red(color), Color.green(color), Color.blue(color));

        searchFieldHeight = AndroidUtilities.dp(50);

        emojiTitles = new String[]{
                LocaleController.getString(R.string.Emoji1),
                LocaleController.getString(R.string.Emoji2),
                LocaleController.getString(R.string.Emoji3),
                LocaleController.getString(R.string.Emoji4),
                LocaleController.getString(R.string.Emoji5),
                LocaleController.getString(R.string.Emoji6),
                LocaleController.getString(R.string.Emoji7),
                LocaleController.getString(R.string.Emoji8)
        };

        if (Build.VERSION.SDK_INT >= 21) {
            outlineProvider = new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(view.getPaddingLeft(), view.getPaddingTop(), view.getMeasuredWidth() - view.getPaddingRight(), view.getMeasuredHeight() - view.getPaddingBottom(), AndroidUtilities.dp(6));
                }
            };
        }

        emojiContainer = new FrameLayout(context) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == emojiGridView) {
                    canvas.save();
                    float top = emojiTabs.getY() + emojiTabs.getMeasuredHeight() + 1;
                    canvas.clipRect(0, top, getMeasuredWidth(), getMeasuredHeight());
                    boolean rez = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return rez;
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        Tab emojiTab = new Tab();
        emojiTab.type = TAB_EMOJI;
        emojiTab.view = emojiContainer;
        allTabs.add(emojiTab);

        emojiGridView = new EmojiGridView(context);
        DefaultItemAnimator emojiItemAnimator = new DefaultItemAnimator();
        emojiItemAnimator.setAddDelay(0);
        emojiItemAnimator.setAddDuration(220);
        emojiItemAnimator.setMoveDuration(220);
        emojiItemAnimator.setChangeDuration(160);
        emojiItemAnimator.setMoveInterpolator(CubicBezierInterpolator.EASE_OUT);
        emojiGridView.setItemAnimator(emojiItemAnimator);
        emojiGridView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
            @Override
            public boolean onItemClick(View view, int position) {
                if (view instanceof ImageViewEmoji) {
                    ImageViewEmoji viewEmoji = (ImageViewEmoji) view;

                    if (viewEmoji.isRecent) {
                        RecyclerListView.ViewHolder holder = emojiGridView.findContainingViewHolder(view);
                        if (holder != null && holder.getAdapterPosition() <= getRecentEmoji().size()) {
                            delegate.onClearEmojiRecent();
                        }
                        emojiGridView.clearTouchesFor(view);
                        return true;
                    } else {
                        String code = (String) viewEmoji.getTag();
                        if (code == null) {
                            return false;
                        }
                        code = code.replace("🏻", "").replace("🏼", "").replace("🏽", "").replace("🏾", "").replace("🏿", "");

                        String color = null;
                        if (!viewEmoji.isRecent) {
                            color = Emoji.emojiColor.get(code);
                        }
                        String toCheck = code;
                        final boolean isCompound = CompoundEmoji.isCompound(toCheck);
                        if (isCompound || EmojiData.emojiColoredMap.contains(code)) {
                            emojiTouchedView = viewEmoji;
                            emojiTouchedX = emojiLastX;
                            emojiTouchedY = emojiLastY;

                            if (isCompound) {
                                toCheck = addColorToCode(code, color);
                            } else {
                                colorPickerView.setSelection(1 + CompoundEmoji.skinTones.indexOf(color));
                            }
                            colorPickerView.setEmoji(toCheck);

                            int popupWidth = colorPickerView.getPopupWidth();
                            int popupHeight = colorPickerView.getPopupHeight();

                            viewEmoji.getLocationOnScreen(location);
                            int x = 0;
                            if (!colorPickerView.isCompound()) {
                                x = emojiSize * colorPickerView.getSelection() + AndroidUtilities.dp(4 * colorPickerView.getSelection() - (AndroidUtilities.isTablet() ? 5 : 1));
                            }
                            if (location[0] - x < AndroidUtilities.dp(5)) {
                                x += (location[0] - x) - AndroidUtilities.dp(5);
                            } else if (location[0] - x + popupWidth > AndroidUtilities.displaySize.x - AndroidUtilities.dp(5)) {
                                x += (location[0] - x + popupWidth) - (AndroidUtilities.displaySize.x - AndroidUtilities.dp(5));
                            }
                            int xOffset = -x;
                            int yOffset = viewEmoji.getTop() < 0 ? viewEmoji.getTop() : 0;

                            colorPickerView.setupArrow(AndroidUtilities.dp(AndroidUtilities.isTablet() ? 30 : 22) - xOffset + (int) AndroidUtilities.dpf2(0.5f));
                            colorPickerView.setFocusable(true);
                            colorPickerView.showAsDropDown(view, xOffset, -view.getMeasuredHeight() - popupHeight + (view.getMeasuredHeight() - emojiSize) / 2 - yOffset);
                            pager.requestDisallowInterceptTouchEvent(true);
                            emojiGridView.hideSelector(true);
                            emojiGridView.clearTouchesFor(view);
                            return true;
                        }
                    }
                }
                return false;
            }
        });
        emojiGridView.setInstantClick(true);
        emojiGridView.setLayoutManager(emojiLayoutManager = new GridLayoutManager(context, 8) {
            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                try {
                    LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(recyclerView.getContext(), LinearSmoothScrollerCustom.POSITION_TOP) {
                        @Override
                        public void onEnd() {
                            emojiSmoothScrolling = false;
                        }
                    };
                    linearSmoothScroller.setTargetPosition(position);
                    startSmoothScroll(linearSmoothScroller);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
        emojiGridView.setTopGlowOffset(AndroidUtilities.dp(38));
        emojiGridView.setBottomGlowOffset(AndroidUtilities.dp(36));
        emojiGridView.setPadding(AndroidUtilities.dp(5), AndroidUtilities.dp(36), AndroidUtilities.dp(5), AndroidUtilities.dp(44));
        emojiGridView.setGlowColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
        emojiGridView.setItemSelectorColorProvider(p -> 0);
        emojiGridView.setClipToPadding(false);
        emojiLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (needEmojiSearch && position == 0 || emojiAdapter.positionToSection.indexOfKey(position) >= 0) {
                    return emojiLayoutManager.getSpanCount();
                }
                return 1;
            }
        });
        emojiGridView.setAdapter(emojiAdapter = new EmojiGridAdapter());
        emojiGridView.addItemDecoration(new EmojiGridSpacing());
        emojiContainer.addView(emojiGridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emojiScrollHelper = new RecyclerAnimationScrollHelper(emojiGridView, emojiLayoutManager);
        emojiScrollHelper.setAnimationCallback(new RecyclerAnimationScrollHelper.AnimationCallback() {

            @Override
            public void onPreAnimation() {
                emojiSmoothScrolling = true;
            }

            @Override
            public void onEndAnimation() {
                emojiSmoothScrolling = false;
            }

            @Override
            public void ignoreView(View view, boolean ignore) {
                if (view instanceof ImageViewEmoji) {
                    ((ImageViewEmoji) view).ignoring = ignore;
                }
            }
        });
        emojiGridView.setOnScrollListener(new TypedScrollListener(Type.EMOJIS) {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateEmojiTabsPosition();
                super.onScrolled(recyclerView, dx, dy);
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    emojiSmoothScrolling = false;
                }
                super.onScrollStateChanged(recyclerView, newState);
            }
        });

        // tgui: without custom emoji packs the collapsible category pill has nothing to make room
        // for, so recent + the 8 categories fill the width (includeAnimated = false; the strip still
        // uses the animated category icons)
        emojiTabs = new EmojiTabsStrip(context, resourcesProvider, true, true, false, 0, null) {
            @Override
            public void setTranslationY(float translationY) {
                if (getTranslationY() != translationY) {
                    super.setTranslationY(translationY);
                    if (emojiTabsShadow != null) {
                        emojiTabsShadow.setTranslationY(translationY);
                    }
                    emojiContainer.invalidate();
                }
            }

            @Override
            protected boolean onTabClick(int index) {
                if (emojiSmoothScrolling) {
                    return false;
                }
                Integer position = null;
                if (index == 0) {
                    position = needEmojiSearch ? 1 : 0;
                } else {
                    index--;
                }
                if (position == null) {
                    if (index < EmojiData.dataColored.length && emojiAdapter.sectionToPosition.indexOfKey(index) >= 0) {
                        position = emojiAdapter.sectionToPosition.get(index);
                    }
                }
                if (position != null) {
                    emojiGridView.stopScroll();
                    updateEmojiTabsPosition(position);
                    scrollEmojisToPosition(position, 0);
                    checkEmojiTabY(null, 0);
                }
                return true;
            }
        };

        final int emojiTabsColor = getThemedColor(Theme.key_chat_emojiPanelBackground);
        if (Color.alpha(emojiTabsColor) >= 0xFF) {
            emojiTabs.setBackgroundColor(emojiTabsColor);
        }
        emojiTabs.updateEmojiPacks(null);
        emojiContainer.addView(emojiTabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36));

        emojiTabsShadow = new View(context);
        emojiTabsShadow.setAlpha(0.0f);
        emojiTabsShadow.setTag(1);
        emojiTabsShadow.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelShadowLine));
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP | Gravity.LEFT);
        layoutParams.topMargin = AndroidUtilities.dp(36);
        emojiContainer.addView(emojiTabsShadow, layoutParams);

        currentTabs.clear();
        currentTabs.addAll(allTabs);

        pager = new ViewPager(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ignorePagerScroll) {
                    return false;
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1));
                }
                try {
                    return super.onInterceptTouchEvent(ev);
                } catch (IllegalArgumentException ignore) {
                }
                return false;
            }

            @Override
            public void setCurrentItem(int item, boolean smoothScroll) {
                if (item == getCurrentItem()) {
                    if (item == 0) {
                        tabsMinusDy[Type.EMOJIS] = 0;
                        ObjectAnimator animator = ObjectAnimator.ofFloat(emojiTabs, TRANSLATION_Y, 0);
                        animator.setDuration(150);
                        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                        animator.start();
                        scrollEmojisToPosition(1, 0);
                        if (emojiTabs != null) {
                            emojiTabs.select(0);
                        }
                    }
                    return;
                }
                super.setCurrentItem(item, smoothScroll);
            }
        };
        pager.setAdapter(emojiPagerAdapter = new EmojiPagesAdapter());

        backspaceButton = new ImageView(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    backspacePressed = true;
                    backspaceOnce = false;
                    postBackspaceRunnable(350);
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP) {
                    backspacePressed = false;
                    if (!backspaceOnce) {
                        if (delegate != null && delegate.onBackspace()) {
                            backspaceButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        }
                    }
                }
                super.onTouchEvent(event);
                return true;
            }
        };
        backspaceButton.setHapticFeedbackEnabled(true);
        backspaceButton.setImageResource(R.drawable.smiles_tab_clear);
        backspaceButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelBackspace), PorterDuff.Mode.MULTIPLY));
        backspaceButton.setScaleType(ImageView.ScaleType.CENTER);
        backspaceButton.setContentDescription(LocaleController.getString(R.string.AccDescrBackspace));
        backspaceButton.setFocusable(true);
        backspaceButton.setOnClickListener(v -> {

        });

        bottomTabContainer = new FrameLayout(context) {
            @Override
            public void setTranslationY(float translationY) {
                if (getTranslationY() != translationY) {
                    super.setTranslationY(translationY);
                    EmojiView.this.invalidate();
                }
            }
        };
        bottomTabContainer.setClickable(true);

        shadowLine = new View(context);
        shadowLine.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelShadowLine));
        bottomTabContainer.addView(shadowLine, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight()));

        bottomTabContainerBackground = new View(context);
        bottomTabContainer.addView(bottomTabContainerBackground, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtilities.dp(40), Gravity.LEFT | Gravity.BOTTOM));

        // upstream's chat input layout (needSearch): a bottom bar with the backspace on the right;
        // the GIF / sticker type tabs and the search button are cut with those pages
        addView(bottomTabContainer, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtilities.dp(40) + AndroidUtilities.getShadowHeight(), Gravity.LEFT | Gravity.BOTTOM));
        bottomTabContainer.addView(backspaceButton, LayoutHelper.createFrame(47, 40, Gravity.BOTTOM | Gravity.RIGHT));
        if (Build.VERSION.SDK_INT >= 21) {
            backspaceButton.setBackground(Theme.createSelectorDrawable(color, Theme.RIPPLE_MASK_CIRCLE_20DP, AndroidUtilities.dp(18)));
        }

        addView(pager, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        emojiSize = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 40 : 32);
        colorPickerView = EmojiColorPickerWindow.create(context, resourcesProvider);
        colorPickerView.setOnSelectionUpdateListener((skinTone1, skinTone2) -> {
            if (emojiTouchedView != null && emojiTouchedView.getDrawable() instanceof CompoundEmoji.CompoundEmojiDrawable) {
                CompoundEmoji.CompoundEmojiDrawable drawable = (CompoundEmoji.CompoundEmojiDrawable) emojiTouchedView.getDrawable();
                drawable.update(skinTone1, skinTone2);

                String code = (String) emojiTouchedView.getTag();
                if (skinTone1 == -1 && skinTone2 == -1) {
                    Emoji.emojiColor.remove(code);
                } else {
                    String colored = (skinTone1 >= 0 ? CompoundEmoji.skinTones.get(skinTone1) : "") + "‍" + (skinTone2 >= 0 ? CompoundEmoji.skinTones.get(skinTone2) : "");
                    Emoji.emojiColor.put(code, colored);
                }
                Emoji.saveEmojiColors();
            }
        });
        // tgui: the sticker and GIF pages are cut, so the panel always opens on emoji
        currentPage = 0;

        Emoji.loadRecentEmoji();
        emojiAdapter.notifyDataSetChanged();

        setAllow(false, false, false);
        updateColors();
    }

    class EmojiGridView extends RecyclerListView {
        public EmojiGridView(Context context) {
            super(context);
        }

        private boolean ignoreLayout;

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            ignoreLayout = true;
            int width = MeasureSpec.getSize(widthSpec);
            int wasSpanCount = emojiLayoutManager.getSpanCount();
            emojiLayoutManager.setSpanCount(Math.max(1, width / AndroidUtilities.dp(AndroidUtilities.isTablet() ? 60 : 45)));
            ignoreLayout = false;
            super.onMeasure(widthSpec, heightSpec);
            if (wasSpanCount != emojiLayoutManager.getSpanCount()) {
                emojiAdapter.notifyDataSetChanged();
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            if (needEmojiSearch && firstEmojiAttach) {
                ignoreLayout = true;
                emojiLayoutManager.scrollToPositionWithOffset(0, 0);
                firstEmojiAttach = false;
                ignoreLayout = false;
            }
            super.onLayout(changed, l, t, r, b);
            checkEmojiSearchFieldScroll(true);
        }

        @Override
        public void requestLayout() {
            if (ignoreLayout) {
                return;
            }
            super.requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (emojiTouchedView != null && colorPickerView != null) {
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (colorPickerView != null && colorPickerView.isShowing() && !colorPickerView.isCompound()) {
                        colorPickerView.dismiss();

                        String color = colorPickerView.getSkinTone(0);
                        String code = (String) emojiTouchedView.getTag();
                        if (!emojiTouchedView.isRecent) {
                            if (color != null) {
                                Emoji.emojiColor.put(code, color);
                                code = addColorToCode(code, color);
                            } else {
                                Emoji.emojiColor.remove(code);
                            }
                            emojiTouchedView.setImageDrawable(Emoji.getEmojiBigDrawable(code), emojiTouchedView.isRecent);
                            sendEmoji(emojiTouchedView, null);
                            try {
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                            } catch (Exception ignore) {}

                            Emoji.saveEmojiColors();
                        } else {
                            code = code.replace("🏻", "")
                                    .replace("🏼", "")
                                    .replace("🏽", "")
                                    .replace("🏾", "")
                                    .replace("🏿", "");
                            if (color != null) {
                                sendEmoji(emojiTouchedView, addColorToCode(code, color));
                            } else {
                                sendEmoji(emojiTouchedView, code);
                            }
                        }
                    }
                    if (colorPickerView == null || !colorPickerView.isCompound()) {
                        emojiTouchedView = null;
                    }
                    emojiTouchedX = -10000;
                    emojiTouchedY = -10000;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    boolean ignore = false;
                    if (emojiTouchedX != -10000) {
                        if (Math.abs(emojiTouchedX - event.getX()) > AndroidUtilities.getPixelsInCM(0.2f, true) || Math.abs(emojiTouchedY - event.getY()) > AndroidUtilities.getPixelsInCM(0.2f, false)) {
                            emojiTouchedX = -10000;
                            emojiTouchedY = -10000;
                        } else {
                            ignore = true;
                        }
                    }
                    if (!ignore) {
                        getLocationOnScreen(location);
                        float x = location[0] + event.getX();
                        colorPickerView.pickerView.getLocationOnScreen(location);
                        x -= location[0] + dp(3);
                        colorPickerView.onTouchMove((int) x);
                    }
                }
                if (colorPickerView == null || !colorPickerView.isCompound() || colorPickerView.isShowing()) {
                    return true;
                }
            }
            emojiLastX = event.getX();
            emojiLastY = event.getY();
            return super.onTouchEvent(event);
        }

        @Override
        public void onScrollStateChanged(int state) {
            super.onScrollStateChanged(state);
            if (state == SCROLL_STATE_IDLE) {
                if (!canScrollVertically(-1) || !canScrollVertically(1)) {
                    showBottomTab(true, true);
                }
                if (!canScrollVertically(1)) {
                    checkTabsY(Type.EMOJIS, AndroidUtilities.dp(36));
                }
            }
        }

        private SparseArray<TouchDownInfo> touches;

        class TouchDownInfo {
            float x, y;
            long time;
            View view;
        }

        public void clearTouchesFor(View view) {
            if (touches != null) {
                for (int i = 0; i < touches.size(); i++) {
                    TouchDownInfo touch = touches.valueAt(i);
                    if (touch.view == view) {
                        touches.removeAt(i);
                        i--;
                        if (touch != null) {
                            if (touch.view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && touch.view.getBackground() instanceof RippleDrawable) {
                                touch.view.getBackground().setState(new int[]{});
                            }
                            if (touch.view != null) {
                                touch.view.setPressed(false);
                            }
                        }
                    }
                }
            }
        }

        public void clearAllTouches() {
            if (touches != null) {
                for (int i = 0; i < touches.size(); i++) {
                    TouchDownInfo touch = touches.valueAt(i);
                    touches.removeAt(i);
                    i--;
                    if (touch != null) {
                        if (touch.view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && touch.view.getBackground() instanceof RippleDrawable) {
                            touch.view.getBackground().setState(new int[]{});
                        }
                        if (touch.view != null) {
                            touch.view.setPressed(false);
                        }
                    }
                }
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            final boolean down = ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN || ev.getActionMasked() == MotionEvent.ACTION_DOWN;
            final boolean up = ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP || ev.getActionMasked() == MotionEvent.ACTION_UP;
            final boolean cancel = ev.getActionMasked() == MotionEvent.ACTION_CANCEL;
            if (down || up || cancel) {
                int index = ev.getActionIndex();
                int id = ev.getPointerId(index);
                if (touches == null) {
                    touches = new SparseArray<>();
                }

                float x = ev.getX(index), y = ev.getY(index);
                View touchChild = findChildViewUnder(x, y);

                TouchDownInfo touch;
                if (down) {
                    if (touchChild != null) {
                        touch = new TouchDownInfo();
                        touch.x = x;
                        touch.y = y;
                        touch.time = SystemClock.elapsedRealtime();
                        touch.view = touchChild;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && touchChild.getBackground() instanceof RippleDrawable) {
                            touchChild.getBackground().setState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled});
                        }
                        touch.view.setPressed(true);
                        touches.put(id, touch);
                        stopScroll();
                    }
                } else {
                    touch = touches.get(id);
                    touches.remove(id);
                    if (
                        touchChild != null && touch != null &&
                        Math.sqrt(Math.pow(x - touch.x, 2) + Math.pow(y - touch.y, 2)) < AndroidUtilities.touchSlop * 3 &&
                        !cancel &&
                        (!colorPickerView.isShowing() || SystemClock.elapsedRealtime() - touch.time < ViewConfiguration.getLongPressTimeout())
                    ) {
                        View view = touch.view;
                        if (view instanceof ImageViewEmoji) {
                            ImageViewEmoji viewEmoji = (ImageViewEmoji) view;
                            sendEmoji(viewEmoji, null);
                            try {
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                            } catch (Exception ignore) {}
                        } else if (view != null) {
                            view.callOnClick();
                        }
                    }
                    if (touch != null && touch.view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && touch.view.getBackground() instanceof RippleDrawable) {
                        touch.view.getBackground().setState(new int[]{});
                    }
                    if (touch != null && touch.view != null) {
                        touch.view.setPressed(false);
                    }
                }
            }
            return super.dispatchTouchEvent(ev) || !cancel && touches.size() > 0;
        }
    }

    private class TypedScrollListener extends RecyclerListView.OnScrollListener {

        @Type
        private final int type;

        private boolean smoothScrolling;

        public TypedScrollListener(@Type int type) {
            this.type = type;
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (recyclerView.getLayoutManager().isSmoothScrolling()) {
                smoothScrolling = true;
                return;
            }
            if (newState == RecyclerListView.SCROLL_STATE_IDLE) {
                if (!smoothScrolling) {
                    animateTabsY(type);
                }
                if (ignoreStickersScroll) {
                    ignoreStickersScroll = false;
                }
                smoothScrolling = false;
            } else {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    if (ignoreStickersScroll) {
                        ignoreStickersScroll = false;
                    }
                    smoothScrolling = false;
                }
                if (!smoothScrolling) {
                    stopAnimatingTabsY(type);
                }
            }
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            checkTabsY(type, dy);
            checkEmojiSearchFieldScroll(false);
            if (!smoothScrolling) {
                checkBottomTabScroll(dy);
            }
        }
    }

    private void updateEmojiTabsPosition() {
        updateEmojiTabsPosition(emojiLayoutManager.findFirstCompletelyVisibleItemPosition());
    }

    private void updateEmojiTabsPosition(int position) {
        if (!emojiSmoothScrolling && position != RecyclerView.NO_POSITION) {
            int tab = 0;
            int count = getRecentEmoji().size() + (needEmojiSearch ? 1 : 0);
            if (position >= count) {
                tab = -1;
                for (int a = 0; a < EmojiData.dataColored.length; a++) {
                    int size = EmojiData.dataColored[a].length + 1;
                    if (position < count + size) {
                        tab = a + 1;
                        break;
                    }
                    count += size;
                }
            }
            if (tab >= 0) {
                emojiTabs.select(tab);
            }
        }
    }

    public class EmojiGridSpacing extends RecyclerView.ItemDecoration {
        public EmojiGridSpacing() {}

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            if (view instanceof StickerSetNameCell) {
                outRect.left = AndroidUtilities.dp(5);
                outRect.right = AndroidUtilities.dp(5);
            }
        }
    }

    public static String addColorToCode(String code, String color) {
        if (CompoundEmoji.isHandshake(code) != null) {
            return CompoundEmoji.applyColor(code, color);
        } else {
            String end = null;
            boolean invert = false;
            if (Emoji.endsWithRightArrow(code)) {
                code = code.substring(0, code.length() - 2);
                invert = true;
            }
            int length = code.length();
            if (length > 2 && code.charAt(code.length() - 2) == '‍') {
                end = code.substring(code.length() - 2);
                code = code.substring(0, code.length() - 2);
            } else if (length > 3 && code.charAt(code.length() - 3) == '‍') {
                end = code.substring(code.length() - 3);
                code = code.substring(0, code.length() - 3);
            }
            code += color;
            if (end != null) {
                code += end;
            }
            if (invert) {
                code += "‍➡";
            }
            return code;
        }
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        updateBottomTabContainerPosition();
    }

    public boolean fixBottomTabContainerTranslation = true;

    private void updateBottomTabContainerPosition() {
        View parent = (View) getParent();
        if (parent != null) {
            float y = getY();
            if (getLayoutParams().height > 0) {
                y += getLayoutParams().height;
            } else {
                y += getMeasuredHeight();
            }
            if (!AndroidUtilities.isInMultiwindow) {
                y -= parent.getHeight();
            } else {
                y -= AndroidUtilities.dp(1);
            }
            if (bottomTabContainer.getTop() - y < 0 || !fixBottomTabContainerTranslation) {
                y = 0;
            }
            bottomTabMainTranslation = -y;
            bottomTabContainer.setTranslationY(bottomTabMainTranslation + bottomTabAdditionalTranslation);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        updateBottomTabContainerPosition();
        super.dispatchDraw(canvas);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == pager && bottomTabContainer.getVisibility() != View.GONE) {
            canvas.save();
            canvas.clipRect(0, 0, getMeasuredWidth(), bottomTabContainer.getY() - 1);
            if (!shouldDrawBackground && shouldLightenBackground) {
                canvas.drawColor(ColorUtils.setAlphaComponent(Color.WHITE, 25));
            }
            boolean res = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return res;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private ArrayList<String> lastRecentArray;
    private int lastRecentCount;

    public ArrayList<String> getRecentEmoji() {
        // upstream: custom emoji ("animated_<id>") are shown only with animated emoji enabled
        if (lastRecentArray == null) {
            lastRecentArray = new ArrayList<>();
        }
        if (Emoji.recentEmoji.size() != lastRecentCount) {
            lastRecentArray.clear();
            for (int i = 0; i < Emoji.recentEmoji.size(); ++i) {
                if (!Emoji.recentEmoji.get(i).startsWith("animated_")) {
                    lastRecentArray.add(Emoji.recentEmoji.get(i));
                }
            }
            lastRecentCount = lastRecentArray.size();
        }
        return lastRecentArray;
    }

    public void addEmojiToRecent(String code) {
        if (code == null || !Emoji.isValidEmoji(code)) {
            return;
        }
        Emoji.addRecentEmoji(code);
        if (getVisibility() != VISIBLE || pager.getCurrentItem() != 0) {
            Emoji.sortEmoji();
            emojiAdapter.notifyDataSetChanged();
        }
        Emoji.saveRecentEmoji();

        if (lastRecentArray == null) {
            lastRecentArray = new ArrayList<>();
        } else {
            lastRecentArray.clear();
        }
        for (int i = 0; i < Emoji.recentEmoji.size(); ++i) {
            if (!Emoji.recentEmoji.get(i).startsWith("animated_")) {
                lastRecentArray.add(Emoji.recentEmoji.get(i));
            }
        }
        lastRecentCount = lastRecentArray.size();
    }

    /** tgui: emoji search is cut; kept for the input bar. */
    public void showSearchField(boolean show) {
    }

    /** tgui: emoji search is cut; kept for the input bar. */
    public void hideSearchKeyboard() {
    }

    /** tgui: emoji search is cut. */
    public static boolean isSearchSupported() {
        return false;
    }

    private void showEmojiShadow(boolean show, boolean animated) {
        if (show && emojiTabsShadow.getTag() == null || !show && emojiTabsShadow.getTag() != null) {
            return;
        }
        if (emojiTabShadowAnimator != null) {
            emojiTabShadowAnimator.cancel();
            emojiTabShadowAnimator = null;
        }
        emojiTabsShadow.setTag(show ? null : 1);
        if (animated) {
            emojiTabShadowAnimator = new AnimatorSet();
            emojiTabShadowAnimator.playTogether(ObjectAnimator.ofFloat(emojiTabsShadow, View.ALPHA, show ? 1.0f : 0.0f));
            emojiTabShadowAnimator.setDuration(200);
            emojiTabShadowAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            emojiTabShadowAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    emojiTabShadowAnimator = null;
                }
            });
            emojiTabShadowAnimator.start();
        } else {
            emojiTabsShadow.setAlpha(show ? 1.0f : 0.0f);
        }
    }

    public void closeSearch(boolean animated) {
        closeSearch(animated, -1);
    }

    private void scrollEmojisToPosition(int p, int offset) {
        View view = emojiLayoutManager.findViewByPosition(p);
        int firstPosition = emojiLayoutManager.findFirstVisibleItemPosition();
        if ((view == null && Math.abs(p - firstPosition) > emojiLayoutManager.getSpanCount() * 9f) || !SharedConfig.animationsEnabled()) {
            emojiScrollHelper.setScrollDirection(emojiLayoutManager.findFirstVisibleItemPosition() < p ? RecyclerAnimationScrollHelper.SCROLL_DIRECTION_DOWN : RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
            emojiScrollHelper.scrollToPosition(p, offset, false, true);
        } else {
            ignoreStickersScroll = true;
            LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(emojiGridView.getContext(), LinearSmoothScrollerCustom.POSITION_TOP) {
                @Override
                public void onEnd() {
                    emojiSmoothScrolling = false;
                }

                @Override
                protected void onStart() {
                    emojiSmoothScrolling = true;
                }
            };
            linearSmoothScroller.setTargetPosition(p);
            linearSmoothScroller.setOffset(offset);
            emojiLayoutManager.startSmoothScroll(linearSmoothScroller);
        }
    }

    /** tgui: emoji search is cut; kept for the input bar. */
    public void closeSearch(boolean animated, long scrollToSet) {
        if (!animated && delegate != null) {
            delegate.onSearchOpenClose(0);
        }
    }

    private void checkBottomTabScroll(float dy) {
        if (SystemClock.elapsedRealtime() - shownBottomTabAfterClick < ViewConfiguration.getTapTimeout()) {
            return;
        }
        lastBottomScrollDy += dy;
        int offset = AndroidUtilities.dp(38);
        if (lastBottomScrollDy >= offset) {
            showBottomTab(false, true);
        } else if (lastBottomScrollDy <= -offset) {
            showBottomTab(true, true);
        } else if (bottomTabContainer.getTag() == null && lastBottomScrollDy < 0 || bottomTabContainer.getTag() != null && lastBottomScrollDy > 0) {
            lastBottomScrollDy = 0;
        }
    }

    private void showBackspaceButton(boolean show, boolean animated) {
        if (show && backspaceButton.getTag() == null || !show && backspaceButton.getTag() != null) {
            return;
        }
        if (backspaceButtonAnimation != null) {
            backspaceButtonAnimation.cancel();
            backspaceButtonAnimation = null;
        }
        backspaceButton.setTag(show ? null : 1);
        if (animated) {
            if (show) {
                backspaceButton.setVisibility(VISIBLE);
            }
            backspaceButtonAnimation = new AnimatorSet();
            backspaceButtonAnimation.playTogether(ObjectAnimator.ofFloat(backspaceButton, View.ALPHA, show ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(backspaceButton, View.SCALE_X, show ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(backspaceButton, View.SCALE_Y, show ? 1.0f : 0.0f));
            backspaceButtonAnimation.setDuration(200);
            backspaceButtonAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            backspaceButtonAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!show) {
                        backspaceButton.setVisibility(INVISIBLE);
                    }
                }
            });
            backspaceButtonAnimation.start();
        } else {
            backspaceButton.setAlpha(show ? 1.0f : 0.0f);
            backspaceButton.setScaleX(show ? 1.0f : 0.0f);
            backspaceButton.setScaleY(show ? 1.0f : 0.0f);
            backspaceButton.setVisibility(show ? VISIBLE : INVISIBLE);
        }
    }

    private ValueAnimator bottomTabContainerAnimator;
    private float bottomTabMainTranslation, bottomTabAdditionalTranslation;
    private long shownBottomTabAfterClick;

    private void showBottomTab(boolean show, boolean animated) {
        lastBottomScrollDy = 0;
        if (delegate != null && delegate.isSearchOpened()) {
            show = false;
        }
        if (show && bottomTabContainer.getTag() == null || !show && bottomTabContainer.getTag() != null) {
            return;
        }
        if (bottomTabContainerAnimator != null) {
            bottomTabContainerAnimator.cancel();
            bottomTabContainerAnimator = null;
        }
        bottomTabContainer.setTag(show ? null : 1);
        if (animated) {
            bottomTabContainerAnimator = ValueAnimator.ofFloat(bottomTabAdditionalTranslation, show ? 0 : AndroidUtilities.dp(45));
            bottomTabContainerAnimator.addUpdateListener(anm -> {
                bottomTabAdditionalTranslation = (float) anm.getAnimatedValue();
                updateBottomTabContainerPosition();
            });
            bottomTabContainerAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (bottomTabContainerAnimator != animation) {
                        return;
                    }
                    bottomTabAdditionalTranslation = (float) bottomTabContainerAnimator.getAnimatedValue();
                    updateBottomTabContainerPosition();
                    bottomTabContainerAnimator = null;
                }
            });
            bottomTabContainerAnimator.setDuration(380);
            bottomTabContainerAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            bottomTabContainerAnimator.start();
        } else {
            bottomTabAdditionalTranslation = show ? 0 : AndroidUtilities.dp(45);
            updateBottomTabContainerPosition();
        }
    }

    private void checkTabsY(@Type int type, int dy) {
        if (type == Type.EMOJIS) {
            checkEmojiTabY(emojiGridView, dy);
        }
    }

    private void resetTabsY(@Type int type) {
        if (delegate != null && delegate.isSearchOpened() || type != Type.EMOJIS) {
            return;
        }
        emojiTabs.setTranslationY(tabsMinusDy[type] = 0);
    }

    private void animateTabsY(@Type int type) {
        if ((delegate != null && delegate.isSearchOpened()) || type != Type.EMOJIS) {
            return;
        }
        final float tabsHeight = AndroidUtilities.dpf2(36);
        final float fraction = tabsMinusDy[type] / -tabsHeight;
        if (fraction <= 0f || fraction >= 1f) {
            return;
        }
        final View tabStrip = emojiTabs;
        final int endValue = fraction > 0.5f ? (int) -Math.ceil(tabsHeight) : 0;
        checkEmojiShadow(endValue);
        if (tabsYAnimators[type] == null) {
            tabsYAnimators[type] = ObjectAnimator.ofFloat(tabStrip, View.TRANSLATION_Y, tabStrip.getTranslationY(), endValue);
            tabsYAnimators[type].addUpdateListener(a -> tabsMinusDy[type] = (int) (float) a.getAnimatedValue());
            tabsYAnimators[type].setDuration(200);
        } else {
            tabsYAnimators[type].setFloatValues(tabStrip.getTranslationY(), endValue);
        }
        tabsYAnimators[type].start();
    }

    private void stopAnimatingTabsY(@Type int type) {
        if (tabsYAnimators[type] != null && tabsYAnimators[type].isRunning()) {
            tabsYAnimators[type].cancel();
        }
    }

    public void scrollEmojiToTop() {
        emojiGridView.stopScroll();
        emojiTabs.scrollTo(0, 0);
        resetTabsY(Type.EMOJIS);
        emojiLayoutManager.scrollToPositionWithOffset(0, 0);
    }

    private void checkEmojiSearchFieldScroll(boolean isLayout) {
        // tgui: without the search field only the tab shadow follows the scroll
        checkEmojiShadow(Math.round(emojiTabs.getTranslationY()));
    }

    private void checkEmojiShadow(int tabsTranslationY) {
        if (tabsYAnimators[Type.EMOJIS] != null && tabsYAnimators[Type.EMOJIS].isRunning()) {
            return;
        }
        final RecyclerView.ViewHolder holder = emojiGridView.findViewHolderForAdapterPosition(0);
        final int translatedBottom = AndroidUtilities.dp(38) + tabsTranslationY;
        showEmojiShadow(translatedBottom > 0 && (holder == null || holder.itemView.getBottom() < translatedBottom), !isLayout);
    }

    private void checkEmojiTabY(View list, int dy) {
        if (list == null) {
            emojiTabs.setTranslationY(tabsMinusDy[Type.EMOJIS] = 0);
            return;
        }
        if (list.getVisibility() != VISIBLE || emojiSmoothScrolling) {
            return;
        }
        if (delegate != null && delegate.isSearchOpened()) {
            return;
        }
        if (dy > 0 && emojiGridView != null && emojiGridView.getVisibility() == VISIBLE) {
            RecyclerView.ViewHolder holder = emojiGridView.findViewHolderForAdapterPosition(0);
            if (holder != null && holder.itemView.getTop() + (needEmojiSearch ? searchFieldHeight : 0) >= emojiGridView.getPaddingTop()) {
                return;
            }
        }
        tabsMinusDy[Type.EMOJIS] -= dy;
        if (tabsMinusDy[Type.EMOJIS] > 0) {
            tabsMinusDy[Type.EMOJIS] = 0;
        } else if (tabsMinusDy[Type.EMOJIS] < -AndroidUtilities.dp(36 * 3)) {
            tabsMinusDy[Type.EMOJIS] = -AndroidUtilities.dp(36 * 3);
        }
        emojiTabs.setTranslationY(Math.max(-AndroidUtilities.dp(36), tabsMinusDy[Type.EMOJIS]));
    }

    public void clearRecentEmoji() {
        Emoji.clearRecentEmoji();
        emojiAdapter.notifyDataSetChanged();
    }

    private void onPageScrolled(int position, int width, int positionOffsetPixels) {
        if (delegate == null) {
            return;
        }
        delegate.onTabOpened(0);
    }

    private void postBackspaceRunnable(final int time) {
        AndroidUtilities.runOnUIThread(() -> {
            if (!backspacePressed) {
                return;
            }
            if (delegate != null && delegate.onBackspace()) {
                backspaceButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            backspaceOnce = true;
            postBackspaceRunnable(Math.max(50, time - 100));
        }, time);
    }

    /** tgui: the GIF page is cut; kept for the input bar. */
    public void switchToGifRecent() {
    }

    @Override
    public void requestLayout() {
        if (isLayout) {
            return;
        }
        super.requestLayout();
    }

    public void updateColors() {
        if (!shouldDrawBackground) {
            setBackground(null);
            bottomTabContainerBackground.setBackground(null);
        } else if (AndroidUtilities.isInMultiwindow || forseMultiwindowLayout) {
            Drawable background = getBackground();
            if (background != null) {
                background.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelBackground), PorterDuff.Mode.MULTIPLY));
            }
        } else {
            setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
            bottomTabContainerBackground.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
        }
        if (emojiTabs != null) {
            if (shouldDrawBackground) {
                emojiTabs.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
                emojiTabsShadow.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelShadowLine));
            } else {
                emojiTabs.setBackground(null);
            }
        }
        if (colorPickerView != null) {
            colorPickerView.updateColors();
        }
        if (emojiGridView != null) {
            emojiGridView.setGlowColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
            for (int i = 0; i < emojiGridView.getChildCount(); i++) {
                View child = emojiGridView.getChildAt(i);
                if (child instanceof StickerSetNameCell) {
                    // upstream: StickerSetNameCell.createThemeDescriptions (textView colour by field name)
                    ((StickerSetNameCell) child).setTitleColor(getThemedColor(Theme.key_chat_emojiPanelStickerSetName));
                    ((StickerSetNameCell) child).updateColors();
                }
            }
        }
        if (backspaceButton != null) {
            backspaceButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelBackspace), PorterDuff.Mode.MULTIPLY));
            int color = getThemedColor(Theme.key_chat_emojiBottomPanelIcon);
            Theme.setSelectorDrawableColor(backspaceButton.getBackground(), Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)), true);
        }
        if (shadowLine != null) {
            shadowLine.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelShadowLine));
        }
        if (emojiTabs != null) {
            emojiTabs.updateColors();
        }
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        isLayout = true;
        if (AndroidUtilities.isInMultiwindow || forseMultiwindowLayout) {
            if (currentBackgroundType != 1) {
                if (Build.VERSION.SDK_INT >= 21) {
                    setOutlineProvider((ViewOutlineProvider) outlineProvider);
                    setClipToOutline(true);
                    setElevation(AndroidUtilities.dp(2));
                }
                setBackgroundResource(R.drawable.smiles_popup);
                getBackground().setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelBackground), PorterDuff.Mode.MULTIPLY));
                if (shouldDrawBackground) {
                    bottomTabContainerBackground.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
                }
                currentBackgroundType = 1;
            }
        } else {
            if (currentBackgroundType != 0) {
                if (Build.VERSION.SDK_INT >= 21) {
                    setOutlineProvider(null);
                    setClipToOutline(false);
                    setElevation(0);
                }
                if (shouldDrawBackground) {
                    setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
                    bottomTabContainerBackground.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
                }
                currentBackgroundType = 0;
            }
        }
        super.onMeasure(View.MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
        isLayout = false;
        setTranslationY(getTranslationY());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateBottomTabContainerPosition();
    }

    public void setDelegate(EmojiViewDelegate emojiViewDelegate) {
        delegate = emojiViewDelegate;
    }

    public EmojiViewDelegate getDelegate() {
        return delegate;
    }

    public void setDragListener(DragListener listener) {
        dragListener = listener;
    }

    public void invalidateViews() {
        emojiGridView.invalidateViews();
    }

    public void setForseMultiwindowLayout(boolean value) {
        forseMultiwindowLayout = value;
    }

    public void onOpen(boolean forceEmoji, boolean groupEmojiHintWasVisible) {
        showBackspaceButton(true, false);
        if (pager.getCurrentItem() != 0) {
            pager.setCurrentItem(0, !forceEmoji);
        }
        showBottomTab(true, true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public void setVisibility(int visibility) {
        boolean changed = getVisibility() != visibility;
        super.setVisibility(visibility);
        if (changed) {
            if (visibility != GONE) {
                Emoji.sortEmoji();
                emojiAdapter.notifyDataSetChanged();
            }
        }
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void onDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (colorPickerView != null && colorPickerView.isShowing()) {
            colorPickerView.dismiss();
        }
    }

    /** tgui: there are no stickers; kept for the input bar. */
    public void setStickersBanned(boolean emojiBanned, boolean stickersBanned, long chatId) {
        this.emojiBanned = emojiBanned;
        this.stickersBanned = stickersBanned;
    }

    /** tgui: there are no stickers; kept for the input bar. */
    public boolean areThereAnyStickers() {
        return false;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (emojiGridView != null) {
                emojiGridView.invalidate();
                int count = emojiGridView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = emojiGridView.getChildAt(a);
                    if (child instanceof ImageViewEmoji) {
                        child.invalidate();
                    }
                }
            }
            if (colorPickerView != null) {
                colorPickerView.pickerView.invalidate();
            }
        }
    }

    private int getThemedColor(int key) {
        if (resourcesProvider != null) {
            return resourcesProvider.getColor(key);
        }
        return Theme.getColor(key);
    }

    private class EmojiGridAdapter extends RecyclerListView.SelectionAdapter {

        private static final int VIEW_TYPE_EMOJI = 0;
        private static final int VIEW_TYPE_HEADER = 1;
        private static final int VIEW_TYPE_SEARCH = 2;

        private ArrayList<Integer> rowHashCodes = new ArrayList<>();
        private SparseIntArray positionToSection = new SparseIntArray();
        private SparseIntArray sectionToPosition = new SparseIntArray();
        private int itemCount;

        @Override
        public int getItemCount() {
            return itemCount;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == VIEW_TYPE_EMOJI;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_EMOJI:
                    view = new ImageViewEmoji(getContext());
                    break;
                case VIEW_TYPE_HEADER:
                    view = new StickerSetNameCell(getContext(), true, resourcesProvider);
                    break;
                case VIEW_TYPE_SEARCH:
                default:
                    view = new View(getContext());
                    view.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, searchFieldHeight));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, @SuppressLint("RecyclerView") int position) {
            int index;
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_EMOJI: {
                    ImageViewEmoji imageView = (ImageViewEmoji) holder.itemView;
                    imageView.position = position;

                    String code;
                    String coloredCode;
                    boolean recent;
                    if (needEmojiSearch) {
                        position--;
                    }

                    int count = getRecentEmoji().size();
                    if (position < count) {
                        coloredCode = code = getRecentEmoji().get(position);
                        recent = true;
                    } else {
                        code = null;
                        coloredCode = null;
                        for (int a = 0; a < EmojiData.dataColored.length; a++) {
                            int size = EmojiData.dataColored[a].length + 1;
                            if (position - count - 1 >= 0 && position < count + size) {
                                coloredCode = code = EmojiData.dataColored[a][position - count - 1];
                                String color = Emoji.emojiColor.get(code);
                                if (color != null) {
                                    coloredCode = addColorToCode(coloredCode, color);
                                }
                                break;
                            }
                            count += size;
                        }
                        recent = false;
                    }
                    imageView.setPadding(0, 0, 0, 0);
                    imageView.setImageDrawable(Emoji.getEmojiBigDrawable(coloredCode), recent);
                    imageView.setTag(code);
                    imageView.setContentDescription(coloredCode);
                    break;
                }
                case VIEW_TYPE_HEADER: {
                    StickerSetNameCell cell = (StickerSetNameCell) holder.itemView;
                    cell.position = position;
                    index = positionToSection.get(position);
                    cell.setText(emojiTitles[index], 0);
                    cell.setTitleColor(getThemedColor(Theme.key_chat_emojiPanelStickerSetName));
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (positionToSection.indexOfKey(position) >= 0) {
                return VIEW_TYPE_HEADER;
            } else if (needEmojiSearch && position == 0) {
                return VIEW_TYPE_SEARCH;
            }
            return VIEW_TYPE_EMOJI;
        }

        public void updateRows() {
            positionToSection.clear();
            sectionToPosition.clear();
            rowHashCodes.clear();
            itemCount = 0;
            if (needEmojiSearch) {
                itemCount++;
                rowHashCodes.add(-1);
            }
            ArrayList<String> recent = getRecentEmoji();
            if (emojiTabs != null) {
                emojiTabs.showRecent(!recent.isEmpty());
            }
            itemCount += recent.size();
            for (int i = 0; i < recent.size(); ++i) {
                rowHashCodes.add(Objects.hash(-43263, recent.get(i)));
            }
            int k = 0;
            for (int a = 0; a < EmojiData.dataColored.length; ++a, ++k) {
                positionToSection.put(itemCount, k);
                sectionToPosition.put(k, itemCount);
                itemCount += EmojiData.dataColored[a].length + 1;
                rowHashCodes.add(Objects.hash(43245, a));
                for (int i = 0; i < EmojiData.dataColored[a].length; ++i) {
                    rowHashCodes.add(EmojiData.dataColored[a][i].hashCode());
                }
            }
        }

        @Override
        public void notifyDataSetChanged() {
            if (frozen) {
                return;
            }
            ArrayList<Integer> prevRowHashCodes = new ArrayList<>(rowHashCodes);

            updateRows();

            DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return prevRowHashCodes.size();
                }

                @Override
                public int getNewListSize() {
                    return rowHashCodes.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return prevRowHashCodes.get(oldItemPosition).equals(rowHashCodes.get(newItemPosition));
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return true;
                }
            }, false).dispatchUpdatesTo(this);
        }
    }

    private class EmojiPagesAdapter extends PagerAdapter {

        public void destroyItem(ViewGroup viewGroup, int position, Object object) {
            viewGroup.removeView((View) object);
        }

        public int getCount() {
            return currentTabs.size();
        }

        public CharSequence getPageTitle(int position) {
            return LocaleController.getString(R.string.Emoji);
        }

        public Object instantiateItem(ViewGroup viewGroup, int position) {
            View view = currentTabs.get(position).view;
            viewGroup.addView(view);
            return view;
        }

        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
    }

    /** tgui: emoji search is cut; kept for the input bar. */
    public void searchProgressChanged() {
    }

    /** tgui: there is no sticker tab strip to expand. */
    public float getStickersExpandOffset() {
        return 0;
    }

    public void setShowing(boolean showing) {
        this.showing = showing;
    }

    public boolean isShowing() {
        return showing;
    }

    public void onMessageSend() {
    }

    private class Tab {
        int type;
        View view;
    }

    private boolean frozen;

    public void freeze(boolean freeze) {
        final boolean wasFrozen = frozen;
        this.frozen = freeze;
        if (wasFrozen && !freeze) {
            if (emojiAdapter != null) {
                emojiAdapter.notifyDataSetChanged();
            }
        }
    }
}
