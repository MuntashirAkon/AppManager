// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.os.SystemClock;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public final class UsageUtils {
    public static final int ONE_DAY = 86400000;

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

    public static class TimeInterval extends Pair<Long, Long> {
        public TimeInterval(Long begin, Long end) {
            super(begin, end);
        }

        public long getStartTime() {
            return first;
        }

        public long getEndTime() {
            return second;
        }

        public long getDuration() {
            return second - first + 1;
        }

        @NonNull
        @Override
        public String toString() {
            return "TimeInterval{" +
                    "startTime=" + first +
                    ", endTime=" + second +
                    '}';
        }
    }

    @NonNull
    public static TimeInterval getTimeInterval(@IntervalType int sort) {
        switch (sort) {
            case USAGE_YESTERDAY:
                return getYesterday();
            case USAGE_WEEKLY:
                return getWeeklyInterval();
            case USAGE_LAST_BOOT:
                return getSinceLastBoot();
            case USAGE_TODAY:
            default:
                return getToday();
        }
    }

    @NonNull
    private static TimeInterval getSinceLastBoot() {
        return new TimeInterval(SystemClock.elapsedRealtime(), System.currentTimeMillis());
    }

    @NonNull
    private static TimeInterval getToday() {
        long timeNow = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new TimeInterval(cal.getTimeInMillis(), timeNow);
    }

    @NonNull
    private static TimeInterval getYesterday() {
        long timeNow = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeNow - ONE_DAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        return new TimeInterval(start, Math.min(start + ONE_DAY, timeNow));
    }

    @NonNull
    private static TimeInterval getWeeklyInterval() {
        long timeEnd = System.currentTimeMillis();
        long timeStart = timeEnd - TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS);
        return new TimeInterval(timeStart, timeEnd);
    }
}
