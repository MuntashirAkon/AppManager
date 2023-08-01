// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.os.Build;
import android.os.IDeviceIdleController;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.utils.ExUtils;

public final class DeviceIdleManagerCompat {
    @RequiresPermission(ManifestCompat.permission.DEVICE_POWER)
    public static boolean disableBatteryOptimization(@NonNull String packageName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                getDeviceIdleController().addPowerSaveWhitelistApp(packageName);
                return true; // returns true when the package isn't installed
            } catch (RemoteException e) {
                ExUtils.rethrowFromSystemServer(e);
            }
        }
        return false;
    }

    @RequiresPermission(ManifestCompat.permission.DEVICE_POWER)
    public static boolean enableBatteryOptimization(@NonNull String packageName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                getDeviceIdleController().removePowerSaveWhitelistApp(packageName);
                return true;
            } catch (RemoteException e) {
                ExUtils.rethrowFromSystemServer(e);
            } catch (UnsupportedOperationException e) {
                // System whitelisted app
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean isBatteryOptimizedApp(@NonNull String packageName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                IDeviceIdleController controller = getDeviceIdleController();
                return !controller.isPowerSaveWhitelistExceptIdleApp(packageName) &&
                        !controller.isPowerSaveWhitelistApp(packageName);
            } catch (RemoteException e) {
                ExUtils.rethrowFromSystemServer(e);
            }
        }
        // Not supported
        return true;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static IDeviceIdleController getDeviceIdleController() {
        return IDeviceIdleController.Stub.asInterface(ProxyBinder.getService("deviceidle"));
    }
}
