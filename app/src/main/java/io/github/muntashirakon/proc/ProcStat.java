// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.proc;

import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Used for accessing contents from /proc/$PID/stat. See Table 1-4 in <a href="https://www.kernel.org/doc/Documentation/filesystems/proc.txt">The /proc filesystem</a>
 */
public final class ProcStat {
    public static final int STAT_PID = 0;
    public static final int STAT_TCOMM = 1;
    public static final int STAT_STATE = 2;
    public static final int STAT_PPID = 3;
    public static final int STAT_PGRP = 4;
    public static final int STAT_SID = 5;
    public static final int STAT_TTY_NR = 6;
    public static final int STAT_TTY_PGRP = 7;
    public static final int STAT_FLAGS = 8;
    public static final int STAT_MIN_FLT = 9;
    public static final int STAT_CMIN_FLT = 10;
    public static final int STAT_MAJ_FLT = 11;
    public static final int STAT_CMAJ_FLT = 12;
    public static final int STAT_UTIME = 13;
    public static final int STAT_STIME = 14;
    public static final int STAT_CUTIME = 15;
    public static final int STAT_CSTIME = 16;
    public static final int STAT_PRIORITY = 17;
    public static final int STAT_NICE = 18;
    public static final int STAT_NUM_THREADS = 19;
    public static final int STAT_IT_REAL_VALUE = 20;
    public static final int STAT_START_TIME = 21;
    public static final int STAT_VSIZE = 22;
    public static final int STAT_RSS = 23;
    public static final int STAT_RSSLIM = 24;
    public static final int STAT_START_CODE = 25;
    public static final int STAT_END_CODE = 26;
    public static final int STAT_START_STACK = 27;
    public static final int STAT_ESP = 28;
    public static final int STAT_EIP = 29;
    public static final int STAT_PENDING = 30;
    public static final int STAT_BLOCKED = 31;
    public static final int STAT_SIGIGN = 32;
    public static final int STAT_SIGCATCH = 33;
    public static final int STAT_PLACEHOLDER_0 = 34;
    public static final int STAT_PLACEHOLDER_1 = 35;
    public static final int STAT_PLACEHOLDER_2 = 36;
    public static final int STAT_EXIT_SIGNAL = 37;
    public static final int STAT_TASK_CPU = 38;
    public static final int STAT_RT_PRIORITY = 39;
    public static final int STAT_POLICY = 40;
    public static final int STAT_BLKIO_TICKS = 41;
    public static final int STAT_GTIME = 42;
    public static final int STAT_CGTIME = 43;
    public static final int STAT_START_DATA = 44;
    public static final int STAT_END_DATA = 45;
    public static final int STAT_START_BRK = 46;
    public static final int STAT_ARG_START = 47;
    public static final int STAT_ARG_END = 48;
    public static final int STAT_ENV_START = 49;
    public static final int STAT_ENV_END = 50;
    public static final int STAT_EXIT_CODE = 51;

    private static final int STAT_COUNT = 52;

    @NonNull
    public static ProcStat parse(@NonNull char[] data) {
        String[] result = new String[STAT_COUNT];
        // See: https://www.openwall.com/lists/oss-security/2022/12/21/6
        int bracketCount = 0;
        int fieldIndex = 0;
        int lastOffset = 0;
        for (int i = 0; i < data.length; ++i) {
            char c = data[i];
            if (c == '(') {
                ++bracketCount;
            } else if (c == ')') {
                --bracketCount;
            } else if (bracketCount == 0 && c == ' ') {
                result[fieldIndex] = new String(data, lastOffset, i - lastOffset);
                ++fieldIndex;
                lastOffset = i + 1;
            }
        }
        // Add last field
        result[fieldIndex] = new String(data, lastOffset, data.length - lastOffset);
        if (fieldIndex + 1 != STAT_COUNT) {
            Log.w(ProcStat.class.getSimpleName(), "Field counts did not match, expected: " + STAT_COUNT
                    + ", actual: " + (fieldIndex + 1));
        }
        return new ProcStat(result);
    }

    @NonNull
    private final String[] mStat;

    private ProcStat(@NonNull String[] stat) {
        mStat = stat;
    }

    @NonNull
    public String getString(int index) {
        return mStat[index];
    }

    public int getInteger(int index) {
        return Integer.decode(mStat[index]);
    }

    public long getLong(int index) {
        return Long.decode(mStat[index]);
    }
}
