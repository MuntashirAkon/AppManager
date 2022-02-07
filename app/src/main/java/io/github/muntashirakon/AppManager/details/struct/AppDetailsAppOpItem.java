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
import io.github.muntashirakon.AppManager.permission.DevelopmentPermission;
import io.github.muntashirakon.AppManager.permission.PermUtils;
import io.github.muntashirakon.AppManager.permission.Permission;
import io.github.muntashirakon.AppManager.permission.PermissionException;
import io.github.muntashirakon.AppManager.permission.ReadOnlyPermission;
import io.github.muntashirakon.AppManager.permission.RuntimePermission;

public class AppDetailsAppOpItem extends AppDetailsItem<OpEntry> {
    @Nullable
    public final Permission permission;
    @Nullable
    public final PermissionInfo permissionInfo;
    public final boolean isDangerous;
    public final boolean hasModifiablePermission;
    /**
     * Whether the permission is part of the app.
     */
    public final boolean appContainsPermission;

    public AppDetailsAppOpItem(@NonNull OpEntry opEntry) {
        super(opEntry);
        permissionInfo = null;
        permission = null;
        isDangerous = false;
        hasModifiablePermission = false;
        appContainsPermission = false;
    }

    public AppDetailsAppOpItem(@NonNull OpEntry opEntry, @NonNull PermissionInfo permissionInfo, boolean isGranted,
                               int permissionFlags, boolean appContainsPermission) {
        super(opEntry);
        this.permissionInfo = permissionInfo;
        this.appContainsPermission = appContainsPermission;
        isDangerous = PermissionInfoCompat.getProtection(permissionInfo) == PermissionInfo.PROTECTION_DANGEROUS;
        int protectionFlags = PermissionInfoCompat.getProtectionFlags(permissionInfo);
        if (isDangerous && PermUtils.systemSupportsRuntimePermissions()) {
            permission = new RuntimePermission(permissionInfo.name, isGranted, opEntry.getOp(), isAllowed(), permissionFlags);
        } else if ((protectionFlags & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
            permission = new DevelopmentPermission(permissionInfo.name, isGranted, opEntry.getOp(), isAllowed(), permissionFlags);
        } else {
            permission = new ReadOnlyPermission(permissionInfo.name, isGranted, opEntry.getOp(), isAllowed(), permissionFlags);
        }
        hasModifiablePermission = PermUtils.isModifiable(permission);
    }

    public boolean isAllowed() {
        boolean isAllowed = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isAllowed = vanillaItem.getMode() == AppOpsManager.MODE_FOREGROUND;
        }
        isAllowed |= vanillaItem.getMode() == AppOpsManager.MODE_ALLOWED;
        // Special case for default
        if (vanillaItem.getMode() == AppOpsManager.MODE_DEFAULT) {
            isAllowed |= (permission != null && permission.isGranted());
        }
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
            throws RemoteException, PermissionException {
        boolean isSuccessful;
        if (hasModifiablePermission && permission != null) {
            PermUtils.grantPermission(packageInfo, permission, appOpsService, true, true);
            isSuccessful = true;
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
            throws RemoteException, PermissionException {
        boolean isSuccessful;
        if (hasModifiablePermission && permission != null) {
            PermUtils.revokePermission(packageInfo, permission, appOpsService, true);
            isSuccessful = true;
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
            throws RemoteException, PermissionException {
        if (hasModifiablePermission && permission != null) {
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
