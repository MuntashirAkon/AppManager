/*
 * Copyright (c) 2021 Muntashir Al-Islam
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

import android.annotation.UserIdInt;
import android.content.pm.IPackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.permission.IPermissionManager;

import androidx.annotation.RequiresApi;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;

public class PermissionCompat {
    @RequiresApi(Build.VERSION_CODES.M)
    public static int getPermissionFlags(String permissionName, String packageName, @UserIdInt int userId)
            throws RemoteException {
        IPackageManager pm = AppManager.getIPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            IPermissionManager permissionManager = getPermissionManager();
            return permissionManager.getPermissionFlags(packageName, permissionName, userId);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return pm.getPermissionFlags(permissionName, packageName, userId);
        } else return 0;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    public static void updatePermissionFlags(String permissionName, String packageName,
                                             int flagMask, int flagValues,
                                             boolean checkAdjustPolicyFlagPermission,
                                             @UserIdInt int userId)
            throws RemoteException {
        IPackageManager pm = AppManager.getIPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            IPermissionManager permissionManager = getPermissionManager();
            permissionManager.updatePermissionFlags(permissionName, packageName, flagMask, flagValues,
                    checkAdjustPolicyFlagPermission, userId);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pm.updatePermissionFlags(permissionName, packageName, flagMask, flagValues,
                    checkAdjustPolicyFlagPermission, userId);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.updatePermissionFlags(permissionName, packageName, flagMask, flagValues, userId);
        }
    }

    @SuppressWarnings("deprecation")
    public static void grantPermission(String packageName, String permissionName, @UserIdInt int userId)
            throws RemoteException {
        IPackageManager pm = AppManager.getIPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            IPermissionManager permissionManager = getPermissionManager();
            permissionManager.grantRuntimePermission(packageName, permissionName, userId);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.grantRuntimePermission(packageName, permissionName, userId);
        } else {
            pm.grantPermission(packageName, permissionName);
        }
    }

    @SuppressWarnings("deprecation")
    public static void revokePermission(String packageName, String permissionName, @UserIdInt int userId)
            throws RemoteException {
        IPackageManager pm = AppManager.getIPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            IPermissionManager permissionManager = getPermissionManager();
            permissionManager.revokeRuntimePermission(packageName, permissionName, userId, null);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.revokeRuntimePermission(packageName, permissionName, userId);
        } else {
            pm.revokePermission(packageName, permissionName);
        }
    }

    public static IPermissionManager getPermissionManager() {
        return IPermissionManager.Stub.asInterface(ProxyBinder.getService("permissionmgr"));
    }
}
