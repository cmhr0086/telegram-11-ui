/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.Editable;
import android.text.Html;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.URLSpan;
import android.util.Base64;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RawRes;
import androidx.annotation.RequiresApi;
import androidx.core.util.Consumer;


import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;



import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;

import org.telegram.messenger.MessagesController;

import org.telegram.messenger.NotificationCenter;

import org.telegram.messenger.OneUIUtilities;
import org.telegram.tgui.R;

import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;

import org.telegram.messenger.Utilities;


import org.telegram.tgnet.SerializedData;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;



import org.telegram.ui.Cells.CheckBoxCell;
















import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * tgui: only the schedule date picker (used by the message input's "Schedule message" and
 * scheduled voice messages) is kept from upstream AlertsCreator, copied verbatim.
 */
public class AlertsCreator {

    public static boolean checkScheduleDate(TextView button, TextView infoText, int type, NumberPicker dayPicker, NumberPicker hourPicker, NumberPicker minutePicker) {
        return checkScheduleDate(button, infoText, 0, type, dayPicker, hourPicker, minutePicker);
    }

    public static boolean checkScheduleDate(TextView button, TextView infoText, long maxDate, int type, NumberPicker dayPicker, NumberPicker hourPicker, NumberPicker minutePicker) {
        int day = dayPicker.getValue();
        int hour = hourPicker.getValue();
        int minute = minutePicker.getValue();
        Calendar calendar = Calendar.getInstance();

        long systemTime = System.currentTimeMillis();
        calendar.setTimeInMillis(systemTime);
        int currentYear = calendar.get(Calendar.YEAR);
        int currentDay = calendar.get(Calendar.DAY_OF_YEAR);

        int maxDay = 0, maxHour = 0, maxMinute = 0;
        if (maxDate > 0) {
            maxDate *= 1000;
            calendar.setTimeInMillis(systemTime + maxDate);
            calendar.set(Calendar.HOUR_OF_DAY, maxHour = 23);
            calendar.set(Calendar.MINUTE, maxMinute = 59);
            calendar.set(Calendar.SECOND, 59);
            maxDay = 7; // ???
            maxDate = calendar.getTimeInMillis();
        }

        calendar.setTimeInMillis(systemTime + 60000L);
        int minDay = 1;
        int minHour = calendar.get(Calendar.HOUR_OF_DAY);
        int minMinute = calendar.get(Calendar.MINUTE);

        calendar.setTimeInMillis(System.currentTimeMillis() + (long) day * 24 * 3600 * 1000);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        long currentTime = calendar.getTimeInMillis();
        calendar.setTimeInMillis(currentTime);

        dayPicker.setMinValue(0);
        if (maxDate > 0) {
            dayPicker.setMaxValue(maxDay);
        }
        day = dayPicker.getValue();

        hourPicker.setMinValue(day == 0 ? minHour : 0);
        if (maxDate > 0) {
            hourPicker.setMaxValue(day == maxDay ? maxHour : 23);
        }
        hour = hourPicker.getValue();

        minutePicker.setMinValue(day == 0 && hour == minHour ? minMinute : 0);
        if (maxDate > 0) {
            minutePicker.setMaxValue(day == maxDay && hour == maxHour ? maxMinute : 59);
        }
        minute = minutePicker.getValue();
        if (currentTime <= systemTime + 60000L) {
            calendar.setTimeInMillis(systemTime + 60000L);
        } else if (maxDate > 0 && currentTime > maxDate) {
            calendar.setTimeInMillis(maxDate);
        }
        int selectedYear = calendar.get(Calendar.YEAR);

        calendar.setTimeInMillis(System.currentTimeMillis() + (long) day * 24 * 3600 * 1000);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);

