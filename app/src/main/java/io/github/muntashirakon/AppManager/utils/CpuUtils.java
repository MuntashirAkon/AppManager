// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.Keep;

public class CpuUtils {
    static {
        System.loadLibrary("am");
    }

    @Keep
    public static native long getClockTicksPerSecond();

    public static PowerManager.WakeLock getPartialWakeLock(String tagPostfix) {
        PowerManager pm = (PowerManager) ContextUtils.getContext().getSystemService(Context.POWER_SERVICE);
        return pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AppManager::" + tagPostfix);
    }
}
