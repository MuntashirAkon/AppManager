// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

public class TimeInterval extends Pair<Long, Long> {
    private final int mIntervalType;

    public TimeInterval(int intervalType, long begin, long end) {
        super(begin, end);
        mIntervalType = intervalType;
    }

    public TimeInterval(long begin, long end) {
        super(begin, end);
        mIntervalType = IntervalType.INTERVAL_DAILY;
    }

    public int getIntervalType() {
        return mIntervalType;
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
