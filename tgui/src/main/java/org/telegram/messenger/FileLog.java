package org.telegram.messenger;

import android.util.Log;

// tgui stub: upstream FileLog writes to disk; here everything goes to logcat.
public class FileLog {

    private static final String TAG = "tgui";

    public static void e(String message) {
        if (BuildVars.LOGS_ENABLED) Log.e(TAG, message);
    }

    public static void e(String message, Throwable e) {
        if (BuildVars.LOGS_ENABLED) Log.e(TAG, message, e);
    }

    public static void e(Throwable e) {
        e(e, true);
    }

    public static void e(Throwable e, boolean logToAppCenter) {
        if (BuildVars.LOGS_ENABLED) Log.e(TAG, "", e);
    }

    public static void fatal(Throwable e) {
        Log.e(TAG, "fatal", e);
    }

    public static void fatal(Throwable e, boolean logToAppCenter) {
        fatal(e);
    }

    public static void d(String message) {
        if (BuildVars.LOGS_ENABLED) Log.d(TAG, message);
    }

    public static void w(String message) {
        if (BuildVars.LOGS_ENABLED) Log.w(TAG, message);
    }
}
