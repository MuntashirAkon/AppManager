/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.usage;

import android.os.SystemClock;
import android.util.Pair;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

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
    public static Pair<Long, Long> getTimeInterval(@IntervalType int sort) {
        Pair<Long, Long> interval;
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
    private static Pair<Long, Long> getSinceLastBoot() {
        return new Pair<>(SystemClock.elapsedRealtime(), System.currentTimeMillis());
    }

    @NonNull
    private static Pair<Long, Long> getToday() {
        long timeNow = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new Pair<>(cal.getTimeInMillis(), timeNow);
    }

    @NonNull
    private static Pair<Long, Long> getYesterday() {
        long timeNow = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeNow - ONE_DAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        return new Pair<>(start, Math.min(start + ONE_DAY, timeNow));
    }

    @NonNull
    private static Pair<Long, Long> getWeeklyInterval() {
        long timeEnd = System.currentTimeMillis();
        long timeStart = timeEnd - TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS);
        return new Pair<>(timeStart, timeEnd);
    }

}
