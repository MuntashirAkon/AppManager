package io.github.muntashirakon.AppManager.utils;

import java.text.DateFormat;
import java.util.Date;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;

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