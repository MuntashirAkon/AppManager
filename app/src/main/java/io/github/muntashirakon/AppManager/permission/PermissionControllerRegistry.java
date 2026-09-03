// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import android.annotation.UserIdInt;
import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;

public final class PermissionControllerRegistry {
    @NonNull
    private static final PermissionControllerRegistry INSTANCE = new PermissionControllerRegistry(
            PackageManagerPermissionController.getInstance(),
            AppOpPermissionController.getInstance(),
            SpecialPermissionController.getInstance());

    @NonNull
    private final PackageManagerPermissionController mPackageManagerController;
    @NonNull
    private final AppOpPermissionController mAppOpController;
    @NonNull
    private final SpecialPermissionController mSpecialPermissionController;
    @Nullable
    private volatile InternetPermissionController mInternetController;

    public boolean isOverlayModifiable(@NonNull String permissionName) {
        return InternetPermissionController.isSupported()
                && InternetPermissionController.PERMISSION.equals(permissionName);
    }

    /**
     * Whether any registered provider can modify the state without user interaction.
     */
    public boolean isModifiable(@NonNull Permission permission) {
        if (isOverlayModifiable(permission.getName())) {
            return true;
        }
        if (mAppOpController.supports(permission)) {
            return mAppOpController.isModifiable(permission);
        }
        return mPackageManagerController.isModifiable(permission);
    }

    @NonNull
    public PermissionState getOverlayState(@NonNull String packageName, int userId,
                                           @NonNull String permissionName) {
        if (isOverlayModifiable(permissionName)) {
            return internetController().getState(packageName, userId
            );
        }
        return PermissionState.UNSUPPORTED;
    }

    @NonNull
    public static PermissionControllerRegistry getInstance() {
        return INSTANCE;
    }

    PermissionControllerRegistry(@NonNull PackageManagerPermissionController packageManagerController,
                                 @NonNull AppOpPermissionController appOpController,
                                 @NonNull SpecialPermissionController specialPermissionController) {
        mPackageManagerController = packageManagerController;
        mAppOpController = appOpController;
        mSpecialPermissionController = specialPermissionController;
    }

    @NonNull
    private InternetPermissionController internetController() {
        InternetPermissionController controller = mInternetController;
        if (controller == null) {
            synchronized (this) {
                controller = mInternetController;
                if (controller == null) {
                    mInternetController = controller = InternetPermissionController.getInstance();
                }
            }
        }
        return controller;
    }

    /**
     * Builds the providers applicable to one permission.
     */
    @NonNull
    public IPermissionController resolve(@NonNull PermissionContext context) {
        List<IPermissionController> providers = new ArrayList<>();
        if (InternetPermissionController.PERMISSION.equals(context.permission.getName())
                && InternetPermissionController.isSupported()) {
            providers.add(internetController());
        } else {
            if (mPackageManagerController.supportsPackageManagerState(context.permission)) {
                providers.add(mPackageManagerController);
            }
            if (mAppOpController.supports(context.permission)) {
                providers.add(mAppOpController);
            }
        }
        if (providers.isEmpty() && mSpecialPermissionController.supports(context.permission.getName())) {
            providers.add(mSpecialPermissionController);
        }
        return new CompositePermissionController(providers);
    }

    @NonNull
    public PermissionChangeResult trySetGrantedForBatch(
            @NonNull PackageInfo packageInfo, @NonNull String permissionName,
            @UserIdInt int userId, @NonNull AppOpsManagerCompat appOpsManager, boolean granted) {
        if (isOverlayModifiable(permissionName)) {
            return internetController().setGranted(packageInfo.packageName, userId, granted,
                    InternetPermissionController.DEFAULT_FIREWALL_CHAIN);
        }
        PermissionChangeResult result;
        if (AppOpsManagerCompat.permissionToOpCode(permissionName) != AppOpsManagerCompat.OP_NONE) {
            result = mAppOpController.trySetGrantedForBatch(packageInfo, permissionName, userId,
                    appOpsManager, granted);
        } else {
            result = mPackageManagerController.trySetPlatformGranted(packageInfo, permissionName,
                    userId, granted);
        }
        // For special permissions (in no-root mode or fallbacks), this effectively does not do
        // anything.
        if (result.getStatus() == PermissionChangeResult.Status.UNSUPPORTED
                && mSpecialPermissionController.supports(permissionName)) {
            return mSpecialPermissionController.requestChange(permissionName, granted);
        }
        return result;
    }
}
