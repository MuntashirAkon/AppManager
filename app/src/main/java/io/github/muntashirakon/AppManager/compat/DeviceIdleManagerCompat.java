// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.os.Build;
import android.os.IDeviceIdleController;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;

public final class DeviceIdleManagerCompat {
    @RequiresPermission(ManifestCompat.permission.DEVICE_POWER)
    public static void disableBatteryOptimization(@NonNull String packageName) throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getDeviceIdleController().addPowerSaveWhitelistApp(packageName);
        }
    }

    @RequiresPermission(ManifestCompat.permission.DEVICE_POWER)
    public static void enableBatteryOptimization(@NonNull String packageName) throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getDeviceIdleController().removePowerSaveWhitelistApp(packageName);
        }
    }

    public static boolean isBatteryOptimizedApp(@NonNull String packageName) throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            IDeviceIdleController controller = getDeviceIdleController();
            return !controller.isPowerSaveWhitelistExceptIdleApp(packageName) &&
                    !controller.isPowerSaveWhitelistApp(packageName);
        }
        // Not supported
        return true;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static IDeviceIdleController getDeviceIdleController() {
        return IDeviceIdleController.Stub.asInterface(ProxyBinder.getService("deviceidle"));
    }
}
