// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_AUTO_REVOKED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_ONE_TIME;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVIEW_REQUIRED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVOKED_COMPAT;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_FIXED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_SET;

import android.annotation.UserIdInt;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Controls permissions whose grant and flags are owned by PackageManager.
 */
public final class PackageManagerPermissionController implements IPermissionController {
    @NonNull
    private static final PackageManagerPermissionController INSTANCE =
            new PackageManagerPermissionController(new FrameworkPackageManagerPermissionPlatform());

    @NonNull
    private final PackageManagerPermissionPlatform mPlatform;

    public static PackageManagerPermissionController getInstance() {
        return INSTANCE;
    }

    PackageManagerPermissionController(@NonNull PackageManagerPermissionPlatform platform) {
        mPlatform = platform;
    }

    /**
     * Whether Package Manager is solely responsible for this permission (no app op dependency).
     */
    public boolean supports(@NonNull Permission permission) {
        // return !permission.isReadOnly() && !permission.affectsAppOp();
        return !permission.readOnly && !permission.affectsAppOp();
    }

    @NonNull
    @Override
    public String getId() {
        return "package-manager";
    }

    @Override
    public boolean supports(@NonNull PermissionContext context) {
        return supportsPackageManagerState(context.permission);
    }

    @NonNull
    @Override
    public PermissionControllerState getState(@NonNull PermissionContext context) {
        return new PermissionControllerState(getId(), context.permission.isGranted()
                ? PermissionState.GRANTED : PermissionState.DENIED,
                isModifiable(context.permission), null);
    }

    @NonNull
    @Override
    public PermissionChangeResult setGranted(@NonNull PermissionContext context,
                                             boolean granted) {
        if (!supportsPackageManagerState(context.permission)) {
            return PermissionChangeResult.unsupported("Package Manager does not support "
                    + context.permission.getName());
        }
        try {
            if (supports(context.permission)) {
                if (granted) {
                    grant(context.packageInfo, context.permission, context.setByUser, context.fixedByUser);
                } else revoke(context.packageInfo, context.permission, context.fixedByUser);
            } else {
                setPlatformGranted(context.packageInfo, context.permission.getName(), context.userId, granted);
            }
            return PermissionChangeResult.success();
        } catch (PermissionException e) {
            return PermissionChangeResult.failure(e.getMessage(), e);
        }
    }

    /**
     * Whether PackageManager can grant/revoke this permission
     */
    public boolean supportsPackageManagerState(@NonNull Permission permission) {
        return !permission.readOnly;
    }

    /**
     * Whether the current backend can modify this permission.
     */
    public boolean isModifiable(@NonNull Permission permission) {
        return supports(permission) && !permission.isReadOnly() && !permission.isPolicyFixed()
                && mPlatform.canModifyPermissions();
    }

    @WorkerThread
    public void grant(@NonNull PackageInfo packageInfo, @NonNull Permission permission,
                      boolean setByTheUser, boolean fixedByTheUser) throws PermissionException {
        requireModifiable(permission);
        PermissionMutation.prepareGrant(packageInfo, permission, setByTheUser, fixedByTheUser);
        persist(packageInfo.applicationInfo, permission, null);
    }

    @WorkerThread
    public void revoke(@NonNull PackageInfo packageInfo, @NonNull Permission permission,
                       boolean fixedByTheUser) throws PermissionException {
        revoke(packageInfo, permission, fixedByTheUser, null);
    }

    @WorkerThread
    public void revoke(@NonNull PackageInfo packageInfo, @NonNull Permission permission,
                       boolean fixedByTheUser, @Nullable String reason) throws PermissionException {
        requireModifiable(permission);
        PermissionMutation.prepareRevoke(packageInfo, permission, fixedByTheUser);
        persist(packageInfo.applicationInfo, permission, reason);
    }

    /**
     * Changes only the PackageManager grant/revoke and deliberately retains all permission flags.
     *
     * <p>This remains the low-level operation for PackageManager-only batch grant/revoke.
     * Generic callers must use {@link PermissionControllerRegistry}, which composes the
     * AppOp controller when necessary.</p>
     */
    @WorkerThread
    public void setPlatformGranted(@NonNull PackageInfo packageInfo,
                                   @NonNull String permissionName,
                                   @UserIdInt int userId,
                                   boolean granted) throws PermissionException {
        PermissionChangeResult result = trySetPlatformGranted(packageInfo, permissionName, userId,
                granted);
        if (!result.isSuccessful()) {
            throw new PermissionException(result.getMessage(), result.getCause());
        }
    }

