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
import android.app.AppOpsManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Controls permissions whose effective state includes an AppOp.
 */
public final class AppOpPermissionController implements IPermissionController {
    private static final String KILL_REASON_APP_OP_CHANGE = "Permission related app op changed";

    @NonNull
    private static final AppOpPermissionController INSTANCE = new AppOpPermissionController(
            new FrameworkPackageManagerPermissionPlatform(),
            new FrameworkAppOpPermissionPlatform()
    );

    @NonNull
    private final PackageManagerPermissionPlatform mPackageManagerPlatform;
    @NonNull
    private final AppOpPermissionPlatform mAppOpPlatform;

    @NonNull
    public static AppOpPermissionController getInstance() {
        return INSTANCE;
    }

    AppOpPermissionController(@NonNull PackageManagerPermissionPlatform packageManagerPlatform,
                              @NonNull AppOpPermissionPlatform appOpPlatform) {
        mPackageManagerPlatform = packageManagerPlatform;
        mAppOpPlatform = appOpPlatform;
    }

    public boolean supports(@NonNull Permission permission) {
        return permission.affectsAppOp();
    }

    @NonNull
    @Override
    public String getId() {
        return "app-op";
    }

    @Override
    public boolean supports(@NonNull PermissionContext context) {
        return supports(context.permission);
    }

    @NonNull
    @Override
    public PermissionControllerState getState(@NonNull PermissionContext context) {
        return new PermissionControllerState(getId(), context.permission.isAppOpAllowed()
                ? PermissionState.GRANTED : PermissionState.DENIED,
                isModifiable(context.permission), null);
    }

    @NonNull
    @Override
    public PermissionChangeResult setGranted(@NonNull PermissionContext context, boolean granted) {
        if (context.appOps == null) {
            return PermissionChangeResult.failure("AppOps service unavailable", null);
        }
        try {
            if (granted) {
                grant(context.packageInfo, context.permission, context.appOps, context.setByUser,
                        context.fixedByUser);
            } else {
                revoke(context.packageInfo, context.permission, context.appOps, context.fixedByUser);
            }
            return PermissionChangeResult.success();
        } catch (PermissionException e) {
            return PermissionChangeResult.failure(e.getMessage(), e);
        }
    }

    public boolean isModifiable(@NonNull Permission permission) {
        return supports(permission) && mPackageManagerPlatform.canModifyPermissions();
    }

    @WorkerThread
    public void grant(@NonNull PackageInfo packageInfo, @NonNull Permission permission,
                      @NonNull AppOpsManagerCompat appOpsManager, boolean setByTheUser,
                      boolean fixedByTheUser) throws PermissionException {
        requireModifiable(permission);
        boolean killApp = PermissionMutation.prepareGrant(packageInfo, permission, setByTheUser,
                fixedByTheUser);
        persist(packageInfo.applicationInfo, permission, appOpsManager, null);
        killIfNeeded(packageInfo.applicationInfo.uid, killApp);
    }

    @WorkerThread
    public void revoke(@NonNull PackageInfo packageInfo, @NonNull Permission permission,
                       @NonNull AppOpsManagerCompat appOpsManager, boolean fixedByTheUser)
            throws PermissionException {
        revoke(packageInfo, permission, appOpsManager, fixedByTheUser, null);
    }

    @WorkerThread
    public void revoke(@NonNull PackageInfo packageInfo, @NonNull Permission permission,
                       @NonNull AppOpsManagerCompat appOpsManager, boolean fixedByTheUser,
                       @Nullable String reason) throws PermissionException {
        requireModifiable(permission);
        boolean killApp = PermissionMutation.prepareRevoke(packageInfo, permission, fixedByTheUser);
        persist(packageInfo.applicationInfo, permission, appOpsManager, reason);
        killIfNeeded(packageInfo.applicationInfo.uid, killApp);
    }

    /**
     * Applies batch/profile state without inventing user-set or user-fixed decisions.
     *
     * <p>Modern runtime/development permissions change both enforcement layers. Legacy and
     * read-only permissions retain the Package Manager grant and change only their AppOp.</p>
     */
    @WorkerThread
    @NonNull
    public PermissionChangeResult trySetGrantedForBatch(
            @NonNull PackageInfo packageInfo, @NonNull String permissionName,
            @UserIdInt int userId, @NonNull AppOpsManagerCompat appOpsManager, boolean granted) {
        if (UserHandleHidden.getUserId(packageInfo.applicationInfo.uid) != userId) {
            return PermissionChangeResult.failure(
                    "Package user does not match requested user " + userId, null);
        }
        if (!mPackageManagerPlatform.canModifyPermissions()) {
            return PermissionChangeResult.unsupported(
                    "AppOp permission capability is unavailable");
        }
        try {
            int appOp = AppOpsManagerCompat.permissionToOpCode(permissionName);
            if (appOp == AppOpsManagerCompat.OP_NONE) {
                return PermissionChangeResult.unsupported(
                        "Permission has no AppOp " + permissionName);
            }
            int mode = mAppOpPlatform.checkOperation(appOpsManager, appOp,
                    packageInfo.applicationInfo.uid, packageInfo.packageName);
            boolean appOpAllowed = mode == AppOpsManager.MODE_ALLOWED
                    || mode == AppOpsManager.MODE_FOREGROUND;
            Permission permission = PermissionResolver.resolve(mPackageManagerPlatform, packageInfo,
                    permissionName, userId, appOpAllowed);

            boolean legacyRuntime = permission.isRuntime()
                    && packageInfo.applicationInfo.targetSdkVersion
                    <= android.os.Build.VERSION_CODES.LOLLIPOP_MR1;
            if (legacyRuntime && !permission.isGranted()) {
                return PermissionChangeResult.unsupported(
                        "Legacy app cannot have not-granted runtime permission " + permissionName);
            }
            boolean killApp = permission.isAppOpAllowed() != granted
                    && (permission.readOnly || legacyRuntime);
            if (!permission.isReadOnly() && !legacyRuntime) {
                permission.setGranted(granted);
            }
            permission.setAppOpAllowed(granted);
            persist(packageInfo.applicationInfo, permission, appOpsManager, null, false);
            killIfNeeded(packageInfo.applicationInfo.uid, killApp);
            return PermissionChangeResult.success();
        } catch (Exception e) {
            return PermissionChangeResult.failure(
                    "Could not change permission " + permissionName, e);
        }
    }

