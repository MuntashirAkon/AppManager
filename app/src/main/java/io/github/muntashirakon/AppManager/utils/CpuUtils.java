// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

public class CpuUtils {
    static {
        System.loadLibrary("am");
    }

    public static native long getClockTicksPerSecond();
}
