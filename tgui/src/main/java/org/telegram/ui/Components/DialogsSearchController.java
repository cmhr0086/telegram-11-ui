/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgui.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;

import java.util.ArrayList;

/**
 * tgui: the chat-list search of upstream DialogsActivity — the {@code searchItem} listener,
 * {@code showSearch} and {@code setSearchAnimationProgress} — on the host's views:
 * <ul>
 * <li>the search icon expands the action bar search field; the folder tabs fade and collapse,
 * the compose button goes away;</li>
 * <li>the chat list fades out and shrinks to 0.9 while the {@link DialogsSearchView} fades in
 * from 1.05 (200ms, 180ms back);</li>
 * <li>with recent searches, upstream's white search bar grows as a circle from the search icon
 * (whiteActionBar), with dark items and status bar icons;</li>
 * <li>back / the arrow collapses it all in reverse.</li>
 * </ul>
 * Results and recents come from the host through {@link DialogsSearchView.Delegate} /
 * {@link #getSearchView()}.{@link DialogsSearchView#setRecent setRecent}.
 */
public class DialogsSearchController {

    public interface Listener {
        /** Search opened or closed (upstream searching): disable the drawer swipe, folder paging, etc. */
        default void onSearchingChanged(boolean searching) {
        }

        /** Upstream canToggleSearch. */
        default boolean canToggleSearch() {
            return true;
        }
    }

    private final ActionBar actionBar;
    private final View listContainer;
    private final FilterTabsView filterTabsView;
    private final DialogsFloatingButton floatingButton;
    private final DialogsSearchView searchView;
    private Listener listener;
    private ActionBarMenuItem searchItem;

    private boolean searching;
    private boolean searchWas;
    private boolean searchIsShowed;
    private boolean searchWasFullyShowed;
    private boolean whiteActionBar;
    private boolean searchAnimationTabsDelayedCrossfade;
    private float searchAnimationProgress;
    private AnimatorSet searchAnimator;
    private Animator tabsAlphaAnimator;
    private int tabsHeight;

