// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.magisk;

import android.content.pm.PackageInfo;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;

import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.settings.Ops;

import static io.github.muntashirakon.AppManager.magisk.MagiskUtils.ISOLATED_MAGIC;

@AnyThread
public class MagiskHide {
    /**
     * Whether MagiskHide is available.
     */
    public static boolean available() {
        return Ops.isRoot() && Runner.runCommand(new String[]{"command", "-v", "magiskhide"}).isSuccessful();
    }

    /**
     * Enable MagiskHide if it is not already enabled.
     *
     * @return {@code true} iff MagiskHide is enabled.
     */
    public static boolean enableIfNotAlready(boolean forceEnable) {
        // Check MagiskHide status
        if (!Runner.runCommand(new String[]{"magiskhide", "status"}).isSuccessful()) {
            // Enable MagiskHide
            if (forceEnable) {
                return Runner.runCommand(new String[]{"magiskhide", "enable"}).isSuccessful();
            } else return false;
        } else return true;
    }

    public static boolean apply(@NonNull MagiskProcess magiskProcess) {
        String packageName = magiskProcess.isIsolatedProcess() && !magiskProcess.isAppZygote() ? ISOLATED_MAGIC
                : magiskProcess.packageName;
        if (magiskProcess.isEnabled()) {
            return add(packageName, magiskProcess.name);
        }
        return remove(packageName, magiskProcess.name);
    }

    private static boolean add(String packageName, String processName) {
        // Check MagiskHide status
        if (!enableIfNotAlready(true)) return false;
        // MagiskHide is enabled, enable hide for the package
        return Runner.runCommand(new String[]{"magiskhide", "add", packageName, processName}).isSuccessful();
    }

    private static boolean remove(String packageName, String processName) {
        // Disable hide for the package (don't need to check for status)
        return Runner.runCommand(new String[]{"magiskhide", "rm", packageName, processName}).isSuccessful();
    }

    @NonNull
    public static List<MagiskProcess> getProcesses(@NonNull PackageInfo packageInfo) {
        return MagiskUtils.getProcesses(packageInfo, getProcesses(packageInfo.packageName));
    }

    @NonNull
    public static Collection<String> getProcesses(@NonNull String packageName) {
        Runner.Result result = Runner.runCommand(new String[]{"magiskhide", "ls"});
        return MagiskUtils.parseProcesses(packageName, result);
    }
}
