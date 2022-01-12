// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import android.content.pm.PackageInfo;
import android.content.pm.PermissionInfo;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PermissionInfoCompat;

import io.github.muntashirakon.AppManager.permission.PermUtils;
import io.github.muntashirakon.AppManager.permission.Permission;

/**
 * Stores individual app details item
 */
public class AppDetailsPermissionItem extends AppDetailsItem<PermissionInfo> {
    @NonNull
    public final Permission permission;
    public final boolean isDangerous;
    public final boolean modifiable;
    public final int flags;
    public final int protectionFlags;

    public AppDetailsPermissionItem(@NonNull PermissionInfo permissionInfo, @NonNull Permission permission, int flags) {
        super(permissionInfo);
        this.permission = permission;
        this.isDangerous = PermissionInfoCompat.getProtection(permissionInfo) == PermissionInfo.PROTECTION_DANGEROUS;
        this.protectionFlags = PermissionInfoCompat.getProtectionFlags(permissionInfo);
        this.modifiable = !permission.isSystemFixed() && (isDangerous
                || (protectionFlags & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0);
        this.flags = flags;
    }

    public boolean isGranted() {
        // FIXME: 12/1/22 Fix by background permissions
        return permission.isGranted() || ((protectionFlags & PermissionInfo.PROTECTION_FLAG_APPOP) != 0
                && permission.affectsAppOp() && permission.isAppOpAllowed());
    }

    /**
     * Grant the permission.
     *
     * <p>This also automatically grants app op if it has app op.
     *
     * @param setByTheUser   If the user has made the decision. This does not unset the flag
     * @param fixedByTheUser If the user requested that she/he does not want to be asked again
     * @return {@code true} iff the permission could be granted.
     */
    @WorkerThread
    public boolean grantPermission(@NonNull PackageInfo packageInfo, boolean setByTheUser, boolean fixedByTheUser)
            throws RemoteException {
        return PermUtils.grantPermission(packageInfo, permission, setByTheUser, fixedByTheUser);
    }

    /**
     * Revoke the permission.
     *
     * <p>This also disallows the app op for the permission if it has app op.
     *
     * @param fixedByTheUser If the user requested that she/he does not want to be asked again
     * @return {@code true} iff the permission could be revoked.
     */
    @WorkerThread
    public boolean revokePermission(@NonNull PackageInfo packageInfo, boolean fixedByTheUser)
            throws RemoteException {
        return PermUtils.revokePermission(packageInfo, permission, fixedByTheUser);
    }
}