        long time = calendar.getTimeInMillis();
        if (button != null) {
            int num;
            if (day == 0) {
                num = 0;
            } else if (currentYear == selectedYear) {
                num = 1;
            } else {
                num = 2;
            }
            if (type == 1) {
                num += 3;
            } else if (type == 2) {
                num += 6;
            } else if (type == 3) {
                num += 9;
            }
            button.setText(LocaleController.getInstance().getFormatterScheduleSend(num).format(time));
        }
        if (infoText != null) {
            int diff = (int) ((time - systemTime) / 1000);
            String t;
            if (diff > 24 * 60 * 60) {
                t = LocaleController.formatPluralString("DaysSchedule", Math.round(diff / (24 * 60 * 60.0f)));
            } else if (diff >= 60 * 60) {
                t = LocaleController.formatPluralString("HoursSchedule", Math.round(diff / (60 * 60.0f)));
            } else if (diff >= 60) {
                t = LocaleController.formatPluralString("MinutesSchedule", Math.round(diff / 60.0f));
            } else {
                t = LocaleController.formatPluralString("SecondsSchedule", diff);
            }
            if (infoText.getTag() != null) {
                infoText.setText(LocaleController.formatString("VoipChannelScheduleInfo", R.string.VoipChannelScheduleInfo, t));
            } else {
                infoText.setText(LocaleController.formatString("VoipGroupScheduleInfo", R.string.VoipGroupScheduleInfo, t));
            }
        }
        return currentTime - systemTime > 60000L;
    }

    public interface ScheduleDatePickerDelegate {
        void didSelectDate(boolean notify, int scheduleDate);
    }

    public static class ScheduleDatePickerColors {

        public final int textColor;
        public final int backgroundColor;

        public final int iconColor;
        public final int iconSelectorColor;

        public final int subMenuTextColor;
        public final int subMenuBackgroundColor;
        public final int subMenuSelectorColor;

        public final int buttonTextColor;
        public final int buttonBackgroundColor;
        public final int buttonBackgroundPressedColor;

        private ScheduleDatePickerColors() {
            this(null);
        }

        public ScheduleDatePickerColors(Theme.ResourcesProvider rp) {
            this(rp != null ? rp.getColorOrDefault(Theme.key_dialogTextBlack) : Theme.getColor(Theme.key_dialogTextBlack),
                    rp != null ? rp.getColorOrDefault(Theme.key_dialogBackground) : Theme.getColor(Theme.key_dialogBackground),
                    rp != null ? rp.getColorOrDefault(Theme.key_sheet_other) : Theme.getColor(Theme.key_sheet_other),
                    rp != null ? rp.getColorOrDefault(Theme.key_player_actionBarSelector) : Theme.getColor(Theme.key_player_actionBarSelector),
                    rp != null ? rp.getColorOrDefault(Theme.key_actionBarDefaultSubmenuItem) : Theme.getColor(Theme.key_actionBarDefaultSubmenuItem),
                    rp != null ? rp.getColorOrDefault(Theme.key_actionBarDefaultSubmenuBackground) : Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground),
                    rp != null ? rp.getColorOrDefault(Theme.key_listSelector) : Theme.getColor(Theme.key_listSelector),
                    rp != null ? rp.getColorOrDefault(Theme.key_featuredStickers_buttonText) : Theme.getColor(Theme.key_featuredStickers_buttonText),
                    rp != null ? rp.getColorOrDefault(Theme.key_featuredStickers_addButton) : Theme.getColor(Theme.key_featuredStickers_addButton),
                    rp != null ? rp.getColorOrDefault(Theme.key_featuredStickers_addButtonPressed) : Theme.getColor(Theme.key_featuredStickers_addButtonPressed));
        }

        public ScheduleDatePickerColors(int textColor, int backgroundColor, int iconColor, int iconSelectorColor, int subMenuTextColor, int subMenuBackgroundColor, int subMenuSelectorColor) {
            this(textColor, backgroundColor, iconColor, iconSelectorColor, subMenuTextColor, subMenuBackgroundColor, subMenuSelectorColor, Theme.getColor(Theme.key_featuredStickers_buttonText), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed));
        }

        public ScheduleDatePickerColors(int textColor, int backgroundColor, int iconColor, int iconSelectorColor, int subMenuTextColor, int subMenuBackgroundColor, int subMenuSelectorColor, int buttonTextColor, int buttonBackgroundColor, int buttonBackgroundPressedColor) {
            this.textColor = textColor;
            this.backgroundColor = backgroundColor;
            this.iconColor = iconColor;
            this.iconSelectorColor = iconSelectorColor;
            this.subMenuTextColor = subMenuTextColor;
            this.subMenuBackgroundColor = subMenuBackgroundColor;
            this.subMenuSelectorColor = subMenuSelectorColor;
            this.buttonTextColor = buttonTextColor;
            this.buttonBackgroundColor = buttonBackgroundColor;
            this.buttonBackgroundPressedColor = buttonBackgroundPressedColor;
        }
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, long dialogId, final ScheduleDatePickerDelegate datePickerDelegate) {
        return createScheduleDatePickerDialog(context, dialogId, -1, datePickerDelegate, null);
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, long dialogId, final ScheduleDatePickerDelegate datePickerDelegate, Theme.ResourcesProvider resourcesProvider) {
        return createScheduleDatePickerDialog(context, dialogId, -1, datePickerDelegate, null, resourcesProvider);
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, long dialogId, final ScheduleDatePickerDelegate datePickerDelegate, final ScheduleDatePickerColors datePickerColors) {
        return createScheduleDatePickerDialog(context, dialogId, -1, datePickerDelegate, null, datePickerColors, null);
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, long dialogId, final ScheduleDatePickerDelegate datePickerDelegate, final Runnable cancelRunnable, Theme.ResourcesProvider resourcesProvider) {
        return createScheduleDatePickerDialog(context, dialogId, -1, datePickerDelegate, cancelRunnable, resourcesProvider);
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, long dialogId, long currentDate, final ScheduleDatePickerDelegate datePickerDelegate, final Runnable cancelRunnable) {
        return createScheduleDatePickerDialog(context, dialogId, currentDate, datePickerDelegate, cancelRunnable, new ScheduleDatePickerColors(), null);
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, long dialogId, long currentDate, final ScheduleDatePickerDelegate datePickerDelegate, final Runnable cancelRunnable, Theme.ResourcesProvider resourcesProvider) {
        return createScheduleDatePickerDialog(context, dialogId, currentDate, datePickerDelegate, cancelRunnable, new ScheduleDatePickerColors(resourcesProvider), resourcesProvider);
    }

    public static BottomSheet.Builder createScheduleDatePickerDialog(Context context, long dialogId, long currentDate, final ScheduleDatePickerDelegate datePickerDelegate, final Runnable cancelRunnable, final ScheduleDatePickerColors datePickerColors, Theme.ResourcesProvider resourcesProvider) {
        if (context == null) {
            return null;
        }

        long selfUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();

        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourcesProvider);
        builder.setApplyBottomPadding(false);

        final NumberPicker dayPicker = new NumberPicker(context, resourcesProvider);
        dayPicker.setTextColor(datePickerColors.textColor);
        dayPicker.setTextOffset(dp(10));
        dayPicker.setItemCount(5);
        final NumberPicker hourPicker = new NumberPicker(context, resourcesProvider) {
            @Override
            protected CharSequence getContentDescription(int value) {
                return LocaleController.formatPluralString("Hours", value);
            }
        };
        hourPicker.setWrapSelectorWheel(true);
        hourPicker.setAllItemsCount(24);
        hourPicker.setItemCount(5);
        hourPicker.setTextColor(datePickerColors.textColor);
        hourPicker.setTextOffset(-dp(10));
        final NumberPicker minutePicker = new NumberPicker(context, resourcesProvider) {
            @Override
            protected CharSequence getContentDescription(int value) {
                return LocaleController.formatPluralString("Minutes", value);
            }
        };
        minutePicker.setWrapSelectorWheel(true);
        minutePicker.setAllItemsCount(60);
        minutePicker.setItemCount(5);
        minutePicker.setTextColor(datePickerColors.textColor);
        minutePicker.setTextOffset(-dp(34));

        LinearLayout container = new LinearLayout(context) {

            boolean ignoreLayout = false;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                ignoreLayout = true;
                int count;
                if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                    count = 3;
                } else {
                    count = 5;
                }
                dayPicker.setItemCount(count);
                hourPicker.setItemCount(count);
                minutePicker.setItemCount(count);
                dayPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                hourPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                minutePicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                ignoreLayout = false;
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        container.setOrientation(LinearLayout.VERTICAL);

        FrameLayout titleLayout = new FrameLayout(context);
        container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 0, 0, 4));

        TextView titleView = new TextView(context);
        if (dialogId == selfUserId) {
            titleView.setText(LocaleController.getString(R.string.SetReminder));
        } else {
            titleView.setText(LocaleController.getString(R.string.ScheduleMessage));
        }
        titleView.setTextColor(datePickerColors.textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 12, 0, 0));
        titleView.setOnTouchListener((v, event) -> true);

        // tgui: upstream adds a "Send when online" menu here for users with a known last-seen time; the input bar offers it in its own long-press menu
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1.0f);
        container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12));

        long currentTime = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTime);
        int currentYear = calendar.get(Calendar.YEAR);

        TextView buttonTextView = new TextView(context) {
            @Override
            public CharSequence getAccessibilityClassName() {
                return Button.class.getName();
            }
        };

        linearLayout.addView(dayPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f));
        dayPicker.setMinValue(0);
        dayPicker.setMaxValue(365);
        dayPicker.setWrapSelectorWheel(false);
        dayPicker.setFormatter(value -> {
            if (value == 0) {
                return LocaleController.getString(R.string.MessageScheduleToday);
            } else {
                long date = currentTime + (long) value * 86400000L;
                calendar.setTimeInMillis(date);
                int year = calendar.get(Calendar.YEAR);
                if (year == currentYear) {
                    return (
                        LocaleController.getInstance().getFormatterWeek().format(date) +
                        ", " +
                        LocaleController.getInstance().getFormatterScheduleDay().format(date)
                    );
                } else {
                    return LocaleController.getInstance().getFormatterScheduleYear().format(date);
                }
            }
        });
        final NumberPicker.OnValueChangeListener onValueChangeListener = (picker, oldVal, newVal) -> {
            checkScheduleDate(buttonTextView, null, selfUserId == dialogId ? 1 : 0, dayPicker, hourPicker, minutePicker);
        };
        dayPicker.setOnValueChangedListener(onValueChangeListener);

        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        linearLayout.addView(hourPicker, LayoutHelper.createLinear(0, 54 * 5, 0.2f));
        hourPicker.setFormatter(value -> String.format("%02d", value));
        hourPicker.setOnValueChangedListener(onValueChangeListener);

        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setValue(0);
        minutePicker.setFormatter(value -> String.format("%02d", value));
        linearLayout.addView(minutePicker, LayoutHelper.createLinear(0, 54 * 5, 0.3f));
        minutePicker.setOnValueChangedListener(onValueChangeListener);

        if (currentDate > 0 && currentDate != 0x7FFFFFFE) {
            currentDate *= 1000;
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            int days = (int) ((currentDate - calendar.getTimeInMillis()) / (24 * 60 * 60 * 1000));
            calendar.setTimeInMillis(currentDate);
            if (days >= 0) {
                minutePicker.setValue(calendar.get(Calendar.MINUTE));
                hourPicker.setValue(calendar.get(Calendar.HOUR_OF_DAY));
                dayPicker.setValue(days);
            }
        }
        final boolean[] canceled = {true};

        checkScheduleDate(buttonTextView, null, selfUserId == dialogId ? 1 : 0, dayPicker, hourPicker, minutePicker);

        buttonTextView.setPadding(dp(34), 0, dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(datePickerColors.buttonTextColor);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setBackground(Theme.AdaptiveRipple.filledRect(datePickerColors.buttonBackgroundColor, 8));
        container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM, 16, 15, 16, 16));
        buttonTextView.setOnClickListener(v -> {
            canceled[0] = false;
            boolean setSeconds = checkScheduleDate(null, null, selfUserId == dialogId ? 1 : 0, dayPicker, hourPicker, minutePicker);
            calendar.setTimeInMillis(System.currentTimeMillis() + (long) dayPicker.getValue() * 24 * 3600 * 1000);
            calendar.set(Calendar.HOUR_OF_DAY, hourPicker.getValue());
            calendar.set(Calendar.MINUTE, minutePicker.getValue());
            if (setSeconds) {
                calendar.set(Calendar.SECOND, 0);
            }
            datePickerDelegate.didSelectDate(true, (int) (calendar.getTimeInMillis() / 1000));
            builder.getDismissRunnable().run();
        });

        builder.setCustomView(container);
        BottomSheet bottomSheet = builder.show();
        bottomSheet.setOnDismissListener(dialog -> {
            if (cancelRunnable != null && canceled[0]) {
                cancelRunnable.run();
            }
        });
        bottomSheet.setBackgroundColor(datePickerColors.backgroundColor);
        bottomSheet.fixNavigationBar(datePickerColors.backgroundColor);
        return builder;
    }
}
