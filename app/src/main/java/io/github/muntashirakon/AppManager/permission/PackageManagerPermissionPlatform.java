// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import android.annotation.UserIdInt;
import android.content.pm.ApplicationInfo;
import android.content.pm.PermissionInfo;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.BroadcastUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * Platform-facing operations used by {@link PackageManagerPermissionController}.
 */
interface PackageManagerPermissionPlatform {
    boolean canModifyPermissions();

    int checkPermission(@NonNull String permissionName, @NonNull String packageName,
                        @UserIdInt int userId) throws RemoteException;

    @Nullable
    PermissionInfo getPermissionInfo(@NonNull String permissionName, @NonNull String packageName)
            throws RemoteException;

    int getPermissionFlags(@NonNull String permissionName, @NonNull String packageName,
                           @UserIdInt int userId);

    void grantPermission(@NonNull String packageName, @NonNull String permissionName,
                         @UserIdInt int userId) throws RemoteException;

    void revokePermission(@NonNull String packageName, @NonNull String permissionName,
                          @UserIdInt int userId, @Nullable String reason) throws RemoteException;

    boolean getCheckAdjustPolicyFlagPermission(@NonNull ApplicationInfo applicationInfo);

    void updatePermissionFlags(@NonNull String permissionName, @NonNull String packageName,
                               int flagMask, int flagValues, boolean checkAdjustPolicy,
                               @UserIdInt int userId) throws RemoteException;

    @UserIdInt
    int getCurrentUserId();

    void onPackageAltered(@NonNull String packageName);
}

final class FrameworkPackageManagerPermissionPlatform implements PackageManagerPermissionPlatform {
    @Override
    public boolean canModifyPermissions() {
        return SelfPermissions.canModifyPermissions();
    }

    @Override
    public int checkPermission(@NonNull String permissionName, @NonNull String packageName,
                               int userId) {
        return PermissionCompat.checkPermission(permissionName, packageName, userId);
    }

    @Nullable
    @Override
    public PermissionInfo getPermissionInfo(@NonNull String permissionName,
                                            @NonNull String packageName) throws RemoteException {
        return PermissionCompat.getPermissionInfo(permissionName, packageName, 0);
    }

    @Override
    public int getPermissionFlags(@NonNull String permissionName, @NonNull String packageName,
                                  int userId) {
        return PermissionCompat.getPermissionFlags(permissionName, packageName, userId);
    }

    @Override
    public void grantPermission(@NonNull String packageName, @NonNull String permissionName,
                                int userId) throws RemoteException {
        PermissionCompat.grantPermission(packageName, permissionName, userId);
    }

    @Override
    public void revokePermission(@NonNull String packageName, @NonNull String permissionName,
                                 int userId, @Nullable String reason) throws RemoteException {
        if (reason == null) {
            PermissionCompat.revokePermission(packageName, permissionName, userId);
        } else {
            PermissionCompat.revokePermission(packageName, permissionName, userId, reason);
        }
    }

    @Override
    public boolean getCheckAdjustPolicyFlagPermission(@NonNull ApplicationInfo applicationInfo) {
        return PermissionCompat.getCheckAdjustPolicyFlagPermission(applicationInfo);
    }

    @Override
    public void updatePermissionFlags(@NonNull String permissionName, @NonNull String packageName,
                                      int flagMask, int flagValues, boolean checkAdjustPolicy,
                                      int userId) throws RemoteException {
        PermissionCompat.updatePermissionFlags(permissionName, packageName, flagMask, flagValues,
                checkAdjustPolicy, userId);
    }

    @Override
    public int getCurrentUserId() {
        return UserHandleHidden.myUserId();
    }

    @Override
    public void onPackageAltered(@NonNull String packageName) {
        BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), new String[]{packageName});
    }
}