    /**
     * @param contentView    the fragment view; the search page is added on top of {@code listContainer}
     * @param listContainer  the chat list (or the folder pager) that fades out
     * @param filterTabsView folder tabs inside the action bar (extra height), or null
     * @param floatingButton the compose button, or null
     */
    public DialogsSearchController(ActionBar actionBar, FrameLayout contentView, View listContainer, FilterTabsView filterTabsView, DialogsFloatingButton floatingButton) {
        this.actionBar = actionBar;
        this.listContainer = listContainer;
        this.filterTabsView = filterTabsView;
        this.floatingButton = floatingButton;
        searchView = new DialogsSearchView(contentView.getContext());
        searchView.setVisibility(View.GONE);
        int index = contentView.indexOfChild(listContainer);
        contentView.addView(searchView, index + 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public DialogsSearchView getSearchView() {
        return searchView;
    }

    public ActionBarMenuItem getSearchItem() {
        return searchItem;
    }

    public boolean isSearching() {
        return searching;
    }

    /** Upstream DialogsActivity.createView: {@code menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true, false)}. */
    public ActionBarMenuItem createSearchItem(ActionBarMenu menu) {
        searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true, false).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
                if (searchView.getQuery().length() != 0) {
                    listContainer.setVisibility(View.GONE);
                }
                if (floatingButton != null) {
                    floatingButton.setVisibility(View.GONE);
                }
                actionBar.setBackButtonContentDescription(LocaleController.getString(R.string.AccDescrGoBack));
                if (!searchIsShowed && searchView.hasRecentSearch()) {
                    searchWas = true;
                    showSearch(true, true);
                }
                if (listener != null) {
                    listener.onSearchingChanged(true);
                }
            }

            @Override
            public boolean canCollapseSearch() {
                return true;
            }

            @Override
            public void onSearchCollapse() {
                searching = false;
                searchWas = false;
                if (floatingButton != null) {
                    floatingButton.setVisibility(View.VISIBLE);
                    // upstream: floatingHidden = true, translation dp(100), then hideFloatingButton(false) at the end
                    floatingButton.hide(true, false);
                }
                showSearch(false, true);
                actionBar.setBackButtonContentDescription(LocaleController.getString(R.string.AccDescrOpenMenu));
                if (listener != null) {
                    listener.onSearchingChanged(false);
                }
            }

            @Override
            public void onTextChanged(EditText editText) {
                String text = editText.getText().toString();
                if (!searching && text.length() == 0) {
                    return;
                }
                if (text.length() != 0 || searchView.hasRecentSearch()) {
                    searchWas = true;
                    if (!searchIsShowed) {
                        showSearch(true, true);
                    }
                }
                searchView.onTextChanged(text);
            }

            @Override
            public boolean canToggleSearch() {
                return !actionBar.isActionModeShowed() && (listener == null || listener.canToggleSearch());
            }
        });
        searchItem.setSearchFieldHint(LocaleController.getString(R.string.Search));
        searchItem.setContentDescription(LocaleController.getString(R.string.Search));
        return searchItem;
    }

    /** Upstream DialogsActivity.showSearch(show, startFromDownloads = false, animated). */
    public void showSearch(boolean show, boolean animated) {
        if (searchAnimator != null) {
            searchAnimator.cancel();
            searchAnimator = null;
        }
        if (tabsAlphaAnimator != null) {
            tabsAlphaAnimator.cancel();
            tabsAlphaAnimator = null;
        }
        searchIsShowed = show;
        if (show) {
            // upstream: whiteActionBar = !onlyDialogsAdapter(), i.e. there are recent searches
            whiteActionBar = searchView.hasRecentSearch();
            updateSearchFieldColors();
            searchView.clear();
            if (filterTabsView != null && filterTabsView.getVisibility() == View.VISIBLE) {
                tabsHeight = AndroidUtilities.dp(44);
            }
        }
        if (!show && filterTabsView != null && tabsHeight != 0) {
            filterTabsView.setVisibility(View.VISIBLE);
        }
        final boolean budget = SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW;
        if (animated) {
            if (show) {
                searchView.setVisibility(View.VISIBLE);
            } else {
                listContainer.setVisibility(View.VISIBLE);
            }
            searchView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            searchAnimator = new AnimatorSet();
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(listContainer, View.ALPHA, show ? 0.0f : 1.0f));
            if (!budget) {
                animators.add(ObjectAnimator.ofFloat(listContainer, View.SCALE_X, show ? 0.9f : 1.0f));
                animators.add(ObjectAnimator.ofFloat(listContainer, View.SCALE_Y, show ? 0.9f : 1.0f));
            } else {
                listContainer.setScaleX(1);
                listContainer.setScaleY(1);
            }
            animators.add(ObjectAnimator.ofFloat(searchView, View.ALPHA, show ? 1.0f : 0.0f));
            if (!budget) {
                animators.add(ObjectAnimator.ofFloat(searchView, View.SCALE_X, show ? 1.0f : 1.05f));
                animators.add(ObjectAnimator.ofFloat(searchView, View.SCALE_Y, show ? 1.0f : 1.05f));
            } else {
                searchView.setScaleX(1);
                searchView.setScaleY(1);
            }
            if (show) {
                searchView.setAlpha(0f);
                if (!budget) {
                    searchView.setScaleX(1.05f);
                    searchView.setScaleY(1.05f);
                }
            }

            if (filterTabsView != null && filterTabsView.getVisibility() == View.VISIBLE) {
                tabsAlphaAnimator = ObjectAnimator.ofFloat(filterTabsView.getTabsContainer(), View.ALPHA, show ? 0.0f : 1.0f).setDuration(100);
                tabsAlphaAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        tabsAlphaAnimator = null;
                    }
                });
            }

            ValueAnimator valueAnimator = ValueAnimator.ofFloat(searchAnimationProgress, show ? 1f : 0);
            valueAnimator.addUpdateListener(valueAnimator1 -> setSearchAnimationProgress((float) valueAnimator1.getAnimatedValue()));

            animators.add(valueAnimator);
            searchAnimator.playTogether(animators);
            searchAnimator.setDuration(show ? 200 : 180);
            searchAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);

            if (tabsHeight != 0) {
                int backgroundColor1 = Theme.getColor(Theme.key_actionBarDefault);
                int backgroundColor2 = Theme.getColor(Theme.key_windowBackgroundWhite);
                int sum = Math.abs(Color.red(backgroundColor1) - Color.red(backgroundColor2)) + Math.abs(Color.green(backgroundColor1) - Color.green(backgroundColor2)) + Math.abs(Color.blue(backgroundColor1) - Color.blue(backgroundColor2));
                searchAnimationTabsDelayedCrossfade = sum / 255f > 0.3f;
            } else {
                searchAnimationTabsDelayedCrossfade = true;
            }
            if (!show) {
                searchAnimator.setStartDelay(20);
                if (tabsAlphaAnimator != null) {
                    if (searchAnimationTabsDelayedCrossfade) {
                        tabsAlphaAnimator.setStartDelay(80);
                        tabsAlphaAnimator.setDuration(100);
                    } else {
                        tabsAlphaAnimator.setDuration(180);
                    }
                }
            }
            searchAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (searchAnimator != animation) {
                        return;
                    }
                    onSearchAnimationEnd(show);
                    searchAnimator = null;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (searchAnimator == animation) {
                        if (show) {
                            listContainer.setVisibility(View.GONE);
                        } else {
                            searchView.setVisibility(View.GONE);
                        }
                        searchAnimator = null;
                    }
                }
            });
            searchAnimator.start();
            if (tabsAlphaAnimator != null) {
                tabsAlphaAnimator.start();
            }
        } else {
            setSearchAnimationProgress(show ? 1f : 0f);
            listContainer.setAlpha(show ? 0.0f : 1.0f);
            listContainer.setScaleX(show ? 0.9f : 1.0f);
            listContainer.setScaleY(show ? 0.9f : 1.0f);
            searchView.setAlpha(show ? 1.0f : 0.0f);
            searchView.setScaleX(show ? 1.0f : 1.1f);
            searchView.setScaleY(show ? 1.0f : 1.1f);
            if (filterTabsView != null && filterTabsView.getVisibility() == View.VISIBLE) {
                filterTabsView.getTabsContainer().setAlpha(show ? 0.0f : 1.0f);
            }
            onSearchAnimationEnd(show);
        }
        checkStatusBar();
    }

    private void onSearchAnimationEnd(boolean show) {
        if (show) {
            listContainer.setVisibility(View.GONE);
            if (filterTabsView != null) {
                filterTabsView.setVisibility(View.GONE);
            }
            searchWasFullyShowed = true;
        } else {
            whiteActionBar = false;
            searchView.setVisibility(View.GONE);
            searchView.clear();
            listContainer.setVisibility(View.VISIBLE);
            if (floatingButton != null) {
                floatingButton.hide(false);
            }
            searchWasFullyShowed = false;
            tabsHeight = 0;
        }
        setSearchAnimationProgress(show ? 1f : 0);
        searchView.setBackground(null);
        if (!show) {
            searchView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }
        checkStatusBar();
    }

    /** Upstream setSearchAnimationProgress + the ContentView dispatchDraw part for the action bar. */
    private void setSearchAnimationProgress(float progress) {
        searchAnimationProgress = progress;
        if (whiteActionBar) {
            int color1 = Theme.getColor(Theme.key_actionBarDefaultIcon);
            actionBar.setItemsColor(ColorUtils.blendARGB(color1, Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), searchAnimationProgress), false);
            actionBar.setItemsColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), searchAnimationProgress), true);

            color1 = Theme.getColor(Theme.key_actionBarDefaultSelector);
            int color2 = Theme.getColor(Theme.key_actionBarActionModeDefaultSelector);
            actionBar.setItemsBackgroundColor(ColorUtils.blendARGB(color1, color2, searchAnimationProgress), false);
            // upstream ContentView: a circle while opening (or before the search was fully shown), else the whole bar
            actionBar.setSearchRevealProgress(progress, Theme.getColor(Theme.key_actionBarDefault), Theme.getColor(Theme.key_windowBackgroundWhite), searchIsShowed || !searchWasFullyShowed);
        } else {
            actionBar.setSearchRevealProgress(0, 0, 0, false);
        }
        // upstream getActionBarFullHeight: filtersTabsHeight * (1f - searchAnimationProgress)
        if (tabsHeight != 0) {
            actionBar.setExtraHeight((int) (tabsHeight * (1f - progress)));
            actionBar.requestLayout();
        }
    }

    private void updateSearchFieldColors() {
        if (searchItem == null) {
            return;
        }
        EditTextBoldCursor editText = searchItem.getSearchField();
        if (editText == null) {
            return;
        }
        if (whiteActionBar) {
            editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            editText.setHintTextColor(Theme.getColor(Theme.key_player_time));
            editText.setCursorColor(Theme.getColor(Theme.key_chat_messagePanelCursor));
        } else {
            editText.setCursorColor(Theme.getColor(Theme.key_actionBarDefaultSearch));
            editText.setHintTextColor(Theme.getColor(Theme.key_actionBarDefaultSearchPlaceholder));
            editText.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSearch));
        }
    }

    /** Upstream DialogsActivity.isLightStatusBar while searching; null when the search bar isn't white. */
    public Boolean isLightStatusBar() {
        if (searching && whiteActionBar) {
            return ColorUtils.calculateLuminance(Theme.getColor(Theme.key_windowBackgroundWhite)) > 0.7f;
        }
        return null;
    }

    private void checkStatusBar() {
        Activity activity = AndroidUtilities.findActivity(actionBar.getContext());
        if (activity == null) {
            return;
        }
        Boolean light = isLightStatusBar();
        if (light == null) {
            light = ColorUtils.calculateLuminance(Theme.getColor(Theme.key_actionBarDefault)) > 0.7f;
        }
        AndroidUtilities.setLightStatusBar(activity.getWindow(), light);
    }

    /** Re-applies the theme to the search bar and page (theme switch while searching). */
    public void updateColors() {
        updateSearchFieldColors();
        if (searchAnimationProgress > 0) {
            setSearchAnimationProgress(searchAnimationProgress);
        }
        searchView.updateColors();
        checkStatusBar();
    }

    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>(searchView.getThemeDescriptions());
        ThemeDescription.ThemeDescriptionDelegate delegate = this::updateColors;
        arrayList.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_actionBarDefaultSearch));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_actionBarDefaultSearchPlaceholder));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_actionBarActionModeDefaultIcon));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_actionBarActionModeDefaultSelector));
        return arrayList;
    }
}
