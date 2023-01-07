// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.proc;

import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Used for accessing contents from /proc/$PID/statm. See Table 1-3 in <a href="https://www.kernel.org/doc/Documentation/filesystems/proc.txt">The /proc filesystem</a>
 */
public final class ProcMemStat {
    public static final int MEM_STAT_SIZE = 0;
    public static final int MEM_STAT_RESIDENT = 1;
    public static final int MEM_STAT_SHARED = 2;
    public static final int MEM_STAT_TRS = 3;
    public static final int MEM_STAT_LRS = 4;
    public static final int MEM_STAT_DRS = 5;
    public static final int MEM_STAT_DT = 6;

    private static final int MEM_STAT_COUNT = 7;

    @NonNull
    public static ProcMemStat parse(@NonNull String data) {
        String[] result = data.split("\\s");
        if (result.length != MEM_STAT_COUNT) {
            Log.w(ProcMemStat.class.getSimpleName(), "Field counts did not match, expected: " + MEM_STAT_COUNT
                    + ", actual: " + result.length);
        }
        return new ProcMemStat(result);
    }

    @NonNull
    private final String[] mMemStat;

    private ProcMemStat(@NonNull String[] memStat) {
        mMemStat = memStat;
    }

    public long getLong(int index) {
        return Long.decode(mMemStat[index]);
    }
}