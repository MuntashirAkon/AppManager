package io.github.muntashirakon.AppManager.usage;

import android.os.SystemClock;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.utils.Tuple;

public final class UsageUtils {
    public static final int ONE_DAY = 86400000;

    @IntDef(value = {
            USAGE_TODAY,
            USAGE_YESTERDAY,
            USAGE_WEEKLY,
            USAGE_LAST_BOOT
    })
    public @interface IntervalType {}
    public static final int USAGE_TODAY = 0;
    public static final int USAGE_YESTERDAY = 1;
    public static final int USAGE_WEEKLY = 2;
    public static final int USAGE_LAST_BOOT = 5;

    @NonNull
    public static Tuple<Long, Long> getTimeInterval(@IntervalType int sort) {
        Tuple<Long, Long> interval;
        switch (sort) {
            case USAGE_YESTERDAY:
                interval = getYesterday();
                break;
            case USAGE_WEEKLY:
                interval = getWeeklyInterval();
                break;
            case USAGE_LAST_BOOT:
                interval = getSinceLastBoot();
                break;
            case USAGE_TODAY:
            default:
                interval = getToday();
        }
        return interval;
    }

    @NonNull
    private static Tuple<Long, Long> getSinceLastBoot() {
        return new Tuple<>(SystemClock.elapsedRealtime(), System.currentTimeMillis());
    }

    @NonNull
    private static Tuple<Long, Long> getToday() {
        long timeNow = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new Tuple<>(cal.getTimeInMillis(), timeNow);
    }

    @NonNull
    private static Tuple<Long, Long> getYesterday() {
        long timeNow = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeNow - ONE_DAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        return new Tuple<>(start, Math.min(start + ONE_DAY, timeNow));
    }

    @NonNull
    private static Tuple<Long, Long> getWeeklyInterval() {
        long timeEnd = System.currentTimeMillis();
        long timeStart = timeEnd - TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS);
        return new Tuple<>(timeStart, timeEnd);
    }

}
