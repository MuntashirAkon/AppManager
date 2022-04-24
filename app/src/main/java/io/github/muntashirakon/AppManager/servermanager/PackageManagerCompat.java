// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.rikka.tools.refine.Refine;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.compat.StorageManagerCompat;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

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
        return AppManager.getIPackageManager().getInstalledPackages(flags, userHandle).getList();
    }

    @WorkerThread
    public static List<ApplicationInfo> getInstalledApplications(int flags, @UserIdInt int userHandle)
            throws RemoteException {
        return AppManager.getIPackageManager().getInstalledApplications(flags, userHandle).getList();
    }

    @NonNull
    public static PackageInfo getPackageInfo(String packageName, int flags, @UserIdInt int userHandle)
            throws RemoteException, PackageManager.NameNotFoundException {
        IPackageManager pm = AppManager.getIPackageManager();
        PackageInfo info = null;
        try {
            info = pm.getPackageInfo(packageName, flags, userHandle);
        } catch (DeadObjectException ignore) {
        }
        if (info == null) {
            // The app might not be loaded properly due parcel size limit, try to load components separately.
            // first check the existence of the package
            int strippedFlags = flags & ~(PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                    | PackageManager.GET_PROVIDERS | PackageManager.GET_RECEIVERS | PackageManager.GET_PERMISSIONS);
            info = pm.getPackageInfo(packageName, strippedFlags, userHandle);
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
                PackageInfo info1 = pm.getPackageInfo(packageName, newFlags, userHandle);
                if (info1 != null) activities = info1.activities;
            }
            ServiceInfo[] services = null;
            if ((flags & PackageManager.GET_SERVICES) != 0) {
                int newFlags = flags & ~(PackageManager.GET_ACTIVITIES | PackageManager.GET_PROVIDERS
                        | PackageManager.GET_RECEIVERS | PackageManager.GET_PERMISSIONS);
                PackageInfo info1 = pm.getPackageInfo(packageName, newFlags, userHandle);
                if (info1 != null) services = info1.services;
            }
            ProviderInfo[] providers = null;
            if ((flags & PackageManager.GET_PROVIDERS) != 0) {
                int newFlags = flags & ~(PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                        | PackageManager.GET_RECEIVERS | PackageManager.GET_PERMISSIONS);
                PackageInfo info1 = pm.getPackageInfo(packageName, newFlags, userHandle);
                if (info1 != null) providers = info1.providers;
            }
            ActivityInfo[] receivers = null;
            if ((flags & PackageManager.GET_RECEIVERS) != 0) {
                int newFlags = flags & ~(PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                        | PackageManager.GET_PROVIDERS | PackageManager.GET_PERMISSIONS);
                PackageInfo info1 = pm.getPackageInfo(packageName, newFlags, userHandle);
                if (info1 != null) receivers = info1.receivers;
            }
            PermissionInfo[] permissions = null;
            if ((flags & PackageManager.GET_PERMISSIONS) != 0) {
                int newFlags = flags & ~(PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                        | PackageManager.GET_PROVIDERS | PackageManager.GET_RECEIVERS);
                PackageInfo info1 = pm.getPackageInfo(packageName, newFlags, userHandle);
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

    @NonNull
    public static ApplicationInfo getApplicationInfo(String packageName, int flags, @UserIdInt int userHandle)
            throws RemoteException {
        return AppManager.getIPackageManager().getApplicationInfo(packageName, flags, userHandle);
    }

    @SuppressWarnings("deprecation")
    @NonNull
    public static List<ResolveInfo> queryIntentActivities(@NonNull Context context, @NonNull Intent intent, int flags,
                                                          @UserIdInt int userId)
            throws RemoteException {
        IPackageManager pm = AppManager.getIPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            IPackageManagerN pmN = Refine.unsafeCast(pm);
            ParceledListSlice<ResolveInfo> resolveInfoList = pmN.queryIntentActivities(intent,
                    intent.resolveTypeIfNeeded(context.getContentResolver()), flags, userId);
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
        return AppManager.getIPackageManager().getComponentEnabledSetting(componentName, userId);
    }

    public static void setComponentEnabledSetting(ComponentName componentName,
                                                  @EnabledState int newState,
                                                  @EnabledFlags int flags,
                                                  @UserIdInt int userId)
            throws RemoteException {
        AppManager.getIPackageManager().setComponentEnabledSetting(componentName, newState, flags, userId);
    }

    public static void setApplicationEnabledSetting(String packageName, @EnabledState int newState,
                                                    @EnabledFlags int flags, @UserIdInt int userId)
            throws RemoteException {
        AppManager.getIPackageManager().setApplicationEnabledSetting(packageName, newState, flags, userId, null);
    }

    public static String getInstallerPackage(String packageName) throws RemoteException {
        return AppManager.getIPackageManager().getInstallerPackageName(packageName);
    }

    public static void clearApplicationUserData(@NonNull UserPackagePair pair) throws AndroidException, InterruptedException {
        IPackageManager pm = AppManager.getIPackageManager();
        CountDownLatch dataClearWatcher = new CountDownLatch(1);
        AtomicBoolean isSuccess = new AtomicBoolean(false);
        pm.clearApplicationUserData(pair.getPackageName(), new IPackageDataObserver.Stub() {
            @Override
            public void onRemoveCompleted(String packageName, boolean succeeded) {
                isSuccess.set(succeeded);
                dataClearWatcher.countDown();
            }
        }, pair.getUserHandle());
        dataClearWatcher.await();
        if (!isSuccess.get()) {
            throw new AndroidException("Could not clear data of package " + pair);
        }
    }

    public static boolean clearApplicationUserData(@NonNull String packageName, @UserIdInt int userId) {
        IPackageManager pm = AppManager.getIPackageManager();
        CountDownLatch dataClearWatcher = new CountDownLatch(1);
        AtomicBoolean isSuccess = new AtomicBoolean(false);
        try {
            pm.clearApplicationUserData(packageName, new IPackageDataObserver.Stub() {
                @Override
                public void onRemoveCompleted(String packageName, boolean succeeded) {
                    isSuccess.set(succeeded);
                    dataClearWatcher.countDown();
                }
            }, userId);
            dataClearWatcher.await();
        } catch (RemoteException | SecurityException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        return isSuccess.get();
    }

    public static void deleteApplicationCacheFilesAsUser(UserPackagePair pair) throws AndroidException, InterruptedException {
        IPackageManager pm = AppManager.getIPackageManager();
        CountDownLatch dataClearWatcher = new CountDownLatch(1);
        AtomicBoolean isSuccess = new AtomicBoolean(false);
        IPackageDataObserver observer = new IPackageDataObserver.Stub() {
            @Override
            public void onRemoveCompleted(String packageName, boolean succeeded) {
                dataClearWatcher.countDown();
                isSuccess.set(succeeded);
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            pm.deleteApplicationCacheFilesAsUser(pair.getPackageName(), pair.getUserHandle(), observer);
        } else pm.deleteApplicationCacheFiles(pair.getPackageName(), observer);
        dataClearWatcher.await();
        if (!isSuccess.get()) {
            throw new AndroidException("Could not clear cache of package " + pair);
        }
    }

    public static boolean deleteApplicationCacheFilesAsUser(String packageName, int userId) {
        IPackageManager pm = AppManager.getIPackageManager();
        CountDownLatch dataClearWatcher = new CountDownLatch(1);
        AtomicBoolean isSuccess = new AtomicBoolean(false);
        try {
            IPackageDataObserver observer = new IPackageDataObserver.Stub() {
                @Override
                public void onRemoveCompleted(String packageName, boolean succeeded) {
                    dataClearWatcher.countDown();
                    isSuccess.set(succeeded);
                }
            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                pm.deleteApplicationCacheFilesAsUser(packageName, userId, observer);
            } else pm.deleteApplicationCacheFiles(packageName, observer);
            dataClearWatcher.await();
        } catch (RemoteException | SecurityException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        return isSuccess.get();
    }

    public static void forceStopPackage(String packageName, int userId) throws RemoteException {
        ActivityManagerCompat.getActivityManager().forceStopPackage(packageName, userId);
    }

    @NonNull
    public static IPackageInstaller getPackageInstaller(@NonNull IPackageManager pm) throws RemoteException {
        return IPackageInstaller.Stub.asInterface(new ProxyBinder(pm.getPackageInstaller().asBinder()));
    }

    @SuppressWarnings("deprecation")
    public static void freeStorageAndNotify(@Nullable String volumeUuid,
                                            long freeStorageSize,
                                            @StorageManagerCompat.AllocateFlags int storageFlags,
                                            IPackageDataObserver observer)
            throws RemoteException {
        IPackageManager pm = AppManager.getIPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pm.freeStorageAndNotify(volumeUuid, freeStorageSize, storageFlags, observer);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.freeStorageAndNotify(volumeUuid, freeStorageSize, observer);
        } else {
            pm.freeStorageAndNotify(freeStorageSize, observer);
        }
    }
}
