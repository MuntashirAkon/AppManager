// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.annotation.UserIdInt;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import java.io.IOException;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.utils.BinderShellExecutor;

@RequiresApi(Build.VERSION_CODES.P)
public final class SensorServiceCompat {
    @RequiresPermission(ManifestCompat.permission.MANAGE_SENSORS)
    public static boolean isSensorEnabled(@NonNull String packageName, @UserIdInt int userId) {
        String[] command;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            command = new String[]{"get-uid-state", packageName, "--user", String.valueOf(userId)};
        } else command = new String[]{"get-uid-state", packageName};
        try {
            BinderShellExecutor.ShellResult result = BinderShellExecutor.execute(getSensorService(), command);
            return "active".equals(result.getStdout().trim());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    @RequiresPermission(ManifestCompat.permission.MANAGE_SENSORS)
    public static void enableSensor(@NonNull String packageName, @UserIdInt int userId, boolean enable) throws IOException {
        String state = enable ? "active" : "idle";
        String[] command;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            command = new String[]{"set-uid-state", packageName, state, "--user", String.valueOf(userId)};
        } else command = new String[]{"set-uid-state", packageName, state};
        BinderShellExecutor.ShellResult result = BinderShellExecutor.execute(getSensorService(), command);
        if (result.getResultCode() != 0) {
            throw new IOException("Could not " + (enable ? "enable" : "disable") + " sensor.");
        }
    }

    @RequiresPermission(ManifestCompat.permission.MANAGE_SENSORS)
    public static void resetSensor(@NonNull String packageName, @UserIdInt int userId) throws IOException {
        String[] command;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            command = new String[]{"reset-uid-state", packageName, "--user", String.valueOf(userId)};
        } else command = new String[]{"reset-uid-state", packageName};
        BinderShellExecutor.ShellResult result = BinderShellExecutor.execute(getSensorService(), command);
        if (result.getResultCode() != 0) {
            throw new IOException("Could not reset sensor.");
        }
    }

    @NonNull
    private static IBinder getSensorService() {
        return ProxyBinder.getService("sensorservice");
    }
}
