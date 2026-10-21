package org.telegram.messenger;

import org.telegram.tgui.R;

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

    // tgui: schedule-picker formatters (upstream uses FastDateFormat; java.text formats are enough on the UI thread)
    private java.text.DateFormat formatterWeek, formatterScheduleDay, formatterScheduleYear;
    private final java.text.DateFormat[] formatterScheduleSend = new java.text.DateFormat[15];

    private java.text.DateFormat createFormatter(Locale locale, int res, String defaultFormat) {
        String format;
        try {
            format = ApplicationLoader.applicationContext.getString(res);
        } catch (Exception e) {
            format = defaultFormat;
        }
        try {
            return new java.text.SimpleDateFormat(format, locale);
        } catch (Exception e) {
            return new java.text.SimpleDateFormat(defaultFormat, locale);
        }
    }

    public java.text.DateFormat getFormatterWeek() {
        if (formatterWeek == null) {
            formatterWeek = createFormatter(currentLocale, R.string.formatterWeek, "EEE");
        }
        return formatterWeek;
    }

    public java.text.DateFormat getFormatterScheduleDay() {
        if (formatterScheduleDay == null) {
            formatterScheduleDay = createFormatter(currentLocale, R.string.formatDateSchedule, "MMM d");
        }
        return formatterScheduleDay;
    }

    public java.text.DateFormat getFormatterScheduleYear() {
        if (formatterScheduleYear == null) {
            formatterScheduleYear = createFormatter(currentLocale, R.string.formatDateScheduleYear, "MMM d yyyy");
        }
        return formatterScheduleYear;
    }

    public java.text.DateFormat getFormatterScheduleSend(int n) {
        if (n < 0 || n >= formatterScheduleSend.length) {
            return null;
        }
        if (formatterScheduleSend[n] == null) {
            final Locale locale = currentLocale == null ? Locale.getDefault() : currentLocale;
            switch (n) {
                case 0: formatterScheduleSend[n] = createFormatter(locale, R.string.SendTodayAt, "'Send today at' HH:mm"); break;
                case 1: formatterScheduleSend[n] = createFormatter(locale, R.string.SendDayAt, "'Send on' MMM d 'at' HH:mm"); break;
                case 2: formatterScheduleSend[n] = createFormatter(locale, R.string.SendDayYearAt, "'Send on' MMM d yyyy 'at' HH:mm"); break;
                case 3: formatterScheduleSend[n] = createFormatter(locale, R.string.RemindTodayAt, "'Remind today at' HH:mm"); break;
                case 4: formatterScheduleSend[n] = createFormatter(locale, R.string.RemindDayAt, "'Remind on' MMM d 'at' HH:mm"); break;
                case 5: formatterScheduleSend[n] = createFormatter(locale, R.string.RemindDayYearAt, "'Remind on' MMM d yyyy 'at' HH:mm"); break;
                case 6: formatterScheduleSend[n] = createFormatter(locale, R.string.StartTodayAt, "'Start today at' HH:mm"); break;
                case 7: formatterScheduleSend[n] = createFormatter(locale, R.string.StartDayAt, "'Start on' MMM d 'at' HH:mm"); break;
                case 8: formatterScheduleSend[n] = createFormatter(locale, R.string.StartDayYearAt, "'Start on' MMM d yyyy 'at' HH:mm"); break;
                case 9: formatterScheduleSend[n] = createFormatter(locale, R.string.StartShortTodayAt, "'Today,' HH:mm"); break;
                case 10: formatterScheduleSend[n] = createFormatter(locale, R.string.StartShortDayAt, "MMM d',' HH:mm"); break;
                case 11: formatterScheduleSend[n] = createFormatter(locale, R.string.StartShortDayYearAt, "MMM d yyyy, HH:mm"); break;
                case 12: formatterScheduleSend[n] = createFormatter(locale, R.string.StartsTodayAt, "'Starts today at' HH:mm"); break;
                case 13: formatterScheduleSend[n] = createFormatter(locale, R.string.StartsDayAt, "'Starts on' MMM d 'at' HH:mm"); break;
                case 14: formatterScheduleSend[n] = createFormatter(locale, R.string.StartsDayYearAt, "'Starts on' MMM d yyyy 'at' HH:mm"); break;
            }
        }
        return formatterScheduleSend[n];
    }
}
