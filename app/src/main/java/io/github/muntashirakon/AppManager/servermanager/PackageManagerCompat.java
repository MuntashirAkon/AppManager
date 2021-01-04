/*
 * Copyright (C) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.servermanager;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.RemoteException;
import android.permission.IPermissionManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.misc.UserIdInt;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.SYNCHRONOUS;

public final class PackageManagerCompat {
    @IntDef({
            COMPONENT_ENABLED_STATE_DEFAULT,
            COMPONENT_ENABLED_STATE_ENABLED,
            COMPONENT_ENABLED_STATE_DISABLED,
            COMPONENT_ENABLED_STATE_DISABLED_USER,
            COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EnabledState {}

    @IntDef(flag = true, value = {
            DONT_KILL_APP,
            SYNCHRONOUS
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EnabledFlags {}

    public static List<PackageInfo> getInstalledPackages(int flags, @UserIdInt int userHandle) throws RemoteException {
        return AppManager.getIPackageManager().getInstalledPackages(flags, userHandle).getList();
    }

    @NonNull
    public static PackageInfo getPackageInfo(String packageName, int flags, @UserIdInt int userHandle) throws RemoteException {
        return AppManager.getIPackageManager().getPackageInfo(packageName, flags, userHandle);
    }

    @NonNull
    public static ApplicationInfo getApplicationInfo(String packageName, int flags, @UserIdInt int userHandle) throws RemoteException {
        return AppManager.getIPackageManager().getApplicationInfo(packageName, flags, userHandle);
    }

    public static void setComponentEnabledSetting(ComponentName componentName,
                                                  @EnabledState int newState,
                                                  @EnabledFlags int flags,
                                                  @UserIdInt int userId)
            throws RemoteException {
        AppManager.getIPackageManager().setComponentEnabledSetting(componentName, newState, flags, userId);
    }

    public static void setApplicationEnabledSetting(String packageName, @EnabledState int newState,
                                                    @EnabledFlags int flags, @UserIdInt int userId)
            throws RemoteException {
        AppManager.getIPackageManager().setApplicationEnabledSetting(packageName, newState, flags, userId, null);
    }

    @SuppressWarnings("deprecation")
    public static void grantPermission(String packageName, String permissionName, int userId)
            throws RemoteException {
        IPackageManager pm = AppManager.getIPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            IPermissionManager permissionManager = IPermissionManager.Stub.asInterface(ProxyBinder.getService("permissionmgr"));
            permissionManager.grantRuntimePermission(packageName, permissionName, userId);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.grantRuntimePermission(packageName, permissionName, userId);
        } else {
            pm.grantPermission(packageName, permissionName);
        }
    }

    @SuppressWarnings("deprecation")
    public static void revokePermission(String packageName, String permissionName, int userId)
            throws RemoteException {
        IPackageManager pm = AppManager.getIPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            IPermissionManager permissionManager = IPermissionManager.Stub.asInterface(ProxyBinder.getService("permissionmgr"));
            permissionManager.revokeRuntimePermission(packageName, permissionName, userId, null);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.revokeRuntimePermission(packageName, permissionName, userId);
        } else {
            pm.revokePermission(packageName, permissionName);
        }
    }


}
