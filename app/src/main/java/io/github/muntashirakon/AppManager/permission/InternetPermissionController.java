// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import android.Manifest;
import android.os.Build;
import android.net.ConnectivityManagerHidden;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.dao.PermissionOverrideDao;
import io.github.muntashirakon.AppManager.db.entity.PermissionOverride;
import io.github.muntashirakon.AppManager.self.SelfPermissions;

public final class InternetPermissionController implements IPermissionController {
    public static final String PERMISSION = Manifest.permission.INTERNET;
    private static volatile InternetPermissionController sInstance;
    public static final String CONTROLLER = "internet";

    public static final int DEFAULT_FIREWALL_CHAIN = ConnectivityManagerHidden.FIREWALL_CHAIN_OEM_DENY_3;
    private final int mSdkInt;
    private final PermissionOverrideDao mDao;
    private final ConnectivityPermissionOverridePlatform mPlatform;

    public InternetPermissionController() {
        this(Build.VERSION.SDK_INT, AppsDb.getInstance().permissionOverrideDao(),
                new ConnectivityPermissionOverridePlatform());
    }

    @WorkerThread
    @NonNull
    public static InternetPermissionController getInstance() {
        InternetPermissionController instance = sInstance;
        if (instance == null) {
            synchronized (InternetPermissionController.class) {
                instance = sInstance;
                if (instance == null) {
                    sInstance = instance = new InternetPermissionController();
                }
            }
        }
        return instance;
    }

    InternetPermissionController(int sdkInt, @NonNull PermissionOverrideDao dao,
                                 @NonNull ConnectivityPermissionOverridePlatform platform) {
        mSdkInt = sdkInt;
        mDao = dao;
        mPlatform = platform;
    }

    public boolean supports(@NonNull String permissionName) {
        return isSupportedApi(mSdkInt) && PERMISSION.equals(permissionName) && canModify();
    }

    @NonNull
    @Override
    public String getId() {
        return CONTROLLER;
    }

    @Override
    public boolean supports(@NonNull PermissionContext context) {
        return supports(context.permission.getName());
    }

    @NonNull
    @Override
    public PermissionControllerState getState(@NonNull PermissionContext context) {
        return new PermissionControllerState(getId(), getState(context.packageInfo.packageName,
                context.userId), true, null);
    }

    @NonNull
    @Override
    public PermissionChangeResult setGranted(@NonNull PermissionContext context,
                                             boolean granted) {
        return setGranted(context.packageInfo.packageName, context.userId, granted,
                DEFAULT_FIREWALL_CHAIN);
    }

    @AnyThread
    public static boolean isSupported() {
        return isSupportedApi(Build.VERSION.SDK_INT) && canModify();
    }

    private static boolean canModify() {
        return SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.NETWORK_SETTINGS);
    }

    private static boolean isSupportedApi(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    @NonNull
    public PermissionChangeResult setGranted(@NonNull String packageName, int userId,
                                             boolean granted, int firewallChain) {
        if (!supports(PERMISSION)) {
            return PermissionChangeResult.unsupported("INTERNET overlay requires Android 14+");
        }
        PermissionOverride override = new PermissionOverride(packageName, userId, PERMISSION,
                granted, Integer.toString(firewallChain));
        override.syncStatus = PermissionOverrideReconciler.PENDING;
        mDao.insert(override);
        PermissionOverrideManager.reconcile(packageName, userId);
        return PermissionChangeResult.success();
    }

    @NonNull
    public PermissionState getState(@NonNull String packageName, int userId) {
        if (!supports(PERMISSION)) {
            return PermissionState.UNSUPPORTED;
        }
        // We use UNKNOWN state for any undesired cases
        PermissionOverride override = mDao.get(packageName, userId, PERMISSION);
        if (override == null) {
            return PermissionState.UNKNOWN;
        }
        try {
            int uid = mPlatform.resolveUid(packageName, userId);
            boolean enforced = mPlatform.isEnforced(uid, override);
            return enforced ? (override.desiredGranted ? PermissionState.GRANTED
                    : PermissionState.DENIED) : PermissionState.UNKNOWN;
        } catch (Exception e) {
            return PermissionState.UNKNOWN;
        }
    }
}