    private void requireModifiable(@NonNull Permission permission) throws PermissionException {
        if (!isModifiable(permission)) {
            throw new PermissionException("Unmodifiable permission " + permission.getName());
        }
    }

    private void killIfNeeded(int uid, boolean killApp) throws PermissionException {
        if (!killApp || !mAppOpPlatform.canKillUid()) {
            return;
        }
        try {
            mAppOpPlatform.killUid(uid, KILL_REASON_APP_OP_CHANGE);
        } catch (Exception e) {
            throw new PermissionException(e);
        }
    }

    private void persist(@NonNull ApplicationInfo applicationInfo,
                         @NonNull Permission permission,
                         @NonNull AppOpsManagerCompat appOpsManager,
                         @Nullable String revokeReason) throws PermissionException {
        persist(applicationInfo, permission, appOpsManager, revokeReason, true);
    }

    private void persist(@NonNull ApplicationInfo applicationInfo,
                         @NonNull Permission permission,
                         @NonNull AppOpsManagerCompat appOpsManager,
                         @Nullable String revokeReason,
                         boolean updatePermissionFlags) throws PermissionException {
        int uid = applicationInfo.uid;
        int userId = UserHandleHidden.getUserId(uid);
        try {
            // isReadOnly() includes system-fixed. Such permissions historically skipped the PM
            // grant bit, while the raw readOnly field below still allowed their flags to be synced.
            if (!permission.isReadOnly()) {
                if (permission.isGranted()) {
                    mPackageManagerPlatform.grantPermission(applicationInfo.packageName,
                            permission.getName(), userId);
                    Log.d("PERM", "Granted %s", permission.getName());
                } else if (mPackageManagerPlatform.checkPermission(permission.getName(),
                        applicationInfo.packageName, userId) == PERMISSION_GRANTED) {
                    mPackageManagerPlatform.revokePermission(applicationInfo.packageName,
                            permission.getName(), userId, revokeReason);
                    Log.d("PERM", "Revoked %s", permission.getName());
                }
            }

            if (updatePermissionFlags && !permission.readOnly) {
                updateFlags(applicationInfo, permission, userId);
            }

            // System-fixed permissions historically skipped AppOp writes, including when their
            // Permission subtype is otherwise mutable. Keep that subtle distinction intact.
            if (!permission.isSystemFixed()) {
                setAppOpMode(appOpsManager, permission.getAppOp(), applicationInfo.packageName, uid,
                        permission.isAppOpAllowed()
                                ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_IGNORED);
            }

            if (userId != mPackageManagerPlatform.getCurrentUserId()) {
                mPackageManagerPlatform.onPackageAltered(applicationInfo.packageName);
            }
        } catch (Exception e) {
            throw new PermissionException(e);
        }
    }

    private void updateFlags(@NonNull ApplicationInfo applicationInfo,
                             @NonNull Permission permission,
                             @UserIdInt int userId) throws Exception {
        int flags = (permission.isUserSet() ? FLAG_PERMISSION_USER_SET : 0)
                | (permission.isUserFixed() ? FLAG_PERMISSION_USER_FIXED : 0)
                | (permission.isRevokedCompat() ? FLAG_PERMISSION_REVOKED_COMPAT : 0)
                // Policy-fixed is intentionally not changed.
                | (permission.isReviewRequired() ? FLAG_PERMISSION_REVIEW_REQUIRED : 0);
        int mask = FLAG_PERMISSION_USER_SET
                | FLAG_PERMISSION_USER_FIXED
                | FLAG_PERMISSION_REVOKED_COMPAT
                | (permission.isReviewRequired() ? 0 : FLAG_PERMISSION_REVIEW_REQUIRED)
                | FLAG_PERMISSION_ONE_TIME
                | FLAG_PERMISSION_AUTO_REVOKED;
        boolean checkAdjustPolicy =
                mPackageManagerPlatform.getCheckAdjustPolicyFlagPermission(applicationInfo);
        mPackageManagerPlatform.updatePermissionFlags(permission.getName(),
                applicationInfo.packageName, mask, flags, checkAdjustPolicy, userId);
    }

    /**
     * Sets an AppOp mode only when its current mode differs.
     */
    public boolean setAppOpMode(@NonNull AppOpsManagerCompat appOpsManager, int appOp,
                                @NonNull String packageName, int uid,
                                @AppOpsManagerCompat.Mode int mode) throws PermissionException {
        try {
            int currentMode = mAppOpPlatform.checkOperation(appOpsManager, appOp, uid, packageName);
            if (currentMode == mode) {
                return false;
            }
            mAppOpPlatform.setMode(appOpsManager, appOp, uid, packageName, mode);
            return true;
        } catch (Exception e) {
            throw new PermissionException(e);
        }
    }

}
