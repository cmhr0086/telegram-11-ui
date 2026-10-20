package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.MenuDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;

import java.util.ArrayList;

/**
 * tgui: the chat-list selection action mode, transplanted from upstream 11.4.2
 * {@code DialogsActivity.createActionMode / showOrUpdateActionMode / hideActionMode}: a NumberTextView
 * counter (18sp bold, 72dp start margin), 54dp-wide icons that grow in with a 200 ms scaleY animation,
 * the action bar fading from {@code actionBarDefault} to {@code windowBackgroundWhite} over 200 ms
 * while the normal bar children fade out, and the back / menu drawable rotating to its close state.
 * Items are chosen by the host.
 * <pre>
 * DialogsActionMode mode = new DialogsActionMode(actionBar);
 * mode.addItem(ID_PIN, R.drawable.msg_pin, "Pin");
 * mode.addOverflowItem(ID_READ, R.drawable.msg_markread, "Mark as read");
 * mode.setCount(selected.size());     // 0 hides it
 * </pre>
 * Route the action bar's item clicks through {@link #isShowing()}: id -1 (back) should clear the
 * selection while the mode is shown, other ids are the host's items.
 */
public class DialogsActionMode {

    private final ActionBar actionBar;
    private ActionBarMenu actionMode;
    private NumberTextView countTextView;
    private ActionBarMenuItem otherItem;
    private final ArrayList<View> actionModeViews = new ArrayList<>();
    private ValueAnimator colorAnimator;
    private float progressToActionMode;
    private int count;
    private int defaultColorKey = Theme.key_actionBarDefault;
    private FilterTabsView filterTabsView;

    public DialogsActionMode(ActionBar actionBar) {
        this.actionBar = actionBar;
    }

    private void createActionMode() {
        if (actionMode != null) {
            return;
        }
        actionMode = actionBar.createActionMode(false, null);
        actionMode.setBackgroundColor(Color.TRANSPARENT);
        actionMode.drawBlur = false;

        countTextView = new NumberTextView(actionMode.getContext());
        countTextView.setTextSize(18);
        countTextView.setTypeface(AndroidUtilities.bold());
        countTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(countTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        countTextView.setOnTouchListener((v, event) -> true);
    }

    /** Adds an icon to the action mode bar (upstream pin / mute / archive / delete). */
    public ActionBarMenuItem addItem(int id, int icon, CharSequence contentDescription) {
        createActionMode();
        if (otherItem != null) {
            throw new IllegalStateException("add icons before overflow items");
        }
        ActionBarMenuItem item = actionMode.addItemWithWidth(id, icon, AndroidUtilities.dp(54), contentDescription);
        actionModeViews.add(item);
        return item;
    }

    /** Adds an entry to the ⋮ overflow menu (upstream mark as read / clear history / block…). */
    public View addOverflowItem(int id, int icon, CharSequence text) {
        createActionMode();
        if (otherItem == null) {
            otherItem = actionMode.addItemWithWidth(0, R.drawable.ic_ab_other, AndroidUtilities.dp(54), LocaleController.getString(R.string.AccDescrMoreOptions));
            actionModeViews.add(otherItem);
        }
        return otherItem.addSubItem(id, icon, text);
    }

    public ActionBarMenu getMenu() {
        createActionMode();
        return actionMode;
    }

    /**
     * Folder tabs living in the action bar's extra height: kept visible during the mode and recolored
     * like upstream ({@code animateColorsTo} profile tab colors and back).
     */
    public void setFilterTabsView(FilterTabsView view) {
        filterTabsView = view;
    }

    /** Action bar color key the bar returns to when the mode hides (archive uses actionBarDefaultArchived). */
    public void setDefaultColorKey(int key) {
        defaultColorKey = key;
    }

    public boolean isShowing() {
        return actionBar.isActionModeShowed();
    }

    public float getProgress() {
        return progressToActionMode;
    }

    /** Shows / updates the counter; 0 hides the mode. */
    public void setCount(int value) {
        int old = count;
        count = value;
        if (value <= 0) {
            hide();
            return;
        }
        boolean updateAnimated = false;
        if (actionBar.isActionModeShowed()) {
            updateAnimated = true;
        } else {
            show();
        }
        countTextView.setNumber(value, updateAnimated && old > 0);
    }

    private void show() {
        createActionMode();
        actionBar.setActionModeOverrideColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        actionBar.showActionMode();

        AnimatorSet animatorSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();
        for (int a = 0; a < actionModeViews.size(); a++) {
            View view = actionModeViews.get(a);
            view.setPivotY(ActionBar.getCurrentActionBarHeight() / 2);
            AndroidUtilities.clearDrawableAnimation(view);
            animators.add(ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.1f, 1.0f));
        }
        animatorSet.playTogether(animators);
        animatorSet.setDuration(200);
        animatorSet.start();

        animateTo(1f);
        if (filterTabsView != null) {
            filterTabsView.animateColorsTo(Theme.key_profile_tabSelectedLine, Theme.key_profile_tabSelectedText, Theme.key_profile_tabText, Theme.key_profile_tabSelector, Theme.key_actionBarActionModeDefault);
        }

        Drawable back = actionBar.getBackButton() != null ? actionBar.getBackButton().getDrawable() : null;
        if (back instanceof MenuDrawable) {
            ((MenuDrawable) back).setRotateToBack(false);
            ((MenuDrawable) back).setRotation(1, true);
        } else if (back instanceof BackDrawable) {
            ((BackDrawable) back).setRotation(1, true);
        }
    }

