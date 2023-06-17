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
public class MagiskDenyList {
    /**
     * Whether Magisk DenyList is available.
     */
    public static boolean available() {
        return Ops.isRoot() && Runner.runCommand(new String[]{"magisk", "--denylist", "ls"}).isSuccessful();
    }

    /**
     * Enable Magisk DenyList if it is not already enabled.
     *
     * @return {@code true} iff Magisk DenyList is enabled.
     */
    public static boolean enableIfNotAlready(boolean forceEnable) {
        // Check DenyList status
        if (!Runner.runCommand(new String[]{"magisk", "--denylist", "status"}).isSuccessful()) {
            // Enable DenyList
            if (forceEnable) {
                return Runner.runCommand(new String[]{"magisk", "--denylist", "enable"}).isSuccessful();
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

    public static boolean add(String packageName, String processName) {
        // Check DenyList status
        if (!enableIfNotAlready(true)) return false;
        // DenyList is enabled, enable hide for the package
        return Runner.runCommand(new String[]{"magisk", "--denylist", "add", packageName, processName}).isSuccessful();
    }

    public static boolean remove(String packageName, String processName) {
        // Disable hide for the package (don't need to check for status)
        return Runner.runCommand(new String[]{"magisk", "--denylist", "rm", packageName, processName}).isSuccessful();
    }

    @NonNull
    public static List<MagiskProcess> getProcesses(@NonNull PackageInfo packageInfo) {
        return MagiskUtils.getProcesses(packageInfo, getProcesses(packageInfo.packageName));
    }

    @NonNull
    public static Collection<String> getProcesses(@NonNull String packageName) {
        Runner.Result result = Runner.runCommand(new String[]{"magisk", "--denylist", "ls"});
        return MagiskUtils.parseProcesses(packageName, result);
    }

}
