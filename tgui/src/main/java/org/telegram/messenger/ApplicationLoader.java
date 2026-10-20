package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;

import java.io.File;

/**
 * tgui: upstream ApplicationLoader is the messenger's Application class.
 * In the library it only holds the process-wide context; call
 * {@link org.telegram.ui.TelegramUI#init(Context)} from your Application.
 */
public class ApplicationLoader {

    @SuppressLint("StaticFieldLeak")
    public static volatile Context applicationContext;
    public static volatile Handler applicationHandler;
    public static volatile boolean isScreenOn = true;
    public static volatile boolean mainInterfacePaused = true;
    public static volatile boolean mainInterfacePausedStageQueue = true;
    public static volatile boolean externalInterfacePaused = true;
    public static boolean canDrawOverlays;

    public static void init(Context context) {
        if (applicationContext != null) {
            return;
        }
        applicationContext = context.getApplicationContext();
        applicationHandler = new Handler(Looper.getMainLooper());
    }

    public static File getFilesDirFixed() {
        File dir = applicationContext.getFilesDir();
        if (dir == null) {
            dir = new File("/data/data/" + applicationContext.getPackageName() + "/files");
        }
        dir.mkdirs();
        return dir;
    }

    public static boolean isStandaloneBuild() {
        return false;
    }

    public static boolean isHuaweiStoreBuild() {
        return false;
    }

    public static void onConfigurationChanged(Configuration newConfig) {
        LocaleController.getInstance().onDeviceConfigurationChange(newConfig);
        AndroidUtilities.checkDisplaySize(applicationContext, newConfig);
    }
}
