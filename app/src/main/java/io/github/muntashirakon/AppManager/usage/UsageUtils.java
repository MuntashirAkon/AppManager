// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.content.Context;
import android.text.format.DateFormat;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.TimeZone;

import io.github.muntashirakon.AppManager.R;

public final class UsageUtils {
    @NonNull
    public static TimeInterval getTimeInterval(@IntervalType int interval, long date) {
        switch (interval) {
            case IntervalType.INTERVAL_WEEKLY:
                return getWeekBounds(date);
            case IntervalType.INTERVAL_DAILY:
            default:
                return getDayBounds(date);
        }
    }

    @NonNull
    public static CharSequence getIntervalDescription(@NonNull Context context,
                                                      @IntervalType int interval,
                                                      long date) {
        switch (interval) {
            case IntervalType.INTERVAL_WEEKLY:
                return getWeekDescription(context, date);
            case IntervalType.INTERVAL_DAILY:
            default:
                return getDateDescription(context, date);
        }
    }

    public static long getNextDateFromInterval(@IntervalType int interval, long currentDate) {
        switch (interval) {
            case IntervalType.INTERVAL_WEEKLY:
                return UsageUtils.getNextWeekDay(currentDate);
            case IntervalType.INTERVAL_DAILY:
            default:
                return UsageUtils.getNextDay(currentDate);
        }
    }

    public static long getPreviousDateFromInterval(@IntervalType int interval, long currentDate) {
        switch (interval) {
            case IntervalType.INTERVAL_WEEKLY:
                return UsageUtils.getPreviousWeekDay(currentDate);
            case IntervalType.INTERVAL_DAILY:
            default:
                return UsageUtils.getPreviousDay(currentDate);
        }
    }

    public static boolean isToday(long date) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(date);
        moveToStartOfDay(calendar);
        long targetTime = calendar.getTimeInMillis();
        calendar.setTimeInMillis(System.currentTimeMillis());
        moveToStartOfDay(calendar);
        return targetTime == calendar.getTimeInMillis();
    }

    @NonNull
    public static TimeInterval getToday() {
        long timeNow = System.currentTimeMillis();
        return getDayBounds(timeNow);
    }

    @NonNull
    public static TimeInterval getLastWeek() {
        long timeNow = System.currentTimeMillis();
        return getWeekBounds(timeNow);
    }

    /**
     * Gets the beginning and end time of the day in milliseconds for a given date
     *
     * @param date Input date in milliseconds
     */
    @NonNull
    public static TimeInterval getDayBounds(long date) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(date);
        // Set to beginning of day (00:00:00.000)
        moveToStartOfDay(calendar);
        long beginningOfDay = calendar.getTimeInMillis();
        // Set to end of day (23:59:59.999)
        moveToEndOfDay(calendar);
        long endOfDay = calendar.getTimeInMillis();
        return new TimeInterval(IntervalType.INTERVAL_DAILY, beginningOfDay, endOfDay);
    }

    public static boolean hasNextDay(long date) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(date);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        moveToStartOfDay(calendar);
        long dayStart = calendar.getTimeInMillis();
        calendar.setTimeInMillis(System.currentTimeMillis());
        moveToStartOfDay(calendar);
        long todayStart = calendar.getTimeInMillis();
        return dayStart <= todayStart;
    }

    /**
     * Gets the next day date from a given date
     *
     * @param date Input date in milliseconds
     * @return Next day date in milliseconds
     */
    public static long getNextDay(long date) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(date);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTimeInMillis();
    }

    /**
     * Gets the next day date from a given date
     *
     * @param date Input date in milliseconds
     * @return Next day date in milliseconds
     */
    public static long getNextWeekDay(long date) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(date);
        calendar.add(Calendar.DAY_OF_MONTH, 7);
        return calendar.getTimeInMillis();
    }

    /**
     * Gets the previous day date from a given date
     *
     * @param date Input date in milliseconds
     * @return Previous day date in milliseconds
     */
    public static long getPreviousDay(long date) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(date);
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        return calendar.getTimeInMillis();
    }

    /**
     * Gets the previous day date from a given date
     *
     * @param date Input date in milliseconds
     * @return Previous day date in milliseconds
     */
    public static long getPreviousWeekDay(long date) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(date);
        calendar.add(Calendar.DAY_OF_MONTH, -7);
        return calendar.getTimeInMillis();
    }

    /**
     * Gets the beginning and end time of a week in milliseconds
     *
     * @param date Any date in the week in milliseconds
     */
    @NonNull
    public static TimeInterval getWeekBounds(long date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(date);
        // Clear time part for start of day calculation
        moveToStartOfDay(calendar);
        // Move to the first day of the week according to locale
        calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
        long startOfWeekMillis = calendar.getTimeInMillis();
        // Move to the last day of the week
        calendar.add(Calendar.DAY_OF_WEEK, 6);
        // Set time to the end of the day (23:59:59.999) for the last day
        moveToEndOfDay(calendar);
        long endOfWeekMillis = calendar.getTimeInMillis();

        return new TimeInterval(IntervalType.INTERVAL_WEEKLY, startOfWeekMillis, endOfWeekMillis);
    }

    /**
     * Returns a CharSequence describing the given dateMillis relative to today.
     *
     * @param context    Android context to access resources
     * @param dateMillis Date in milliseconds since epoch
     * @return description of date
     */
    @NonNull
    public static CharSequence getWeekDescription(Context context, long dateMillis) {
        TimeInterval targetInterval = getWeekBounds(dateMillis);
        long today = System.currentTimeMillis();

        if (today >= targetInterval.getStartTime() && today <= targetInterval.getEndTime()) {
            return context.getString(R.string.usage_this_week);
        }

        long sameDayLastWeek = getPreviousWeekDay(today);
        if (sameDayLastWeek >= targetInterval.getStartTime() && sameDayLastWeek <= targetInterval.getEndTime()) {
            return context.getString(R.string.usage_last_week);
        }

        java.text.DateFormat formatter = DateFormat.getMediumDateFormat(context);
        return formatter.format(targetInterval.getStartTime()) + "–" + formatter.format(targetInterval.getEndTime());
    }

    /**
     * Returns a CharSequence describing the given dateMillis relative to today.
     *
     * @param context    Android context to access resources
     * @param dateMillis Date in milliseconds since epoch
     * @return description of date
     */
    @NonNull
    public static CharSequence getDateDescription(Context context, long dateMillis) {
        Calendar inputCal = Calendar.getInstance();
        inputCal.setTimeInMillis(dateMillis);

        Calendar todayCal = Calendar.getInstance();

        // Clear time components for accurate comparison
        moveToStartOfDay(todayCal);
        moveToStartOfDay(inputCal);

        long diffMillis = todayCal.getTimeInMillis() - inputCal.getTimeInMillis();
        long oneDayMillis = 24 * 60 * 60 * 1000L;

        if (diffMillis == 0) {
            // Same day -> today
            return context.getString(R.string.usage_today);
        } else if (diffMillis == oneDayMillis) {
            // Yesterday
            return context.getString(R.string.usage_yesterday);
        } else {
            // Otherwise, formatted date string using system locale and patterns
            return DateFormat.getMediumDateFormat(context).format(dateMillis);
        }
    }

    private static void moveToStartOfDay(@NonNull Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private static void moveToEndOfDay(@NonNull Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
    }
}
