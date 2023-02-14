// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import android.content.pm.PackageInfo;
import android.content.pm.PermissionInfo;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PermissionInfoCompat;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.permission.PermUtils;
import io.github.muntashirakon.AppManager.permission.Permission;
import io.github.muntashirakon.AppManager.permission.PermissionException;

/**
 * Stores individual app details item
 */
public class AppDetailsPermissionItem extends AppDetailsItem<PermissionInfo> {
    @NonNull
    public final Permission permission;
    public final boolean isDangerous; // AKA Runtime
    public final boolean modifiable;
    public final int flags;
    public final int protectionFlags;

    public AppDetailsPermissionItem(@NonNull PermissionInfo permissionInfo, @NonNull Permission permission, int flags) {
        super(permissionInfo);
        this.permission = permission;
        this.isDangerous = PermissionInfoCompat.getProtection(permissionInfo) == PermissionInfo.PROTECTION_DANGEROUS;
        this.protectionFlags = PermissionInfoCompat.getProtectionFlags(permissionInfo);
        this.modifiable = PermUtils.isModifiable(permission);
        this.flags = flags;
    }

    public boolean isGranted() {
        if (!permission.isReadOnly()) {
            return permission.isGrantedIncludingAppOp();
        }
        if (permission.affectsAppOp()) {
            return permission.isAppOpAllowed();
        }
        return permission.isGranted();
    }

    /**
     * Grant the permission.
     *
     * <p>This also automatically grants app op if it has app op.
     */
    @WorkerThread
    public void grantPermission(@NonNull PackageInfo packageInfo, @NonNull AppOpsManagerCompat appOpsManager)
            throws RemoteException, PermissionException {
        PermUtils.grantPermission(packageInfo, permission, appOpsManager, true, true);
    }

    /**
     * Revoke the permission.
     *
     * <p>This also disallows the app op for the permission if it has app op.
     */
    @WorkerThread
    public void revokePermission(@NonNull PackageInfo packageInfo, AppOpsManagerCompat appOpsManager)
            throws RemoteException, PermissionException {
        PermUtils.revokePermission(packageInfo, permission, appOpsManager, true);
    }
}
