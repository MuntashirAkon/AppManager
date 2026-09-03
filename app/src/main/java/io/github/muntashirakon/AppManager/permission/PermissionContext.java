// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;

/**
 * Immutable input shared by all permission providers.
 */
public final class PermissionContext {
    /**
     * Current package info. Requires {@link android.content.pm.PackageManager#GET_PERMISSIONS} flag.
     */
    @NonNull
    public final PackageInfo packageInfo;
    /**
     * Current snapshot of the requested permission.
     */
    @NonNull
    public final Permission permission;
    /**
     * AppOps service for providers that require it.
     */
    @Nullable
    public final AppOpsManagerCompat appOps;
    public final int userId;
    public final boolean setByUser;
    public final boolean fixedByUser;

    public PermissionContext(@NonNull PackageInfo packageInfo, @NonNull Permission permission,
                             int userId, @Nullable AppOpsManagerCompat appOps) {
        this(packageInfo, permission, userId, appOps, true, true);
    }

    public PermissionContext(@NonNull PackageInfo packageInfo, @NonNull Permission permission,
                             int userId, @Nullable AppOpsManagerCompat appOps,
                             boolean setByUser, boolean fixedByUser) {
        this.packageInfo = packageInfo;
        this.permission = permission;
        this.userId = userId;
        this.appOps = appOps;
        this.setByUser = setByUser;
        this.fixedByUser = fixedByUser;
    }
}
