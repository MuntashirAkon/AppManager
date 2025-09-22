// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

public class UsageDataProcessor {
    private static final long HOUR_IN_MILLIS = 60 * 60 * 1000L;
    private static final long DAY_IN_MILLIS = 24L * 60 * 60 * 1000;

    public static void updateChartWithAppUsage(@NonNull BarChartView chart,
                                               @NonNull List<PackageUsageInfo.Entry> events,
                                               @IntervalType int interval, long targetDate) {
        switch (interval) {
            case IntervalType.INTERVAL_WEEKLY:
                updateChartWithDailyAppUsage(chart, events, targetDate);
                break;
            case IntervalType.INTERVAL_DAILY:
            default:
                updateChartWithHourlyAppUsage(chart, events, targetDate);
        }
    }

    public static void updateChartWithHourlyAppUsage(BarChartView chart, List<PackageUsageInfo.Entry> events, long targetDate) {
        float[] hourlyMinutes = convertToMinutes(groupIntoHourlyBucketsForDay(events, targetDate));

        List<Float> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        String[] timeLabels = getHourLabels();

        for (int i = 0; i < Math.min(hourlyMinutes.length, timeLabels.length); i++) {
            values.add(hourlyMinutes[i]);
            labels.add(timeLabels[i]);
        }

        chart.setManualYAxisRange(0f, nextDivisibleBy4(Collections.max(values)));
        chart.setYAxisFormat("%.0f min");
        chart.setData(values, labels);
        chart.setTooltipListener((barIndex, value, label) ->
                String.format("%s • %.0f minutes", label, value));
    }

    public static void updateChartWithDailyAppUsage(BarChartView chart, List<PackageUsageInfo.Entry> events, long targetDate) {
        long[] dailyMillis = groupIntoDailyBucketsForWeek(events, targetDate);
        boolean displayInHours = ArrayUtils.max(dailyMillis) > 2 * HOUR_IN_MILLIS;
        float[] dailyUsage;
        if (displayInHours) {
            dailyUsage = convertToHours(dailyMillis);
        } else {
            dailyUsage = convertToMinutes(dailyMillis);
        }

        List<Float> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        String[] weekDayLabels = getWeekDayLabels();

        for (int i = 0; i < Math.min(dailyUsage.length, weekDayLabels.length); i++) {
            values.add(dailyUsage[i]);
            labels.add(weekDayLabels[i]);
        }

        chart.setManualYAxisRange(0f, nextDivisibleBy4(Collections.max(values)));
        chart.setYAxisFormat(displayInHours ? "%.0f hr" : "%.0f min");
        chart.setData(values, labels);
        chart.setTooltipListener((barIndex, value, label) ->
                String.format(displayInHours ? "%s • %.1f hours" : "%s • %.1f minutes", label, value));
    }

    /**
     * Groups events into hourly buckets for a specific day
     */
    @NonNull
    public static long[] groupIntoHourlyBucketsForDay(@NonNull List<PackageUsageInfo.Entry> events, long targetDate) {
        TimeInterval interval = UsageUtils.getDayBounds(targetDate);
        long dayStartTimestamp = interval.getStartTime();
        long dayEndTimestamp = interval.getEndTime();
        long[] hourlyDurations = new long[24];

        for (PackageUsageInfo.Entry event : events) {
            // Only process events that overlap with this day
            if (event.endTime < dayStartTimestamp || event.startTime > dayEndTimestamp) {
                continue; // Event doesn't overlap with this day
            }

            // Clip event to day boundaries
            long clippedStart = Math.max(event.startTime, dayStartTimestamp);
            long clippedEnd = Math.min(event.endTime, dayEndTimestamp);

            distributeClippedEventAcrossHours(clippedStart, clippedEnd, dayStartTimestamp, hourlyDurations);
        }

        return hourlyDurations;
    }

    /**
     * Distributes a clipped event (within day boundaries) across hourly buckets
     */
    private static void distributeClippedEventAcrossHours(long startTime, long endTime, long dayStart,
                                                          @NonNull long[] hourlyDurations) {
        long currentTime = startTime;

        while (currentTime <= endTime) {
            // Calculate hour bucket based on offset from day start
            int hourBucket = (int) ((currentTime - dayStart) / HOUR_IN_MILLIS);
            hourBucket = Math.min(hourBucket, 23); // Ensure we don't exceed hour 23

            // Calculate end of current hour
            long hourEnd = dayStart + ((hourBucket + 1) * HOUR_IN_MILLIS) - 1;

            // Calculate segment duration
            long segmentEnd = Math.min(endTime, hourEnd);
            long segmentDuration = (segmentEnd - currentTime) + 1;

            // Add to bucket
            hourlyDurations[hourBucket] += segmentDuration;

            // Move to next hour
            currentTime = hourEnd + 1;
        }
    }

