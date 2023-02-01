// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.Keep;

public class CpuUtils {
    static {
        System.loadLibrary("am");
    }

    @Keep
    public static native long getClockTicksPerSecond();
}
