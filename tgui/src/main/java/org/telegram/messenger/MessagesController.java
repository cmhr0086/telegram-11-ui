package org.telegram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;

// tgui stub: only the preference accessors survive; there is no messaging layer.
public class MessagesController {

    public static SharedPreferences getGlobalMainSettings() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
    }

    public static SharedPreferences getMainSettings(int account) {
        return getGlobalMainSettings();
    }

    public static SharedPreferences getGlobalNotificationsSettings() {
        return ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
    }
}