    /** Upstream hideActionMode. */
    public void hide() {
        count = 0;
        if (!actionBar.isActionModeShowed()) {
            return;
        }
        actionBar.hideActionMode();
        Drawable back = actionBar.getBackButton() != null ? actionBar.getBackButton().getDrawable() : null;
        if (back instanceof MenuDrawable) {
            ((MenuDrawable) back).setRotation(0, true);
        } else if (back instanceof BackDrawable) {
            ((BackDrawable) back).setRotation(0, true);
        }
        if (filterTabsView != null) {
            filterTabsView.animateColorsTo(Theme.key_actionBarTabLine, Theme.key_actionBarTabActiveText, Theme.key_actionBarTabUnactiveText, Theme.key_actionBarTabSelector, Theme.key_actionBarDefault);
        }
        animateTo(0f);
    }

    private void animateTo(float target) {
        if (colorAnimator != null) {
            colorAnimator.cancel();
        }
        colorAnimator = ValueAnimator.ofFloat(progressToActionMode, target);
        colorAnimator.addUpdateListener(valueAnimator -> {
            progressToActionMode = (float) valueAnimator.getAnimatedValue();
            applyProgress();
        });
        colorAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                colorAnimator = null;
            }
        });
        colorAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        colorAnimator.setDuration(200);
        colorAnimator.start();
    }

    private void applyProgress() {
        // upstream draws this blend in the fragment's dispatchDraw (actionBarSearchPaint)
        actionBar.setBackgroundColor(ColorUtils.blendARGB(Theme.getColor(defaultColorKey), Theme.getColor(Theme.key_windowBackgroundWhite), progressToActionMode));
        for (int i = 0; i < actionBar.getChildCount(); i++) {
            View child = actionBar.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && child != actionBar.getActionMode() && child != actionBar.getBackButton() && child != filterTabsView) {
                child.setAlpha(1f - progressToActionMode);
            }
        }
    }

    /** Re-applies colors after a theme change (wired through {@link #getThemeDescriptions()}). */
    public void updateColors() {
        if (countTextView != null) {
            countTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        }
        if (actionMode != null && actionBar.isActionModeShowed()) {
            actionBar.setActionModeOverrideColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }
        if (progressToActionMode > 0) {
            applyProgress();
        }
    }

    /** Upstream DialogsActivity action-mode ThemeDescriptions; add them to the fragment's list. */
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        createActionMode();
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate delegate = this::updateColors;
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_TOPBACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefaultTop));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_actionBarDefault));
        return arrayList;
    }
}
