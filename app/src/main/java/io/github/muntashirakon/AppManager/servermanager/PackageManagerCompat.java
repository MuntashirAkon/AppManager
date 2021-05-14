/*
 * Copyright (C) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageInstaller;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
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

    private static final int workingFlags = PackageManager.GET_META_DATA | PackageUtils.flagMatchUninstalled;

    @WorkerThread
    public static List<PackageInfo> getInstalledPackages(int flags, @UserIdInt int userHandle)
            throws RemoteException {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M && (flags & workingFlags) != 0) {
            // Need workaround
            List<ApplicationInfo> applicationInfoList = getInstalledApplications(flags & workingFlags, userHandle);
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
        PackageInfo info = pm.getPackageInfo(packageName, flags, userHandle);
        if (info == null) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                // The app might not be loaded properly due parcel size limit, try to load components separately.
                ActivityInfo[] activities = null;
                if ((flags & PackageManager.GET_ACTIVITIES) != 0) {
                    int newFlags = flags & ~(PackageManager.GET_SERVICES | PackageManager.GET_PROVIDERS
                            | PackageManager.GET_RECEIVERS | PackageManager.GET_PERMISSIONS);
                    PackageInfo info1 = pm.getPackageInfo(packageName, newFlags, userHandle);
                    if (info1 != null) activities = info1.activities;
                    flags &= ~PackageManager.GET_ACTIVITIES;
                }
                ServiceInfo[] services = null;
                if ((flags & PackageManager.GET_SERVICES) != 0) {
                    int newFlags = flags & ~(PackageManager.GET_ACTIVITIES | PackageManager.GET_PROVIDERS
                            | PackageManager.GET_RECEIVERS | PackageManager.GET_PERMISSIONS);
                    PackageInfo info1 = pm.getPackageInfo(packageName, newFlags, userHandle);
                    if (info1 != null) services = info1.services;
                    flags &= ~PackageManager.GET_SERVICES;
                }
                ProviderInfo[] providers = null;
                if ((flags & PackageManager.GET_PROVIDERS) != 0) {
                    int newFlags = flags & ~(PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                            | PackageManager.GET_RECEIVERS | PackageManager.GET_PERMISSIONS);
                    PackageInfo info1 = pm.getPackageInfo(packageName, newFlags, userHandle);
                    if (info1 != null) providers = info1.providers;
                    flags &= ~PackageManager.GET_PROVIDERS;
                }
                ActivityInfo[] receivers = null;
                if ((flags & PackageManager.GET_RECEIVERS) != 0) {
                    int newFlags = flags & ~(PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                            | PackageManager.GET_PROVIDERS | PackageManager.GET_PERMISSIONS);
                    PackageInfo info1 = pm.getPackageInfo(packageName, newFlags, userHandle);
                    if (info1 != null) receivers = info1.receivers;
                    flags &= ~PackageManager.GET_RECEIVERS;
                }
                PermissionInfo[] permissions = null;
                if ((flags & PackageManager.GET_PERMISSIONS) != 0) {
                    int newFlags = flags & ~(PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                            | PackageManager.GET_PROVIDERS | PackageManager.GET_RECEIVERS);
                    PackageInfo info1 = pm.getPackageInfo(packageName, newFlags, userHandle);
                    if (info1 != null) permissions = info1.permissions;
                    flags &= ~PackageManager.GET_PERMISSIONS;
                }
                info = pm.getPackageInfo(packageName, flags, userHandle);
                info.activities = activities;
                info.services = services;
                info.providers = providers;
                info.receivers = receivers;
                info.permissions = permissions;
                if (info != null) {
                    return info;
                }
            }
            throw new PackageManager.NameNotFoundException(packageName + " not found.");
        }
        return info;
    }

    @NonNull
    public static ApplicationInfo getApplicationInfo(String packageName, int flags, @UserIdInt int userHandle)
            throws RemoteException {
        return AppManager.getIPackageManager().getApplicationInfo(packageName, flags, userHandle);
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

    public static boolean clearApplicationUserData(String packageName, @UserIdInt int userId) {
        IPackageManager pm = AppManager.getIPackageManager();
        CountDownLatch dataClearWatcher = new CountDownLatch(1);
        AtomicBoolean isSuccess = new AtomicBoolean(false);
        try {
            pm.clearApplicationUserData(packageName, new IPackageDataObserver.Stub() {
                @Override
                public void onRemoveCompleted(String packageName, boolean succeeded) {
                    dataClearWatcher.countDown();
                    isSuccess.set(succeeded);
                }
            }, userId);
            dataClearWatcher.await();
        } catch (RemoteException | SecurityException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        return isSuccess.get();
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
}
