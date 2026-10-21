package org.telegram.ui.Components;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;
import org.telegram.tgui.recyclerview.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;

/**
 * tgui: the chat-list compose (pencil) button, transplanted from upstream 11.4.2 DialogsActivity:
 * {@code floatingButtonContainer} (56dp circle from {@code createSimpleSelectorCircleDrawable},
 * translationZ 2→4dp state animator, oval outline, pre-21 {@code floating_shadow}) holding the
 * {@code write_contacts_fab_icon} Lottie, {@code hideFloatingButton} (100dp slide, 300 ms
 * AccelerateDecelerate) and the hide-on-scroll rule from the list's {@code onScrolled}: hide while
 * scrolling down, show again when the user drags up.
 * <pre>
 * DialogsFloatingButton fab = new DialogsFloatingButton(context);
 * frameLayout.addView(fab, DialogsFloatingButton.createLayoutParams());
 * fab.attachToList(listView);
 * fab.setOnClickListener(v -> ...);
 * </pre>
 */
public class DialogsFloatingButton extends FrameLayout {

    private final RLottieImageView floatingButton;
    private boolean floatingHidden;
    private float floatingButtonHideProgress;
    private float floatingButtonTranslation;
    private float additionalTranslation;
    private boolean floatingForceVisible;
    private final AccelerateDecelerateInterpolator floatingInterpolator = new AccelerateDecelerateInterpolator();

    public DialogsFloatingButton(Context context) {
        super(context);
        floatingButton = new RLottieImageView(context);
        floatingButton.setScaleType(ImageView.ScaleType.CENTER);
        floatingButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        floatingButton.setAnimation(R.raw.write_contacts_fab_icon, 52, 52);
        setContentDescription(LocaleController.getString(R.string.NewMessageTitle));
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(this, View.TRANSLATION_Z, dp(2), dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(this, View.TRANSLATION_Z, dp(4), dp(2)).setDuration(200));
            setStateListAnimator(animator);
            setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, dp(56), dp(56));
                }
            });
        }
        addView(floatingButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        updateColors();
    }

    /** Upstream placement: 56dp (60 before API 21), bottom end, 14dp margins. */
    public static FrameLayout.LayoutParams createLayoutParams() {
        return LayoutHelper.createFrame((Build.VERSION.SDK_INT >= 21 ? 56 : 60), (Build.VERSION.SDK_INT >= 21 ? 56 : 60), (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 14);
    }

    public RLottieImageView getImageView() {
        return floatingButton;
    }

    /** Replaces the pencil, e.g. with {@code R.drawable.floating_check} as upstream does in select mode. */
    public void setIcon(int resId) {
        floatingButton.setImageResource(resId);
    }

    /** Upstream updateFloatingButtonColor. */
    public void updateColors() {
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = ContextCompat.getDrawable(getContext(), R.drawable.floating_shadow).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(dp(56), dp(56));
            drawable = combinedDrawable;
        }
        setBackground(drawable);
        floatingButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
    }

    public boolean isHidden() {
        return floatingHidden;
    }

    /** Keeps the button on screen regardless of scrolling. */
    public void setForceVisible(boolean value) {
        floatingForceVisible = value;
        if (value) {
            hide(false);
        }
    }

    /** Extra upward offset, e.g. for an undo bar or the keyboard (upstream additionalFloatingTranslation). */
    public void setAdditionalTranslation(float value) {
        additionalTranslation = value;
        updateOffset();
    }

    /** Hides / shows at once (upstream onSearchCollapse: floatingHidden = true, translation dp(100)). */
    public void hide(boolean hide, boolean animated) {
        if (animated) {
            hide(hide);
            return;
        }
        floatingHidden = hide;
        floatingButtonHideProgress = hide ? 1f : 0f;
        floatingButtonTranslation = dp(100) * floatingButtonHideProgress;
        setClickable(!hide);
        updateOffset();
    }

    /** Upstream hideFloatingButton. */
    public void hide(boolean hide) {
        if (floatingHidden == hide || hide && floatingForceVisible) {
            return;
        }
        floatingHidden = hide;
        AnimatorSet animatorSet = new AnimatorSet();
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(floatingButtonHideProgress, floatingHidden ? 1f : 0f);
        valueAnimator.addUpdateListener(animation -> {
            floatingButtonHideProgress = (float) animation.getAnimatedValue();
            floatingButtonTranslation = dp(100) * floatingButtonHideProgress;
            updateOffset();
        });
        animatorSet.playTogether(valueAnimator);
        animatorSet.setDuration(300);
        animatorSet.setInterpolator(floatingInterpolator);
        setClickable(!hide);
        animatorSet.start();
    }

    private void updateOffset() {
        setTranslationY(floatingButtonTranslation - additionalTranslation * (1f - floatingButtonHideProgress));
    }

    /** Upstream DialogsActivity list onScrollStateChanged / onScrolled rule for the button. */
    public void attachToList(RecyclerView listView) {
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private boolean wasManualScroll;
            private boolean scrollingManually;
            private boolean scrollUpdated;
            private int prevPosition;
            private int prevTop;

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    wasManualScroll = true;
                    scrollingManually = true;
                } else {
                    scrollingManually = false;
                }
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    wasManualScroll = false;
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (!wasManualScroll || getVisibility() == GONE || recyclerView.getChildCount() <= 0) {
                    return;
                }
                RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
                if (!(lm instanceof org.telegram.tgui.recyclerview.LinearLayoutManager)) {
                    return;
                }
                int firstVisiblePosition = ((org.telegram.tgui.recyclerview.LinearLayoutManager) lm).findFirstVisibleItemPosition();
                if (firstVisiblePosition == RecyclerView.NO_POSITION) {
                    return;
                }
                RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(firstVisiblePosition);
                int firstViewTop = 0;
                if (holder != null) {
                    firstViewTop = holder.itemView.getTop();
                }
                boolean goingDown;
                boolean changed = true;
                if (prevPosition == firstVisiblePosition) {
                    final int topDelta = prevTop - firstViewTop;
                    goingDown = firstViewTop < prevTop;
                    changed = Math.abs(topDelta) > 1;
                } else {
                    goingDown = firstVisiblePosition > prevPosition;
                }
                if (changed && scrollUpdated && (goingDown || scrollingManually)) {
                    hide(goingDown);
                }
                prevPosition = firstVisiblePosition;
                prevTop = firstViewTop;
                scrollUpdated = true;
            }
        });
    }

    /** Upstream floating button ThemeDescriptions. */
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate delegate = this::updateColors;
        arrayList.add(new ThemeDescription(floatingButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionIcon));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_chats_actionBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_chats_actionPressedBackground));
        return arrayList;
    }
}
