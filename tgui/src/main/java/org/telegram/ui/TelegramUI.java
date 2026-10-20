package org.telegram.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

/**
 * Entry point of the tgui library (Telegram Android UI components without the messenger).
 *
 * <pre>
 * public class App extends Application {
 *     public void onCreate() {
 *         super.onCreate();
 *         TelegramUI.init(this);
 *     }
 *     public void onConfigurationChanged(Configuration c) {
 *         super.onConfigurationChanged(c);
 *         TelegramUI.onConfigurationChanged(c);
 *     }
 * }
 * </pre>
 *
 * It replaces the bits of upstream {@code LaunchActivity} / {@code ApplicationLoader} that the
 * UI components reach for (system bar colors, visible dialog, sheet fragment stacks).
 */
public final class TelegramUI {

    /** Host-side hooks; upstream these were implemented by LaunchActivity. */
    public interface Host {
        /** Re-apply status/navigation bar colors after a sheet or dialog changed them. */
        default void checkSystemBarColors(boolean useCurrentFragment, boolean checkStatusBar, boolean checkNavigationBar, boolean animated) {
        }

        /** A dialog shown by the host outside of any fragment, if any. */
        default Dialog getVisibleDialog() {
            return null;
        }
    }

    /** Navigation layouts opened through {@code BaseFragment.showAsSheet}. */
    public static final ArrayList<INavigationLayout> sheetFragmentsStack = new ArrayList<>();

    private static Host host = new Host() {};
    private static boolean initialized;

    private TelegramUI() {
    }

    public static synchronized void init(Context context) {
        if (initialized) {
            return;
        }
        initialized = true;
        ApplicationLoader.init(context);
        AndroidUtilities.checkDisplaySize(ApplicationLoader.applicationContext, null);
        SharedConfig.loadConfig();
        LocaleController.getInstance();
        Theme.getActiveTheme();
    }

    public static void onConfigurationChanged(Configuration newConfig) {
        ApplicationLoader.onConfigurationChanged(newConfig);
    }

    public static void setHost(Host h) {
        host = h != null ? h : new Host() {};
    }

    public static Host getHost() {
        return host;
    }

    /**
     * Switch between the last used day and night themes with the circular reveal animation
     * (upstream drawer sun/moon button). The reveal starts at the center of {@code anchor};
     * pass null to switch without the reveal.
     */
    public static void toggleDayNight(View anchor) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        String dayThemeName = preferences.getString("lastDayTheme", "Blue");
        if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
            dayThemeName = "Blue";
        }
        String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
        if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
            nightThemeName = "Dark Blue";
        }
        Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
        if (dayThemeName.equals(nightThemeName)) {
            if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                dayThemeName = "Blue";
            } else {
                nightThemeName = "Dark Blue";
            }
        }
        boolean toDark = !themeInfo.isDark();
        applyTheme(Theme.getTheme(toDark ? nightThemeName : dayThemeName), anchor);
    }

    /** Apply one of {@link Theme#themes} with the reveal animation starting at {@code anchor}. */
    public static void applyTheme(Theme.ThemeInfo themeInfo, View anchor) {
        if (themeInfo == null) {
            return;
        }
        Theme.turnOffAutoNight((android.widget.FrameLayout) null, null);
        int[] pos = null;
        if (anchor != null && anchor.isAttachedToWindow()) {
            pos = new int[2];
            anchor.getLocationInWindow(pos);
            pos[0] += anchor.getMeasuredWidth() / 2;
            pos[1] += anchor.getMeasuredHeight() / 2;
        }
        boolean toDark = themeInfo.isDark();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, pos, -1, toDark, null);
    }
}
