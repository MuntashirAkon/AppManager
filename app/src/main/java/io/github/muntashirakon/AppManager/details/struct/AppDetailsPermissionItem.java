// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import android.content.pm.PackageInfo;
import android.content.pm.PermissionInfo;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PermissionInfoCompat;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.permission.PermUtils;
import io.github.muntashirakon.AppManager.permission.Permission;
import io.github.muntashirakon.AppManager.permission.PermissionException;
import io.github.muntashirakon.AppManager.permission.PermissionUserAction;

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
    private volatile Boolean mOverlayGranted;
    @Nullable
    public final PermissionUserAction settingItem;

    public AppDetailsPermissionItem(@NonNull PermissionInfo permissionInfo, @NonNull Permission permission,
                                    int flags, boolean modifiable,
                                    @Nullable PermissionUserAction settingItem) {
        super(permissionInfo);
        this.permission = permission;
        this.isDangerous = PermissionInfoCompat.getProtection(permissionInfo) == PermissionInfo.PROTECTION_DANGEROUS;
        this.protectionFlags = PermissionInfoCompat.getProtectionFlags(permissionInfo);
        this.modifiable = modifiable;
        this.flags = flags;
        this.settingItem = settingItem;
    }

    public void setInitialOverlayState(@Nullable Boolean granted) {
        mOverlayGranted = granted;
    }

    public boolean hasOverlayState() {
        return mOverlayGranted != null;
    }

    public boolean isGranted() {
        if (mOverlayGranted != null) {
            return mOverlayGranted;
        }
        if (!permission.isReadOnly()) {
            return permission.isGrantedIncludingAppOp();
        }
        if (permission.affectsAppOp()) {
            return permission.isAppOpAllowed();
        }
        return permission.isGranted();
    }

    public void setOverlayGranted(boolean granted) {
        mOverlayGranted = granted;
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
