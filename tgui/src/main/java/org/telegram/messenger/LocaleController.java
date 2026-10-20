package org.telegram.messenger;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.SparseArray;

import androidx.annotation.StringRes;

import java.util.Locale;

/**
 * tgui: upstream LocaleController downloads language packs from the server.
 * Here strings come straight from Android resources (the library ships the
 * upstream default English strings; host apps can override/translate them).
 * Plural keys keep upstream naming: {@code Key_one}, {@code Key_few}, {@code Key_other}...
 */
public class LocaleController {

    public static boolean isRTL = false;
    public static int nameDisplayOrder = 1;
    public static boolean is24HourFormat = false;

    private static final SparseArray<String> resourcesCacheMap = new SparseArray<>();
    private static volatile LocaleController Instance;

    private Locale currentLocale;

    public static LocaleController getInstance() {
        LocaleController localInstance = Instance;
        if (localInstance == null) {
            synchronized (LocaleController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new LocaleController();
                }
            }
        }
        return localInstance;
    }

    public LocaleController() {
        applyConfiguration(ApplicationLoader.applicationContext.getResources().getConfiguration());
    }

    private void applyConfiguration(Configuration configuration) {
        Locale locale = Build.VERSION.SDK_INT >= 24 ? configuration.getLocales().get(0) : configuration.locale;
        currentLocale = locale != null ? locale : Locale.getDefault();
        is24HourFormat = DateFormat.is24HourFormat(ApplicationLoader.applicationContext);
        isRTL = TextUtils.getLayoutDirectionFromLocale(currentLocale) == android.view.View.LAYOUT_DIRECTION_RTL;
    }

    public void onDeviceConfigurationChange(Configuration newConfig) {
        applyConfiguration(newConfig);
    }

    public Locale getCurrentLocale() {
        return currentLocale;
    }

    public Locale getSystemDefaultLocale() {
        return currentLocale;
    }

    private static Resources res() {
        return ApplicationLoader.applicationContext.getResources();
    }

    public static String getString(@StringRes int res) {
        if (res == 0) {
            return "";
        }
        try {
            return res().getString(res);
        } catch (Exception e) {
            FileLog.e(e);
            return "LOC_ERR:" + res;
        }
    }

    @Deprecated
    public static String getString(String key, @StringRes int res) {
        return getString(res);
    }

    @Deprecated
    public static String getString(String key, String fallback, int fallbackRes, int res) {
        if (res != 0) {
            return getString(res);
        }
        return getString(fallbackRes);
    }

    @Deprecated
    public static String getString(String key, String fallback, int res) {
        return getString(res);
    }

    public static String getString(String key) {
        if (TextUtils.isEmpty(key)) {
            return "LOC_ERR:" + key;
        }
        int resourceId = getStringResId(key);
        if (resourceId != 0) {
            return getString(resourceId);
        }
        return "LOC_ERR:" + key;
    }

    public static int getStringResId(String key) {
        return res().getIdentifier(key, "string", ApplicationLoader.applicationContext.getPackageName());
    }

    public static String nullable(String val) {
        if (val == null || val.startsWith("LOC_ERR")) return null;
        return val;
    }

    private static String quantityKey(String key, int number) {
        String quantity = "other";
        if (Build.VERSION.SDK_INT >= 24) {
            quantity = android.icu.text.PluralRules.forLocale(getInstance().currentLocale).select(number);
        } else if (number == 1) {
            quantity = "one";
        }
        String param = key + "_" + quantity;
        if (getStringResId(param) == 0) {
            param = key + "_other";
        }
        return param;
    }

    public static String getPluralString(String key, int plural) {
        if (TextUtils.isEmpty(key)) {
            return "LOC_ERR:" + key;
        }
        return getString(quantityKey(key, plural));
    }

    public static String formatPluralString(String key, int plural, Object... args) {
        if (TextUtils.isEmpty(key)) {
            return "LOC_ERR:" + key;
        }
        Object[] argsWithPlural = new Object[args.length + 1];
        argsWithPlural[0] = plural;
        System.arraycopy(args, 0, argsWithPlural, 1, args.length);
        return formatStringSimple(getString(quantityKey(key, plural)), argsWithPlural);
    }

    public static String formatPluralStringComma(String key, int plural) {
        return formatPluralStringComma(key, plural, ',');
    }

    public static String formatPluralStringComma(String key, int plural, char symbol, Object... args) {
        String value = getString(quantityKey(key, plural));
        value = value.replace("%d", "%1$s").replace("%1$d", "%1$s");
        Object[] a = new Object[(args == null ? 0 : args.length) + 1];
        a[0] = formatNumber(plural, symbol);
        for (int i = 1; i < a.length; ++i) {
            a[i] = args[i - 1];
        }
        return formatStringSimple(value, a);
    }

    public static String formatNumber(long count, char symbol) {
        StringBuilder stringBuilder = new StringBuilder(String.format("%d", count));
        for (int a = stringBuilder.length() - 3; a > 0; a -= 3) {
            stringBuilder.insert(a, symbol);
        }
        return stringBuilder.toString();
    }

    public static String formatString(@StringRes int res, Object... args) {
        return formatStringSimple(getString(res), args);
    }

    @Deprecated
    public static String formatString(String key, int res, Object... args) {
        return formatString(res, args);
    }

    public static String formatStringSimple(String string, Object... args) {
        try {
            Locale locale = getInstance().currentLocale;
            return locale != null ? String.format(locale, string, args) : String.format(string, args);
        } catch (Exception e) {
            FileLog.e(e);
            return "LOC_ERR: " + string;
        }
    }

    public static boolean isRTLCharacter(char ch) {
        return Character.getDirectionality(ch) == Character.DIRECTIONALITY_RIGHT_TO_LEFT || Character.getDirectionality(ch) == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC || Character.getDirectionality(ch) == Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING || Character.getDirectionality(ch) == Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE;
    }
}
