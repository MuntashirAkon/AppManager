// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

import io.github.muntashirakon.AppManager.runner.Runner;

public class RestartUtils {
    @IntDef({
            RESTART_NORMAL,
            RESTART_RECOVERY,
            RESTART_BOOTLOADER,
            RESTART_USERSPACE,
            RESTART_DOWNLOAD,
            RESTART_EDL,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RestartType {
    }

    public static final int RESTART_NORMAL = 0;
    public static final int RESTART_RECOVERY = 1;
    public static final int RESTART_BOOTLOADER = 2;
    public static final int RESTART_USERSPACE = 3;
    public static final int RESTART_DOWNLOAD = 4;
    public static final int RESTART_EDL = 5;

    private static final String[] RESTART_REASON = new String[]{
            // Mapped to above
            "", "recovery", "bootloader", "userspace", "download", "edl"
    };

    public static void restart(@RestartType int type) {
        restart(RESTART_REASON[type]);
    }

    private static void restart(String reason) {
        // https://github.com/topjohnwu/Magisk/blob/5512917ec123e815dec1e3af871357f760acc1f3/app/src/main/java/com/topjohnwu/magisk/ktx/XSU.kt
        if (RESTART_REASON[RESTART_RECOVERY].equals(reason)) {
            // KEYCODE_POWER = 26, hide incorrect "Factory data reset" message
            Runner.runCommand(new String[]{"/system/bin/input", "keyevent", "26"});
        }
        String cmd = String.format(Locale.ROOT, "/system/bin/svc power reboot %s || /system/bin/reboot %s", reason, reason);
        Runner.runCommand(cmd);
    }
}
