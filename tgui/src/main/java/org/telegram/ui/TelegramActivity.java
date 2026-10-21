package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Host activity for {@link BaseFragment} navigation, extracted from upstream {@code LaunchActivity}.
 * <p>
 * It owns a {@link DrawerLayoutContainer} + {@link ActionBarLayout}, forwards the activity
 * lifecycle to the fragment stack, keeps status/navigation bar colors in sync with the current
 * theme and plays the circular-reveal day/night switch animation on
 * {@link NotificationCenter#needSetDayNightTheme}.
 * <p>
 * Subclasses only provide the first fragment via {@link #createRootFragment()}.
 * Declare the activity with
 * {@code android:configChanges="keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|uiMode|density"}
 * so theme/density changes are handled in place.
 */
public abstract class TelegramActivity extends Activity implements NotificationCenter.NotificationCenterDelegate, TelegramUI.Host, INavigationLayout.INavigationLayoutDelegate {

    protected FrameLayout frameLayout;
    protected DrawerLayoutContainer drawerLayoutContainer;
    protected ActionBarLayout actionBarLayout;
    protected final List<BaseFragment> mainFragmentsStack = new ArrayList<>();

    private ImageView themeSwitchImageView;
    private boolean isNavigationBarColorFrozen;

    /** The fragment shown when the activity starts with an empty stack. */
    protected abstract BaseFragment createRootFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        TelegramUI.init(getApplicationContext());
        AndroidUtilities.checkDisplaySize(this, getResources().getConfiguration());
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(R.style.Theme_TMessages);
        try {
            setTaskDescription(new ActivityManager.TaskDescription(null, null, Theme.getColor(Theme.key_actionBarDefault) | 0xff000000));
        } catch (Throwable ignore) {
        }
        getWindow().setBackgroundDrawableResource(R.drawable.transparent);
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 24) {
            AndroidUtilities.isInMultiwindow = isInMultiWindowMode();
        }
        Theme.createDialogsResources(this);
        AndroidUtilities.fillStatusBarHeight(this, false);

        actionBarLayout = new ActionBarLayout(this, true);

        frameLayout = new FrameLayout(this);
        frameLayout.setClipToPadding(false);
        frameLayout.setClipChildren(false);
        setContentView(frameLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        themeSwitchImageView = new ImageView(this);
        themeSwitchImageView.setVisibility(View.GONE);

        drawerLayoutContainer = new DrawerLayoutContainer(this);
        drawerLayoutContainer.setClipChildren(false);
        drawerLayoutContainer.setClipToPadding(false);
        drawerLayoutContainer.setBehindKeyboardColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        frameLayout.addView(drawerLayoutContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        drawerLayoutContainer.addView(actionBarLayout.getView(), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        drawerLayoutContainer.setParentActionBarLayout(actionBarLayout);
        drawerLayoutContainer.setAllowOpenDrawer(false, false);
        actionBarLayout.setDrawerLayoutContainer(drawerLayoutContainer);
        actionBarLayout.setFragmentStack(mainFragmentsStack);
        actionBarLayout.setFragmentStackChangedListener(() -> checkSystemBarColors(true, false));
        actionBarLayout.setDelegate(this);

        TelegramUI.setHost(this);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);

        if (mainFragmentsStack.isEmpty()) {
            // addFragmentToStack already attaches (creates) the root view; rebuilding here would
            // call createView() a second time.
            actionBarLayout.addFragmentToStack(createRootFragment());
        } else {
            actionBarLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
        }
        checkSystemBarColors();
    }

    public INavigationLayout getActionBarLayout() {
        return actionBarLayout;
    }

    public DrawerLayoutContainer getDrawerLayoutContainer() {
        return drawerLayoutContainer;
    }

    public BaseFragment getLastFragment() {
        return actionBarLayout.getLastFragment();
    }

    public boolean presentFragment(BaseFragment fragment) {
        return actionBarLayout.presentFragment(fragment);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TelegramUI.setHost(this);
        actionBarLayout.onResume();
        Theme.checkAutoNightThemeConditions();
    }

    @Override
    protected void onPause() {
        super.onPause();
        actionBarLayout.onPause();
    }

    @Override
    protected void onDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        if (TelegramUI.getHost() == this) {
            TelegramUI.setHost(null);
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayoutContainer.isDrawerOpened()) {
            drawerLayoutContainer.closeDrawer(false);
        } else if (!TelegramUI.sheetFragmentsStack.isEmpty()) {
            TelegramUI.sheetFragmentsStack.get(TelegramUI.sheetFragmentsStack.size() - 1).onBackPressed();
        } else {
            // closes search / action mode first; closing the root fragment finishes the activity
            // through needCloseLastFragment()
            actionBarLayout.onBackPressed();
        }
    }

    @Override
    public boolean needCloseLastFragment(INavigationLayout layout) {
        if (layout.getFragmentStack().size() <= 1) {
            finish();
            return false;
        }
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            actionBarLayout.onKeyUp(keyCode, event);
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        actionBarLayout.onLowMemory();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        actionBarLayout.onUserLeaveHint();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        BaseFragment fragment = actionBarLayout.getLastFragment();
        if (fragment != null) {
            fragment.onActivityResultFragment(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        BaseFragment fragment = actionBarLayout.getLastFragment();
        if (fragment != null) {
            fragment.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        AndroidUtilities.checkDisplaySize(this, newConfig);
        super.onConfigurationChanged(newConfig);
        TelegramUI.onConfigurationChanged(newConfig);
        actionBarLayout.onConfigurationChanged(newConfig);
        if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM) {
            Theme.checkAutoNightThemeConditions();
        }
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        AndroidUtilities.isInMultiwindow = isInMultiWindowMode;
        super.onMultiWindowModeChanged(isInMultiWindowMode);
    }

    @Override
    public Dialog getVisibleDialog() {
        BaseFragment last = actionBarLayout != null ? actionBarLayout.getLastFragment() : null;
        return last != null ? last.getVisibleDialog() : null;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) {
            drawerLayoutContainer.setBehindKeyboardColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            boolean checkNavigationBarColor = args.length <= 1 || (boolean) args[1];
            checkSystemBarColors(args.length > 2 && (boolean) args[2], true, checkNavigationBarColor && !isNavigationBarColorFrozen && !actionBarLayout.isTransitionAnimationInProgress(), true);
        } else if (id == NotificationCenter.needSetDayNightTheme) {
            boolean instant = false;
            if (args.length > 2 && args[2] instanceof int[]) {
                if (themeSwitchImageView.getVisibility() == View.VISIBLE) {
                    return;
                }
                instant = playThemeSwitchReveal((int[]) args[2], args.length > 4 && (Boolean) args[4]);
            }
            Theme.ThemeInfo theme = (Theme.ThemeInfo) args[0];
            boolean nightTheme = (Boolean) args[1];
            int accentId = (Integer) args[3];
            Runnable calcInBackgroundEnd = args.length > 7 ? (Runnable) args[7] : null;
            actionBarLayout.animateThemedValues(theme, accentId, nightTheme, instant, calcInBackgroundEnd);
        }
    }

    /**
     * Snapshot the current UI and reveal the new theme in a circle from {@code pos}
     * (the upstream sun/moon switch effect).
     */
    private boolean playThemeSwitchReveal(int[] pos, boolean toDark) {
        try {
            int w = drawerLayoutContainer.getMeasuredWidth();
            int h = drawerLayoutContainer.getMeasuredHeight();
            isNavigationBarColorFrozen = true;
            Bitmap bitmap = AndroidUtilities.snapshotView(drawerLayoutContainer);
            frameLayout.removeView(themeSwitchImageView);
            themeSwitchImageView = new ImageView(this);
            if (toDark) {
                frameLayout.addView(themeSwitchImageView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            } else {
                frameLayout.addView(themeSwitchImageView, 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            }
            themeSwitchImageView.setImageBitmap(bitmap);
            themeSwitchImageView.setVisibility(View.VISIBLE);
            float finalRadius = (float) Math.max(Math.sqrt((w - pos[0]) * (w - pos[0]) + (h - pos[1]) * (h - pos[1])), Math.sqrt(pos[0] * pos[0] + (h - pos[1]) * (h - pos[1])));
            float finalRadius2 = (float) Math.max(Math.sqrt((w - pos[0]) * (w - pos[0]) + pos[1] * pos[1]), Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1]));
            finalRadius = Math.max(finalRadius, finalRadius2);
            Animator anim = ViewAnimationUtils.createCircularReveal(toDark ? drawerLayoutContainer : themeSwitchImageView, pos[0], pos[1], toDark ? 0 : finalRadius, toDark ? finalRadius : 0);
            anim.setDuration(400);
            anim.setInterpolator(Easings.easeInOutQuad);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    drawerLayoutContainer.invalidate();
                    themeSwitchImageView.setImageDrawable(null);
                    themeSwitchImageView.setVisibility(View.GONE);
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.themeAccentListUpdated);
                }
            });
            AndroidUtilities.runOnUIThread(() -> {
                if (isNavigationBarColorFrozen) {
                    isNavigationBarColorFrozen = false;
                    checkSystemBarColors(false, true);
                }
            }, toDark ? (h - pos[1]) / AndroidUtilities.dp(2.25f) : 50);
            anim.start();
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            try {
                themeSwitchImageView.setImageDrawable(null);
                frameLayout.removeView(themeSwitchImageView);
            } catch (Exception e2) {
                FileLog.e(e2);
            }
            return false;
        }
    }

    public void checkSystemBarColors() {
        checkSystemBarColors(false, true, !isNavigationBarColorFrozen, true);
    }

    private void checkSystemBarColors(boolean checkStatusBar, boolean checkNavigationBar) {
        checkSystemBarColors(false, checkStatusBar, checkNavigationBar, true);
    }

    @Override
    public void checkSystemBarColors(boolean useCurrentFragment, boolean checkStatusBar, boolean checkNavigationBar, boolean checkButtons) {
        BaseFragment currentFragment = !mainFragmentsStack.isEmpty() ? mainFragmentsStack.get(mainFragmentsStack.size() - 1) : null;
        if (currentFragment != null && (currentFragment.isRemovingFromStack() || currentFragment.isInPreviewMode())) {
            currentFragment = mainFragmentsStack.size() > 1 ? mainFragmentsStack.get(mainFragmentsStack.size() - 2) : null;
        }
        boolean forceLightStatusBar = currentFragment != null && currentFragment.hasForceLightStatusBar();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkStatusBar) {
                boolean enable;
                if (currentFragment != null) {
                    enable = currentFragment.isLightStatusBar();
                    if (currentFragment.sheetsStack != null && !currentFragment.sheetsStack.isEmpty()) {
                        BaseFragment.AttachedSheet sheet = currentFragment.sheetsStack.get(currentFragment.sheetsStack.size() - 1);
                        if (sheet.isShown()) {
                            enable = sheet.isAttachedLightStatusBar();
                        }
                    }
                } else {
                    int color = Theme.getColor(Theme.key_actionBarDefault, null, true);
                    enable = ColorUtils.calculateLuminance(color) > 0.7f;
                }
                AndroidUtilities.setLightStatusBar(getWindow(), enable, forceLightStatusBar);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && checkNavigationBar && (!useCurrentFragment || currentFragment == null || !currentFragment.isInPreviewMode())) {
                int color = currentFragment != null && useCurrentFragment ? currentFragment.getNavigationBarColor() : Theme.getColor(Theme.key_windowBackgroundGray, null, true);
                setNavigationBarColor(color, checkButtons);
            }
        }
        if ((SharedConfig.noStatusBar || forceLightStatusBar) && checkStatusBar) {
            getWindow().setStatusBarColor(0);
        }
    }

    public void setNavigationBarColor(int color, boolean checkButtons) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final Window window = getWindow();
            if (window.getNavigationBarColor() != color) {
                window.setNavigationBarColor(color);
                if (checkButtons) {
                    final float brightness = AndroidUtilities.computePerceivedBrightness(color);
                    AndroidUtilities.setLightNavigationBar(window, brightness >= 0.721f);
                }
            }
        }
    }
}
