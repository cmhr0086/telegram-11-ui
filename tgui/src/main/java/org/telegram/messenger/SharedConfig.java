package org.telegram.messenger;

import android.app.ActivityManager;
import android.os.Build;
import android.content.Context;
import androidx.annotation.IntDef;
import java.util.Locale;
import java.io.RandomAccessFile;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import android.content.SharedPreferences;

// tgui: trimmed upstream SharedConfig, only the UI-related settings remain.
public class SharedConfig {

    public static final boolean noStatusBar = true;
    public static int fontSize = 16;
    public static int bubbleRadius = 17;
    public static int lockRecordAudioVideoHint;
    public static boolean drawActionBarShadow = true;
    public static boolean useNewBlur = true;
    public static boolean forceDisableTabletMode;

    public static void updateTabletConfig() {
    }
    public static int lastLocalId = -210000;
    private static boolean configLoaded;

    public static int overrideDevicePerformanceClass = -1;
    private static int devicePerformanceClass = -1;

    public static void loadConfig() {
        if (configLoaded || ApplicationLoader.applicationContext == null) {
            return;
        }
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        fontSize = preferences.getInt("fons_size", AndroidUtilities.isTablet() ? 18 : 16);
        bubbleRadius = preferences.getInt("bubbleRadius", 17);
        lockRecordAudioVideoHint = preferences.getInt("lockRecordAudioVideoHint", 0);
        overrideDevicePerformanceClass = preferences.getInt("overrideDevicePerformanceClass", -1);
        configLoaded = true;
    }

    public static void saveConfig() {
        MessagesController.getGlobalMainSettings().edit()
                .putInt("fons_size", fontSize)
                .putInt("bubbleRadius", bubbleRadius)
                .apply();
    }

    public static int getLastLocalId() {
        return lastLocalId--;
    }

    public final static int PERFORMANCE_CLASS_LOW = 0;
    public final static int PERFORMANCE_CLASS_AVERAGE = 1;
    public final static int PERFORMANCE_CLASS_HIGH = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            PERFORMANCE_CLASS_LOW,
            PERFORMANCE_CLASS_AVERAGE,
            PERFORMANCE_CLASS_HIGH
    })
    public @interface PerformanceClass {}

    @PerformanceClass
    public static int getDevicePerformanceClass() {
        if (overrideDevicePerformanceClass != -1) {
            return overrideDevicePerformanceClass;
        }
        if (devicePerformanceClass == -1) {
            devicePerformanceClass = measureDevicePerformanceClass();
        }
        return devicePerformanceClass;
    }

    public static int measureDevicePerformanceClass() {
        int androidVersion = Build.VERSION.SDK_INT;
        int cpuCount = Runtime.getRuntime().availableProcessors();
        int memoryClass = ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();

        int totalCpuFreq = 0;
        int freqResolved = 0;
        for (int i = 0; i < cpuCount; i++) {
            try {
                RandomAccessFile reader = new RandomAccessFile(String.format(Locale.ENGLISH, "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i), "r");
                String line = reader.readLine();
                if (line != null) {
                    totalCpuFreq += Utilities.parseInt(line) / 1000;
                    freqResolved++;
                }
                reader.close();
            } catch (Throwable ignore) {}
        }
        int maxCpuFreq = freqResolved == 0 ? -1 : (int) Math.ceil(totalCpuFreq / (float) freqResolved);

        long ram = -1;
        try {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memoryInfo);
            ram = memoryInfo.totalMem;
        } catch (Exception ignore) {}

        if (
            cpuCount <= 2 ||
            memoryClass <= 100 ||
            cpuCount <= 4 && maxCpuFreq != -1 && maxCpuFreq <= 1250 ||
            cpuCount <= 4 && maxCpuFreq <= 1300 && memoryClass <= 128 && androidVersion <= 24 ||
            ram != -1 && ram < 2L * 1024L * 1024L * 1024L
        ) {
            return PERFORMANCE_CLASS_LOW;
        } else if (
            cpuCount < 8 ||
            memoryClass <= 160 ||
            maxCpuFreq != -1 && maxCpuFreq <= 2055 ||
            maxCpuFreq == -1 && cpuCount == 8 && androidVersion <= 23
        ) {
            return PERFORMANCE_CLASS_AVERAGE;
        }
        return PERFORMANCE_CLASS_HIGH;
    }

    public static boolean canBlurChat() {
        return getDevicePerformanceClass() >= (Build.VERSION.SDK_INT >= 31 ? PERFORMANCE_CLASS_AVERAGE : PERFORMANCE_CLASS_HIGH);
    }

    public static boolean chatBlurEnabled() {
        return canBlurChat() && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR);
    }

    private static Boolean animationsEnabled;

    public static void setAnimationsEnabled(boolean b) {
        animationsEnabled = b;
    }

    public static boolean animationsEnabled() {
        if (animationsEnabled == null) {
            animationsEnabled = MessagesController.getGlobalMainSettings().getBoolean("view_animations", true);
        }
        return animationsEnabled;
    }

    public static boolean deviceIsLow() {
        return getDevicePerformanceClass() == PERFORMANCE_CLASS_LOW;
    }

    public static boolean deviceIsAboveAverage() {
        return getDevicePerformanceClass() >= PERFORMANCE_CLASS_AVERAGE;
    }

    public static boolean deviceIsHigh() {
        return getDevicePerformanceClass() >= PERFORMANCE_CLASS_HIGH;
    }

    public static boolean deviceIsAverage() {
        return getDevicePerformanceClass() <= PERFORMANCE_CLASS_AVERAGE;
    }

    public static void increaseLockRecordAudioVideoHintShowed() {
        MessagesController.getGlobalMainSettings().edit().putInt("lockRecordAudioVideoHint", ++lockRecordAudioVideoHint).apply();
    }

    public static void removeLockRecordAudioVideoHint() {
        lockRecordAudioVideoHint = 3;
        MessagesController.getGlobalMainSettings().edit().putInt("lockRecordAudioVideoHint", 3).apply();
    }
}
