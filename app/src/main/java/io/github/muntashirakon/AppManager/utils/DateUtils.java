// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.text.DateFormat;
import java.util.Date;

import io.github.muntashirakon.AppManager.R;

public final class DateUtils {
    @NonNull
    public static String formatDate(@NonNull Context context, long millis) {
        Date dateTime = new Date(millis);
        return getDateFormat(context).format(dateTime);
    }

    @NonNull
    public static String formatDateTime(@NonNull Context context, long millis) {
        Date dateTime = new Date(millis);
        String date = getDateFormat(context).format(dateTime);
        String time = getTimeFormat(context).format(dateTime);
        return date + " " + time;
    }

    @NonNull
    public static String formatWeekMediumDateTime(@NonNull Context context, long millis) {
        Date dateTime = new Date(millis);
        CharSequence week = android.text.format.DateFormat.format("EE", dateTime);
        String date = getMediumDateFormat(context).format(dateTime);
        String time = getTimeFormat(context).format(dateTime);
        return week + ", " + date + " " + time;
    }

    public static String getFormattedDuration(@NonNull Context context, long millis) {
        return getFormattedDuration(context, millis, false);
    }

    public static String getFormattedDuration(@NonNull Context context, long millis, boolean addSign) {
        return getFormattedDuration(context, millis, addSign, false);
    }

    public static String getFormattedDuration(@NonNull Context context, long millis, boolean addSign,
                                              boolean includeSeconds) {
        String fTime = "";
        if (millis < 0) {
            millis = -millis;
            if (addSign) fTime = "- ";
        }
        long time = millis / 1000; // seconds
        long month, day, hour, min, sec;
        Resources res = context.getResources();
        month = time / 2_592_000;
        time %= 2_592_000;
        day = time / 86_400;
        time %= 86_400;
        hour = time / 3_600;
        time = time % 3_600;
        min = time / 60;
        sec = time % 60;
        int count = 0;
        if (month != 0) {
            fTime += res.getQuantityString(R.plurals.usage_months, (int) month, month);
            ++count;
        }
        if (day != 0) {
            fTime += (count > 0 ? " " : "") + res.getQuantityString(R.plurals.usage_days, (int) day, day);
            ++count;
        }
        if (hour != 0) {
            fTime += (count > 0 ? " " : "") + res.getQuantityString(R.plurals.usage_hours, (int) hour, hour);
            ++count;
        }
        if (min != 0) {
            fTime += (count > 0 ? " " : "") + res.getQuantityString(R.plurals.usage_minutes, (int) min, min);
            ++count;
        } else if (count == 0 && !includeSeconds) {
            fTime = context.getString(R.string.usage_less_than_a_minute);
        }
        if (includeSeconds) {
            fTime += (count > 0 ? " " : "") + res.getQuantityString(R.plurals.usage_seconds, (int) sec, sec);
        }
        return fTime;
    }

    @NonNull
    public static String getFormattedDurationShort(long millis, boolean addSign, boolean includeMinutes, boolean includeSeconds) {
        StringBuilder fTime = new StringBuilder();
        boolean isNegative;
        if (millis < 0) {
            millis = -millis;
            isNegative = true;
        } else isNegative = false;
        long time = millis / 1000; // seconds
        long month, day, hour, min, sec;
        month = time / 2_592_000;
        time %= 2_592_000;
        day = time / 86_400;
        time %= 86_400;
        hour = time / 3_600;
        time = time % 3_600;
        min = time / 60;
        sec = time % 60;
        if (!includeMinutes && (min > 0 || sec > 0)) {
            fTime.append("~");
        }
        if (isNegative && addSign) {
            fTime.append("-");
        }
        int count = 0;
        if (month != 0) {
            fTime.append(month).append("mo");
            ++count;
        }
        if (day != 0) {
            if (count > 0) fTime.append(" ");
            fTime.append(day).append("d");
            ++count;
        }
        if (hour != 0) {
            if (count > 0) fTime.append(" ");
            fTime.append(hour).append("h");
            ++count;
        }
        if (min != 0) {
            if (count > 0) fTime.append(" ");
            fTime.append(min).append("m");
            ++count;
        } else if (count == 0 && !includeSeconds) {
            fTime.append("1m");
        }
        if (includeSeconds) {
            if (count > 0) fTime.append(" ");
            fTime.append(sec).append("s");
        }
        return fTime.toString();
    }

    @NonNull
    public static String getFormattedDurationSingle(long millis, boolean addSign) {
        StringBuilder fTime = new StringBuilder();
        boolean isNegative;
        if (millis < 0) {
            millis = -millis;
            isNegative = true;
        } else isNegative = false;
        long time = millis / 1000; // seconds
        long month, day, hour, min;
        month = time / 2_592_000;
        time %= 2_592_000;
        day = time / 86_400;
        time %= 86_400;
        hour = time / 3_600;
        time = time % 3_600;
        min = time / 60;
        if (month > 0) {
            if (day > 0) {
                fTime.append('~');
            }
            fTime.append(month).append("mo");
        } else if (day > 0) {
            if (hour > 0) fTime.append('~');
            fTime.append(day).append('d');
        } else if (hour > 0) {
            if (min > 0) fTime.append('~');
            fTime.append(hour).append('h');
        } else if (min > 0) {
            fTime.append(min).append("m");
        } else {
            // Seconds not included
            fTime.append("~1m");
        }
        return (addSign && isNegative ? "-" : "") + fTime;
    }

    private static DateFormat getDateFormat(@NonNull Context context) {
        return android.text.format.DateFormat.getDateFormat(context);
    }

    public static DateFormat getMediumDateFormat(@NonNull Context context) {
        return android.text.format.DateFormat.getMediumDateFormat(context);
    }

    private static DateFormat getTimeFormat(@NonNull Context context) {
        return android.text.format.DateFormat.getTimeFormat(context);
    }
}