    /**
     * Structured variant of {@link #setPlatformGranted(PackageInfo, String, int, boolean)}.
     */
    @WorkerThread
    @NonNull
    public PermissionChangeResult trySetPlatformGranted(@NonNull PackageInfo packageInfo,
                                                        @NonNull String permissionName,
                                                        @UserIdInt int userId,
                                                        boolean granted) {
        if (UserHandleHidden.getUserId(packageInfo.applicationInfo.uid) != userId) {
            return PermissionChangeResult.failure(
                    "Package user does not match requested user " + userId, null);
        }
        Permission permission;
        try {
            permission = PermissionResolver.resolve(mPlatform, packageInfo, permissionName, userId,
                    false);
        } catch (PermissionException e) {
            return PermissionChangeResult.failure("Could not resolve permission " + permissionName,
                    e);
        }
        if (!supportsPackageManagerState(permission) || permission.isReadOnly()
                || permission.isPolicyFixed()) {
            return PermissionChangeResult.unsupported("Unmodifiable permission " + permissionName);
        }
        if (!mPlatform.canModifyPermissions()) {
            return PermissionChangeResult.unsupported(
                    "Package Manager permission capability is unavailable");
        }
        permission.setGranted(granted);
        try {
            persist(packageInfo.applicationInfo, permission, null, false);
            return PermissionChangeResult.success();
        } catch (PermissionException e) {
            return PermissionChangeResult.failure("Could not change permission " + permissionName,
                    e);
        }
    }

    private void requireModifiable(@NonNull Permission permission) throws PermissionException {
        if (!isModifiable(permission)) {
            throw new PermissionException("Unmodifiable permission " + permission.getName());
        }
    }

    private void persist(@NonNull ApplicationInfo applicationInfo,
                         @NonNull Permission permission,
                         @Nullable String revokeReason) throws PermissionException {
        persist(applicationInfo, permission, revokeReason, true);
    }

    private void persist(@NonNull ApplicationInfo applicationInfo,
                         @NonNull Permission permission,
                         @Nullable String revokeReason,
                         boolean updatePermissionFlags) throws PermissionException {
        int userId = UserHandleHidden.getUserId(applicationInfo.uid);
        try {
            if (permission.isGranted()) {
                mPlatform.grantPermission(applicationInfo.packageName, permission.getName(), userId);
                Log.d("PERM", "Granted %s", permission.getName());
            } else if (mPlatform.checkPermission(permission.getName(), applicationInfo.packageName,
                    userId) == PERMISSION_GRANTED) {
                mPlatform.revokePermission(applicationInfo.packageName, permission.getName(), userId,
                        revokeReason);
                Log.d("PERM", "Revoked %s", permission.getName());
            }
            if (updatePermissionFlags) {
                updateFlags(applicationInfo, permission, userId);
            }
            if (userId != mPlatform.getCurrentUserId()) {
                mPlatform.onPackageAltered(applicationInfo.packageName);
            }
        } catch (Exception e) {
            throw new PermissionException(e);
        }
    }

    private void updateFlags(@NonNull ApplicationInfo applicationInfo,
                             @NonNull Permission permission,
                             @UserIdInt int userId) throws RemoteException {
        int flags = (permission.isUserSet() ? FLAG_PERMISSION_USER_SET : 0)
                | (permission.isUserFixed() ? FLAG_PERMISSION_USER_FIXED : 0)
                | (permission.isRevokedCompat() ? FLAG_PERMISSION_REVOKED_COMPAT : 0)
                // FLAG_PERMISSION_POLICY_FIXED is intentionally not changed.
                | (permission.isReviewRequired() ? FLAG_PERMISSION_REVIEW_REQUIRED : 0);
        int mask = FLAG_PERMISSION_USER_SET
                | FLAG_PERMISSION_USER_FIXED
                | FLAG_PERMISSION_REVOKED_COMPAT
                // Clear review-required only when the in-memory transition cleared it.
                | (permission.isReviewRequired() ? 0 : FLAG_PERMISSION_REVIEW_REQUIRED)
                | FLAG_PERMISSION_ONE_TIME
                | FLAG_PERMISSION_AUTO_REVOKED;
        boolean checkAdjustPolicy =
                mPlatform.getCheckAdjustPolicyFlagPermission(applicationInfo);
        mPlatform.updatePermissionFlags(permission.getName(), applicationInfo.packageName, mask,
                flags, checkAdjustPolicy, userId);
    }
}
