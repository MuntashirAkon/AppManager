// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.text.DateFormat;
import java.util.Date;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;

public final class DateUtils {
    @NonNull
    public static String formatDate(long millis) {
        Date dateTime = new Date(millis);
        return getDateFormat().format(dateTime);
    }

    @NonNull
    public static String formatDateTime(long millis) {
        Date dateTime = new Date(millis);
        String date = getDateFormat().format(dateTime);
        String time = getTimeFormat().format(dateTime);
        return date + " " + time;
    }

    @NonNull
    public static String formatWeekMediumDateTime(long millis) {
        Date dateTime = new Date(millis);
        CharSequence week = android.text.format.DateFormat.format("EE", dateTime);
        String date = getMediumDateFormat().format(dateTime);
        String time = getTimeFormat().format(dateTime);
        return week + ", " + date + " " + time;
    }

    public static String getFormattedDuration(Context context, long millis) {
        return getFormattedDuration(context, millis, false);
    }

    public static String getFormattedDuration(Context context, long millis, boolean addSign) {
        return getFormattedDuration(context, millis, addSign, false);
    }

    public static String getFormattedDuration(Context context, long millis, boolean addSign, boolean includeSeconds) {
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

    private static DateFormat getDateFormat() {
        return android.text.format.DateFormat.getDateFormat(AppManager.getContext());
    }

    public static DateFormat getMediumDateFormat() {
        return android.text.format.DateFormat.getMediumDateFormat(AppManager.getContext());
    }

    private static DateFormat getTimeFormat() {
        return android.text.format.DateFormat.getTimeFormat(AppManager.getContext());
    }
}