// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import android.content.pm.PackageInfo;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PermissionInfoCompat;

import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.appops.OpEntry;
import io.github.muntashirakon.AppManager.permission.PermUtils;
import io.github.muntashirakon.AppManager.permission.Permission;

public class AppDetailsAppOpItem extends AppDetailsItem<OpEntry> {
    @Nullable
    public final Permission permission;
    @Nullable
    public final PermissionInfo permissionInfo;
    public final boolean isDangerous;

    public AppDetailsAppOpItem(@NonNull OpEntry opEntry) {
        super(opEntry);
        permissionInfo = null;
        permission = null;
        isDangerous = false;
    }

    public AppDetailsAppOpItem(@NonNull OpEntry opEntry, @NonNull PermissionInfo permissionInfo, boolean isGranted,
                               int permissionFlags) {
        super(opEntry);
        this.permissionInfo = permissionInfo;
        permission = new Permission(permissionInfo.name, isGranted, opEntry.getOp(),
                opEntry.getMode() == AppOpsManager.MODE_ALLOWED, permissionFlags);
        isDangerous = PermissionInfoCompat.getProtection(permissionInfo) == PermissionInfo.PROTECTION_DANGEROUS;
    }

    public boolean isAllowed() {
        boolean isAllowed = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isAllowed = vanillaItem.getMode() == AppOpsManager.MODE_FOREGROUND;
        }
        isAllowed |= vanillaItem.getMode() == AppOpsManager.MODE_ALLOWED;
        return isAllowed;
    }

    /**
     * Allow the app op.
     *
     * <p>This also automatically grants the permission associated with the app op.
     *
     * @return {@code true} iff the app op could be allowed.
     */
    @WorkerThread
    public boolean allowAppOp(@NonNull PackageInfo packageInfo, @NonNull AppOpsService appOpsService)
            throws RemoteException {
        boolean isSuccessful;
        if (permission != null) {
            isSuccessful = PermUtils.grantPermission(packageInfo, permission, appOpsService, true, true);
        } else {
            isSuccessful = PermUtils.allowAppOp(appOpsService, vanillaItem.getOp(), packageInfo.packageName,
                    packageInfo.applicationInfo.uid);
        }
        vanillaItem.setMode(appOpsService.checkOperation(vanillaItem.getOp(), packageInfo.applicationInfo.uid,
                packageInfo.packageName));
        return isSuccessful;
    }

    /**
     * Disallow the app op.
     *
     * <p>This also revokes the permission associated with the app op.
     *
     * @return {@code true} iff the app op could be disallowed.
     */
    @WorkerThread
    public boolean disallowAppOp(@NonNull PackageInfo packageInfo, AppOpsService appOpsService)
            throws RemoteException {
        boolean isSuccessful;
        if (permission != null) {
            isSuccessful = PermUtils.revokePermission(packageInfo, permission, appOpsService, true);
        } else {
            isSuccessful = PermUtils.disallowAppOp(appOpsService, vanillaItem.getOp(), packageInfo.packageName,
                    packageInfo.applicationInfo.uid);
        }
        vanillaItem.setMode(appOpsService.checkOperation(vanillaItem.getOp(), packageInfo.applicationInfo.uid,
                packageInfo.packageName));
        return isSuccessful;
    }

    /**
     * Set mode for app op.
     *
     * <p>This also grants/revoke the permission associated with the app op.
     *
     * @return {@code true} iff the app op could be set.
     */
    @WorkerThread
    public boolean setAppOp(@NonNull PackageInfo packageInfo, AppOpsService appOpsService, @AppOpsManager.Mode int mode)
            throws RemoteException {
        if (permission != null) {
            boolean isAllowed = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isAllowed = vanillaItem.getMode() == AppOpsManager.MODE_FOREGROUND;
            }
            isAllowed |= vanillaItem.getMode() == AppOpsManager.MODE_ALLOWED;
            if (isAllowed) {
                PermUtils.grantPermission(packageInfo, permission, appOpsService, true, true);
            } else {
                PermUtils.revokePermission(packageInfo, permission, appOpsService, true);
            }
        }
        boolean isSuccessful = PermUtils.setAppOpMode(appOpsService, vanillaItem.getOp(), packageInfo.packageName,
                packageInfo.applicationInfo.uid, mode);
        if (isSuccessful) {
            vanillaItem.setMode(mode);
        }
        return isSuccessful;
    }
}
