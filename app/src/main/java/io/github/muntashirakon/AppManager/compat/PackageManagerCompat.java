// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageInstaller;
import android.content.pm.IPackageManager;
import android.content.pm.IPackageManagerN;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.DeadObjectException;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.AndroidException;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.rikka.tools.refine.Refine;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.SYNCHRONOUS;

public final class PackageManagerCompat {
    @IntDef({
            COMPONENT_ENABLED_STATE_DEFAULT,
            COMPONENT_ENABLED_STATE_ENABLED,
            COMPONENT_ENABLED_STATE_DISABLED,
            COMPONENT_ENABLED_STATE_DISABLED_USER,
            COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EnabledState {
    }

    @IntDef(flag = true, value = {
            DONT_KILL_APP,
            SYNCHRONOUS
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EnabledFlags {
    }

    private static final int WORKING_FLAGS = PackageManager.GET_META_DATA | PackageUtils.flagMatchUninstalled;

    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    @WorkerThread
    public static List<PackageInfo> getInstalledPackages(int flags, @UserIdInt int userHandle)
            throws RemoteException {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M && (flags & ~WORKING_FLAGS) != 0) {
            // Need workaround
            List<ApplicationInfo> applicationInfoList = getInstalledApplications(flags & WORKING_FLAGS, userHandle);
            List<PackageInfo> packageInfoList = new ArrayList<>(applicationInfoList.size());
            for (int i = 0; i < applicationInfoList.size(); ++i) {
                try {
                    packageInfoList.add(getPackageInfo(applicationInfoList.get(i).packageName, flags, userHandle));
                    if (i % 100 == 0) {
                        // Prevent DeadObjectException
                        SystemClock.sleep(300);
                    }
                } catch (Exception e) {
                    throw new RemoteException(e.getMessage());
                }
            }
            return packageInfoList;
        }
        IPackageManager pm = getPackageManager();
        if (CompatUtils.isAndroid13Beta()) {
            return pm.getInstalledPackages((long) flags, userHandle).getList();
        }
        return pm.getInstalledPackages(flags, userHandle).getList();
    }

    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    @WorkerThread
    public static List<ApplicationInfo> getInstalledApplications(int flags, @UserIdInt int userHandle)
            throws RemoteException {
        IPackageManager pm = getPackageManager();
        if (CompatUtils.isAndroid13Beta()) {
            return pm.getInstalledApplications((long) flags, userHandle).getList();
        }
        return pm.getInstalledApplications(flags, userHandle).getList();
    }

    @NonNull
    public static PackageInfo getPackageInfo(String packageName, int flags, @UserIdInt int userHandle)
            throws RemoteException, PackageManager.NameNotFoundException {
        IPackageManager pm = getPackageManager();
        PackageInfo info = null;
        try {
            info = getPackageInfoInternal(pm, packageName, flags, userHandle);
        } catch (DeadObjectException ignore) {
        }
        if (info == null) {
            // The app might not be loaded properly due parcel size limit, try to load components separately.
            // first check the existence of the package
            int strippedFlags = flags & ~(PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                    | PackageManager.GET_PROVIDERS | PackageManager.GET_RECEIVERS | PackageManager.GET_PERMISSIONS);
            info = getPackageInfoInternal(pm, packageName, strippedFlags, userHandle);
            if (info == null) {
                // At this point, it should either return package info or throw RemoteException.
                // Returning null denotes that it failed again even after the major flags have been stripped.
                throw new IllegalStateException(String.format("Could not retrieve info for package %s with flags 0x%X",
                        packageName, strippedFlags));
            }
            // Load info for major flags
            ActivityInfo[] activities = null;
            if ((flags & PackageManager.GET_ACTIVITIES) != 0) {
                int newFlags = flags & ~(PackageManager.GET_SERVICES | PackageManager.GET_PROVIDERS
                        | PackageManager.GET_RECEIVERS | PackageManager.GET_PERMISSIONS);
                PackageInfo info1 = getPackageInfoInternal(pm, packageName, newFlags, userHandle);
                if (info1 != null) activities = info1.activities;
            }
            ServiceInfo[] services = null;
            if ((flags & PackageManager.GET_SERVICES) != 0) {
                int newFlags = flags & ~(PackageManager.GET_ACTIVITIES | PackageManager.GET_PROVIDERS
                        | PackageManager.GET_RECEIVERS | PackageManager.GET_PERMISSIONS);
                PackageInfo info1 = getPackageInfoInternal(pm, packageName, newFlags, userHandle);
                if (info1 != null) services = info1.services;
            }
            ProviderInfo[] providers = null;
            if ((flags & PackageManager.GET_PROVIDERS) != 0) {
                int newFlags = flags & ~(PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                        | PackageManager.GET_RECEIVERS | PackageManager.GET_PERMISSIONS);
                PackageInfo info1 = getPackageInfoInternal(pm, packageName, newFlags, userHandle);
                if (info1 != null) providers = info1.providers;
            }
            ActivityInfo[] receivers = null;
            if ((flags & PackageManager.GET_RECEIVERS) != 0) {
                int newFlags = flags & ~(PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                        | PackageManager.GET_PROVIDERS | PackageManager.GET_PERMISSIONS);
                PackageInfo info1 = getPackageInfoInternal(pm, packageName, newFlags, userHandle);
                if (info1 != null) receivers = info1.receivers;
            }
            PermissionInfo[] permissions = null;
            if ((flags & PackageManager.GET_PERMISSIONS) != 0) {
                int newFlags = flags & ~(PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                        | PackageManager.GET_PROVIDERS | PackageManager.GET_RECEIVERS);
                PackageInfo info1 = getPackageInfoInternal(pm, packageName, newFlags, userHandle);
                if (info1 != null) permissions = info1.permissions;
            }
            info.activities = activities;
            info.services = services;
            info.providers = providers;
            info.receivers = receivers;
            info.permissions = permissions;
        }
        // Info should never be null here but it's checked anyway.
        return Objects.requireNonNull(info);
    }

    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    @NonNull
    public static ApplicationInfo getApplicationInfo(String packageName, int flags, @UserIdInt int userHandle)
            throws RemoteException {
        IPackageManager pm = getPackageManager();
        if (CompatUtils.isAndroid13Beta()) {
            return pm.getApplicationInfo(packageName, (long) flags, userHandle);
        }
        return pm.getApplicationInfo(packageName, flags, userHandle);
    }

    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    @NonNull
    public static List<ResolveInfo> queryIntentActivities(@NonNull Context context, @NonNull Intent intent, int flags,
                                                          @UserIdInt int userId)
            throws RemoteException {
        IPackageManager pm = getPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            IPackageManagerN pmN = Refine.unsafeCast(pm);
            ParceledListSlice<ResolveInfo> resolveInfoList;
            if (CompatUtils.isAndroid13Beta()) {
                resolveInfoList = pmN.queryIntentActivities(intent,
                        intent.resolveTypeIfNeeded(context.getContentResolver()), (long) flags, userId);
            } else {
                resolveInfoList = pmN.queryIntentActivities(intent,
                        intent.resolveTypeIfNeeded(context.getContentResolver()), flags, userId);
            }
            return resolveInfoList.getList();
        } else {
            return pm.queryIntentActivities(intent, intent.resolveTypeIfNeeded(context.getContentResolver()), flags,
                    userId);
        }
    }

    @EnabledState
    public static int getComponentEnabledSetting(ComponentName componentName,
                                                 @UserIdInt int userId)
            throws RemoteException {
        return getPackageManager().getComponentEnabledSetting(componentName, userId);
    }

    public static void setComponentEnabledSetting(ComponentName componentName,
                                                  @EnabledState int newState,
                                                  @EnabledFlags int flags,
                                                  @UserIdInt int userId)
            throws RemoteException {
        getPackageManager().setComponentEnabledSetting(componentName, newState, flags, userId);
    }

    public static void setApplicationEnabledSetting(String packageName, @EnabledState int newState,
                                                    @EnabledFlags int flags, @UserIdInt int userId)
            throws RemoteException {
        getPackageManager().setApplicationEnabledSetting(packageName, newState, flags, userId, null);
    }

    public static String getInstallerPackage(String packageName) throws RemoteException {
        return getPackageManager().getInstallerPackageName(packageName);
    }

    public static void clearApplicationUserData(@NonNull UserPackagePair pair) throws AndroidException {
        IPackageManager pm = getPackageManager();
        ClearDataObserver obs = new ClearDataObserver();
        pm.clearApplicationUserData(pair.getPackageName(), obs, pair.getUserHandle());
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obs) {
            while (!obs.isCompleted()) {
                try {
                    obs.wait(60_000);
                } catch (InterruptedException ignore) {
                }
            }
        }
        if (!obs.isSuccessful()) {
            throw new AndroidException("Could not clear data of package " + pair);
        }
    }

    public static boolean clearApplicationUserData(@NonNull String packageName, @UserIdInt int userId) {
        try {
            clearApplicationUserData(new UserPackagePair(packageName, userId));
            return true;
        } catch (AndroidException | SecurityException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void deleteApplicationCacheFilesAsUser(UserPackagePair pair) throws AndroidException {
        IPackageManager pm = getPackageManager();
        ClearDataObserver obs = new ClearDataObserver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            pm.deleteApplicationCacheFilesAsUser(pair.getPackageName(), pair.getUserHandle(), obs);
        } else pm.deleteApplicationCacheFiles(pair.getPackageName(), obs);
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obs) {
            while (!obs.isCompleted()) {
                try {
                    obs.wait(60_000);
                } catch (InterruptedException ignore) {
                }
            }
        }
        if (!obs.isSuccessful()) {
            throw new AndroidException("Could not clear cache of package " + pair);
        }
    }

    public static boolean deleteApplicationCacheFilesAsUser(String packageName, int userId) {
        try {
            deleteApplicationCacheFilesAsUser(new UserPackagePair(packageName, userId));
            return true;
        } catch (AndroidException | SecurityException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void forceStopPackage(String packageName, int userId) throws RemoteException {
        ActivityManagerCompat.getActivityManager().forceStopPackage(packageName, userId);
    }

    @NonNull
    public static IPackageInstaller getPackageInstaller() throws RemoteException {
        return IPackageInstaller.Stub.asInterface(new ProxyBinder(getPackageManager().getPackageInstaller().asBinder()));
    }

    @SuppressWarnings("deprecation")
    public static void freeStorageAndNotify(@Nullable String volumeUuid,
                                            long freeStorageSize,
                                            @StorageManagerCompat.AllocateFlags int storageFlags)
            throws RemoteException {
        IPackageManager pm;
        ClearDataObserver obs = new ClearDataObserver();
        if (PermissionUtils.hasPermission(AppManager.getContext(), Manifest.permission.CLEAR_APP_CACHE)) {
            // Clear cache using unprivileged method: Mostly applicable for Android Lollipop
            pm = getUnprivilegedPackageManager();
        } else { // Use privileged mode
            if (Ops.isAdb() && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // IPackageManager#freeStorageAndNotify cannot be used before Android Oreo because Shell does not have
                // the permission android.permission.CLEAR_APP_CACHE
                for (int userId : Users.getUsersIds()) {
                    for (ApplicationInfo info : getInstalledApplications(0, userId)) {
                        deleteApplicationCacheFilesAsUser(info.packageName, userId);
                    }
                }
                return;
            }
            pm = getPackageManager();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pm.freeStorageAndNotify(volumeUuid, freeStorageSize, storageFlags, obs);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.freeStorageAndNotify(volumeUuid, freeStorageSize, obs);
        } else {
            pm.freeStorageAndNotify(freeStorageSize, obs);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obs) {
            while (!obs.isCompleted()) {
                try {
                    obs.wait(60_000);
                } catch (InterruptedException ignore) {
                }
            }
        }
    }

    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    private static PackageInfo getPackageInfoInternal(IPackageManager pm, String packageName, int flags, @UserIdInt int userId)
            throws RemoteException {
        if (CompatUtils.isAndroid13Beta()) {
            return pm.getPackageInfo(packageName, (long) flags, userId);
        }
        return pm.getPackageInfo(packageName, flags, userId);
    }

    public static IPackageManager getPackageManager() {
        return IPackageManager.Stub.asInterface(ProxyBinder.getService("package"));
    }

    private static IPackageManager getUnprivilegedPackageManager() {
        return IPackageManager.Stub.asInterface(ProxyBinder.getUnprivilegedService("package"));
    }
}
