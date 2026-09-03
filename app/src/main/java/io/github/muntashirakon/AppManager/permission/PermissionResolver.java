// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.annotation.UserIdInt;
import android.content.pm.PackageInfo;
import android.content.pm.PermissionInfo;

import androidx.annotation.NonNull;
import androidx.core.content.pm.PermissionInfoCompat;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;

/**
 * Builds the permission model from current platform state.
 */
final class PermissionResolver {
    private PermissionResolver() {
    }

    @NonNull
    static Permission resolve(@NonNull PackageManagerPermissionPlatform platform,
                              @NonNull PackageInfo packageInfo,
                              @NonNull String permissionName,
                              @UserIdInt int userId,
                              boolean appOpAllowed) throws PermissionException {
        try {
            PermissionInfo permissionInfo = platform.getPermissionInfo(permissionName,
                    packageInfo.packageName);
            if (permissionInfo == null) {
                throw new PermissionException("Unknown permission " + permissionName);
            }
            boolean granted = platform.checkPermission(permissionName, packageInfo.packageName,
                    userId) == PERMISSION_GRANTED;
            int flags = platform.getPermissionFlags(permissionName, packageInfo.packageName, userId);
            int appOp = AppOpsManagerCompat.permissionToOpCode(permissionName);
            int protection = PermissionInfoCompat.getProtection(permissionInfo);
            int protectionFlags = PermissionInfoCompat.getProtectionFlags(permissionInfo);
            if (protection == PermissionInfo.PROTECTION_DANGEROUS
                    && PermUtils.systemSupportsRuntimePermissions()) {
                return new RuntimePermission(permissionName, granted, appOp, appOpAllowed, flags);
            }
            if ((protectionFlags & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
                return new DevelopmentPermission(permissionName, granted, appOp, appOpAllowed,
                        flags);
            }
            return new ReadOnlyPermission(permissionName, granted, appOp, appOpAllowed, flags);
        } catch (PermissionException e) {
            throw e;
        } catch (Exception e) {
            throw new PermissionException(e);
        }
    }
}