    /**
     * Group events into 7 daily buckets ending at periodEnd (inclusive).
     */
    @NonNull
    public static long[] groupIntoDailyBucketsForWeek(@NonNull List<PackageUsageInfo.Entry> events, long targetDate) {
        TimeInterval interval = UsageUtils.getWeekBounds(targetDate);
        int numDays = 7;
        long[] msBuckets = new long[numDays];
        long periodStart = interval.getStartTime();
        long periodEnd = interval.getEndTime();

        for (PackageUsageInfo.Entry e : events) {
            // Clip event to [periodStart, periodEnd]
            long start = Math.max(e.startTime, periodStart);
            long end = Math.min(e.endTime, periodEnd);
            if (start > end) continue;

            long current = start;
            while (current <= end) {
                int bucketIndex = (int) ((current - periodStart) / DAY_IN_MILLIS);
                bucketIndex = Math.min(bucketIndex, numDays - 1);

                long bucketEnd = periodStart + (bucketIndex + 1) * DAY_IN_MILLIS - 1;
                long segmentEnd = Math.min(end, bucketEnd);
                long segmentDur = (segmentEnd - current) + 1;
                msBuckets[bucketIndex] += segmentDur;

                current = bucketEnd + 1;
            }
        }
        return msBuckets;
    }

    @NonNull
    public static String[] getHourLabels() {
        boolean is24Hour = android.text.format.DateFormat.is24HourFormat(ContextUtils.getContext());
        if (is24Hour) {
            return new String[]{
                    "00:00", "01:00", "02:00", "03:00", "04:00", "05:00", "06:00", "07:00", "08:00",
                    "09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00", "17:00",
                    "18:00", "19:00", "20:00", "21:00", "22:00", "23:00"
            };

        }
        return new String[]{
                "12 AM", "1 AM", "2 AM", "3 AM", "4 AM", "5 AM", "6 AM", "7 AM", "8 AM", "9 AM",
                "10 AM", "11 AM", "12 PM", "1 PM", "2 PM", "3 PM", "4 PM", "5 PM", "6 PM", "7 PM",
                "8 PM", "9 PM", "10 PM", "11 PM"
        };
    }

    @NonNull
    public static String[] getWeekDayLabels() {
        String[] labels;
        int firstDayOfWeek = Calendar.getInstance(TimeZone.getDefault()).getFirstDayOfWeek();
        if (firstDayOfWeek == Calendar.MONDAY) {
            labels = new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        } else if (firstDayOfWeek == Calendar.TUESDAY) {
            labels = new String[]{"Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "Mon"};
        } else if (firstDayOfWeek == Calendar.WEDNESDAY) {
            labels = new String[]{"Wed", "Thu", "Fri", "Sat", "Sun", "Mon", "Tue"};
        } else if (firstDayOfWeek == Calendar.THURSDAY) {
            labels = new String[]{"Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed"};
        } else if (firstDayOfWeek == Calendar.FRIDAY) {
            labels = new String[]{"Fri", "Sat", "Sun", "Mon", "Tue", "Wed", "Thu"};
        } else if (firstDayOfWeek == Calendar.SATURDAY) {
            labels = new String[]{"Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri"};
        } else {// if (firstDayOfWeek == Calendar.SUNDAY) {
            labels = new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        }
        return labels;
    }

    /**
     * Find the next float value > maxValue that is divisible by 4.
     * <p>
     * Note: This only works for positive data.
     */
    public static float nextDivisibleBy4(float maxValue) {
        float ceil = (float) Math.ceil(maxValue);
        int multiple = ((int) ceil + 3) / 4;
        float upperBound = multiple * 4;
        if (upperBound <= maxValue) {
            upperBound += 4;
        }
        return upperBound;
    }

    @NonNull
    public static float[] convertToMinutes(@NonNull long[] durationsMillis) {
        float[] minutes = new float[durationsMillis.length];
        for (int i = 0; i < durationsMillis.length; i++) {
            minutes[i] = durationsMillis[i] / 60_000f;
        }
        return minutes;
    }

    @NonNull
    public static float[] convertToHours(@NonNull long[] durationsMillis) {
        float[] hours = new float[durationsMillis.length];
        for (int i = 0; i < durationsMillis.length; i++) {
            hours[i] = durationsMillis[i] / 3_600_000f;
        }
        return hours;
    }
}
