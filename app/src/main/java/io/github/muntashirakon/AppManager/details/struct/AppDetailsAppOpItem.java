// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import android.app.AppOpsManager;
import android.content.pm.PackageInfo;
import android.content.pm.PermissionInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PermissionInfoCompat;

import java.util.List;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.permission.DevelopmentPermission;
import io.github.muntashirakon.AppManager.permission.PermUtils;
import io.github.muntashirakon.AppManager.permission.Permission;
import io.github.muntashirakon.AppManager.permission.PermissionException;
import io.github.muntashirakon.AppManager.permission.ReadOnlyPermission;
import io.github.muntashirakon.AppManager.permission.RuntimePermission;

public class AppDetailsAppOpItem extends AppDetailsItem<Integer> {
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

    @Nullable
    private AppOpsManagerCompat.OpEntry mOpEntry;

    public AppDetailsAppOpItem(@NonNull AppOpsManagerCompat.OpEntry opEntry) {
        this(opEntry.getOp());
        mOpEntry = opEntry;
    }

    public AppDetailsAppOpItem(int op) {
        super(op);
        mOpEntry = null;
        permissionInfo = null;
        permission = null;
        isDangerous = false;
        hasModifiablePermission = false;
        appContainsPermission = false;
    }

    public AppDetailsAppOpItem(@NonNull AppOpsManagerCompat.OpEntry opEntry, @NonNull PermissionInfo permissionInfo,
                               boolean isGranted, int permissionFlags, boolean appContainsPermission) {
        super(opEntry.getOp());
        mOpEntry = opEntry;
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

    public AppDetailsAppOpItem(int op, @NonNull PermissionInfo permissionInfo,
                               boolean isGranted, int permissionFlags, boolean appContainsPermission) {
        super(op);
        mOpEntry = null;
        this.permissionInfo = permissionInfo;
        this.appContainsPermission = appContainsPermission;
        isDangerous = PermissionInfoCompat.getProtection(permissionInfo) == PermissionInfo.PROTECTION_DANGEROUS;
        int protectionFlags = PermissionInfoCompat.getProtectionFlags(permissionInfo);
        if (isDangerous && PermUtils.systemSupportsRuntimePermissions()) {
            permission = new RuntimePermission(permissionInfo.name, isGranted, op, isAllowed(), permissionFlags);
        } else if ((protectionFlags & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
            permission = new DevelopmentPermission(permissionInfo.name, isGranted, op, isAllowed(), permissionFlags);
        } else {
            permission = new ReadOnlyPermission(permissionInfo.name, isGranted, op, isAllowed(), permissionFlags);
        }
        hasModifiablePermission = PermUtils.isModifiable(permission);
    }

    public int getOp() {
        return mainItem;
    }

    @AppOpsManagerCompat.Mode
    public int getMode() {
        if (mOpEntry != null) {
            return mOpEntry.getMode();
        }
        return AppOpsManagerCompat.opToDefaultMode(getOp());
    }

    public long getDuration() {
        if (mOpEntry != null) {
            return mOpEntry.getDuration();
        }
        return 0L;
    }

    public long getTime() {
        if (mOpEntry != null) {
            return mOpEntry.getTime();
        }
        return 0L;
    }

    public long getRejectTime() {
        if (mOpEntry != null) {
            return mOpEntry.getRejectTime();
        }
        return 0L;
    }

    public boolean isRunning() {
        return mOpEntry != null && mOpEntry.isRunning();
    }

    public boolean isAllowed() {
        boolean isAllowed = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isAllowed = getMode() == AppOpsManager.MODE_FOREGROUND;
        }
        isAllowed |= getMode() == AppOpsManager.MODE_ALLOWED;
        // Special case for default
        if (getMode() == AppOpsManager.MODE_DEFAULT) {
            isAllowed |= (permission != null && permission.isGranted());
        }
        return isAllowed;
    }

    /**
     * Allow the app op.
     *
     * <p>This also automatically grants the permission associated with the app op.
     */
    @RequiresPermission(allOf = {
            "android.permission.MANAGE_APP_OPS_MODES",
            ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS,
    })
    @WorkerThread
    public void allowAppOp(@NonNull PackageInfo packageInfo, @NonNull AppOpsManagerCompat appOpsManager)
            throws PermissionException {
        if (hasModifiablePermission && permission != null) {
            PermUtils.grantPermission(packageInfo, permission, appOpsManager, true, true);
        } else {
            PermUtils.allowAppOp(appOpsManager, getOp(), packageInfo.packageName, packageInfo.applicationInfo.uid);
        }
        invalidate(appOpsManager, packageInfo);
    }

    /**
     * Disallow the app op.
     *
     * <p>This also revokes the permission associated with the app op.
     */
    @RequiresPermission(allOf = {
            "android.permission.MANAGE_APP_OPS_MODES",
            ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS,
    })
    @WorkerThread
    public void disallowAppOp(@NonNull PackageInfo packageInfo, @NonNull AppOpsManagerCompat appOpsManager)
            throws PermissionException {
        if (hasModifiablePermission && permission != null) {
            PermUtils.revokePermission(packageInfo, permission, appOpsManager, true);
        } else {
            PermUtils.disallowAppOp(appOpsManager, getOp(), packageInfo.packageName, packageInfo.applicationInfo.uid);
        }
        invalidate(appOpsManager, packageInfo);
    }

    /**
     * Set mode for app op.
     *
     * <p>This also grants/revoke the permission associated with the app op.
     */
    @RequiresPermission(allOf = {
            "android.permission.MANAGE_APP_OPS_MODES",
            ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS,
            ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS,
    })
    @WorkerThread
    public void setAppOp(@NonNull PackageInfo packageInfo, @NonNull AppOpsManagerCompat appOpsManager,
                         @AppOpsManagerCompat.Mode int mode) throws PermissionException {
        if (hasModifiablePermission && permission != null) {
            boolean isAllowed = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isAllowed = getMode() == AppOpsManager.MODE_FOREGROUND;
            }
            isAllowed |= getMode() == AppOpsManager.MODE_ALLOWED;
            if (isAllowed) {
                PermUtils.grantPermission(packageInfo, permission, appOpsManager, true, true);
            } else {
                PermUtils.revokePermission(packageInfo, permission, appOpsManager, true);
            }
        }
        PermUtils.setAppOpMode(appOpsManager, getOp(), packageInfo.packageName, packageInfo.applicationInfo.uid, mode);
        invalidate(appOpsManager, packageInfo);
    }

    @RequiresPermission("android.permission.MANAGE_APP_OPS_MODES")
    public void invalidate(@NonNull AppOpsManagerCompat appOpsManager, @NonNull PackageInfo packageInfo)
            throws PermissionException {
        try {
            List<AppOpsManagerCompat.OpEntry> opEntryList = appOpsManager.getOpsForPackage(packageInfo.applicationInfo.uid,
                    packageInfo.packageName, new int[]{getOp()}).get(0).getOps();
            mOpEntry = !opEntryList.isEmpty() ? opEntryList.get(0) : null;
        } catch (Exception e) {
            throw new PermissionException(e);
        }
    }
}
