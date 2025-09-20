// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.os.SystemClock;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public final class UsageUtils {
    @IntDef(value = {
            USAGE_TODAY,
            USAGE_YESTERDAY,
            USAGE_WEEKLY,
            USAGE_LAST_BOOT
    })
    public @interface IntervalType {
    }

    public static final int USAGE_TODAY = 0;
    public static final int USAGE_YESTERDAY = 1;
    public static final int USAGE_WEEKLY = 2;
    public static final int USAGE_LAST_BOOT = 5;

    @NonNull
    public static TimeInterval getTimeInterval(@IntervalType int sort) {
        switch (sort) {
            case USAGE_YESTERDAY:
                return getYesterday();
            case USAGE_WEEKLY:
                return getLastWeek();
            case USAGE_LAST_BOOT:
                return getSinceLastBoot();
            case USAGE_TODAY:
            default:
                return getToday();
        }
    }

    @NonNull
    public static TimeInterval getSinceLastBoot() {
        return new TimeInterval(SystemClock.elapsedRealtime(), System.currentTimeMillis());
    }

    @NonNull
    public static TimeInterval getToday() {
        long timeNow = System.currentTimeMillis();
        return getDayBounds(timeNow);
    }

    @NonNull
    public static TimeInterval getYesterday() {
        long timeNow = System.currentTimeMillis();
        return getDayBounds(getPreviousDay(timeNow));
    }

    @NonNull
    public static TimeInterval getLastWeek() {
        long timeEnd = System.currentTimeMillis();
        long timeStart = timeEnd - TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS);
        return new TimeInterval(timeStart, timeEnd);
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
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long beginningOfDay = calendar.getTimeInMillis();

        // Set to end of day (23:59:59.999)
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        long endOfDay = calendar.getTimeInMillis();
        return new TimeInterval(USAGE_TODAY, beginningOfDay, endOfDay);
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
     * Gets the week number of the year for a given date
     *
     * @param date Input date in milliseconds
     * @return Week number (1-53)
     */
    public static int getWeekOfYear(long date) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(date);
        return calendar.get(Calendar.WEEK_OF_YEAR);
    }

    /**
     * Gets the day number (Calendar constant) from a date in milliseconds
     *
     * @param date Input date in milliseconds
     * @return Day number constant (e.g., Calendar.MONDAY, Calendar.TUESDAY, etc.)
     */
    public static int getDayOfWeek(long date) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(date);
        return calendar.get(Calendar.DAY_OF_WEEK);
    }

    /**
     * Gets the year from a date in milliseconds
     *
     * @param date Input date in milliseconds
     * @return Year (e.g., 2025)
     */
    public static int getYear(long date) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(date);
        return calendar.get(Calendar.YEAR);
    }

    /**
     * Gets a date from week number and a date
     *
     * @param weekNumber Week number (1-53)
     * @param date       A date
     * @return Date in milliseconds
     */
    public static long getDateFromWeekAndDayName(int weekNumber, int date) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(date);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int year = calendar.get(Calendar.YEAR);
        return getDateFromWeekAndDay(weekNumber, year, dayOfWeek);
    }

    /**
     * Gets a date from week number and day of week
     *
     * @param weekNumber Week number (1-53)
     * @param year       Year
     * @param dayOfWeek  Day of week (Calendar.MONDAY = 2, Calendar.SUNDAY = 1)
     * @return Date in milliseconds
     */
    public static long getDateFromWeekAndDay(int weekNumber, int year, int dayOfWeek) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());

        // Set to the first day of the year
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // Set to the desired week
        calendar.set(Calendar.WEEK_OF_YEAR, weekNumber);

        // Set to the desired day of week
        calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek);

        return calendar.getTimeInMillis();
    }

    /**
     * Gets the beginning and end time of a week in milliseconds
     *
     * @param weekNumber Week number (1-53)
     * @param year       Year
     */
    public static TimeInterval getWeekBounds(int weekNumber, int year) {
        int firstDayOfWeek = Calendar.getInstance(TimeZone.getDefault()).getFirstDayOfWeek();
        long weekStart = getDateFromWeekAndDay(weekNumber, year, firstDayOfWeek);
        long weekEnd = getDateFromWeekAndDay(weekNumber, year, getEndOfWeekDay(firstDayOfWeek));

        // Set weekStart to beginning of Monday (00:00:00.000)
        Calendar startCal = Calendar.getInstance(TimeZone.getDefault());
         startCal.setTimeInMillis(weekStart);
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);

        // Set weekEnd to end of Sunday (23:59:59.999)
        Calendar endCal = Calendar.getInstance(TimeZone.getDefault());
        endCal.setTimeInMillis(weekEnd);
        endCal.set(Calendar.HOUR_OF_DAY, 23);
        endCal.set(Calendar.MINUTE, 59);
        endCal.set(Calendar.SECOND, 59);
        endCal.set(Calendar.MILLISECOND, 999);

        return new TimeInterval(USAGE_WEEKLY, startCal.getTimeInMillis(), endCal.getTimeInMillis());
    }

    /**
     * Returns the end of week day (Calendar constant) given the start of week day
     *
     * @param startOfWeekDay Calendar constant for the start day of the week (e.g., Calendar.MONDAY)
     * @return Calendar constant for the end day of the week
     */
    public static int getEndOfWeekDay(int startOfWeekDay) {
        switch (startOfWeekDay) {
            case Calendar.MONDAY:
                return Calendar.SUNDAY;
            case Calendar.SUNDAY:
                return Calendar.SATURDAY;
            case Calendar.TUESDAY:
                return Calendar.MONDAY;
            case Calendar.WEDNESDAY:
                return Calendar.TUESDAY;
            case Calendar.THURSDAY:
                return Calendar.WEDNESDAY;
            case Calendar.FRIDAY:
                return Calendar.THURSDAY;
            case Calendar.SATURDAY:
                return Calendar.FRIDAY;
            default:
                throw new IllegalArgumentException("Invalid day of week: " + startOfWeekDay);
        }
    }
}
