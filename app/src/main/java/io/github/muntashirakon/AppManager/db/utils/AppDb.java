// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.dao.AppDao;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver;
import io.github.muntashirakon.AppManager.users.Users;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagMatchUninstalled;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagSigningInfo;

public class AppDb {
    public static final String TAG = AppDb.class.getSimpleName();

    private static final Object sLock = new Object();

    private final AppDao appDao;

    public AppDb() {
        appDao = AppManager.getDb().appDao();
    }

    public List<App> getAllApplications() {
        synchronized (sLock) {
            return appDao.getAll();
        }
    }

    @WorkerThread
    public void loadInstalledOrBackedUpApplications(@NonNull Context context) {
        updateBackups(context);
        updateApplications(context);
    }

    @WorkerThread
    public void updateBackups(@NonNull Context context) {
        synchronized (sLock) {
            Map<String, Backup> backups = getBackups(true);
            // Interrupt thread on request
            if (Thread.currentThread().isInterrupted()) return;

            Set<App> allApps = new HashSet<>(appDao.getAll());
            List<App> newApps = new ArrayList<>();
            // Add the backup items, i.e., items that aren't already present
            for (Backup backup : backups.values()) {
                // Interrupt thread on request
                if (Thread.currentThread().isInterrupted()) return;

                if (backup == null) continue;
                App app = App.fromBackup(backup);
                if (allApps.contains(app)) {
                    // Already has this entry, skip
                    continue;
                }
                try (ComponentsBlocker cb = ComponentsBlocker.getInstance(app.packageName, app.userId, true)) {
                    app.rulesCount = cb.entryCount();
                }
                newApps.add(app);
            }
            appDao.insert(newApps);
            if (newApps.size() > 0) {
                // New apps
                Intent intent = new Intent(PackageChangeReceiver.ACTION_PACKAGE_ADDED);
                intent.setPackage(context.getPackageName());
                intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, getPackageNamesFromApps(newApps));
                context.sendBroadcast(intent);
            }
        }
    }

    @WorkerThread
    public void updateApplications(@NonNull Context context) {
        synchronized (sLock) {
            Map<String, Backup> backups = getBackups(false);
            // Interrupt thread on request
            if (Thread.currentThread().isInterrupted()) return;

            List<App> newApps = new ArrayList<>();
            for (int userId : Users.getUsersIds()) {
                // Interrupt thread on request
                if (Thread.currentThread().isInterrupted()) return;

                List<PackageInfo> packageInfoList;
                try {
                    packageInfoList = PackageManagerCompat.getInstalledPackages(flagSigningInfo
                            | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                            | PackageManager.GET_SERVICES | flagDisabledComponents | flagMatchUninstalled, userId);
                } catch (Exception e) {
                    Log.e(TAG, "Could not retrieve package info list for user " + userId, e);
                    continue;
                }

                for (PackageInfo packageInfo : packageInfoList) {
                    // Interrupt thread on request
                    if (Thread.currentThread().isInterrupted()) return;

                    App app = App.fromPackageInfo(context, packageInfo);
                    backups.remove(packageInfo.packageName);
                    try (ComponentsBlocker cb = ComponentsBlocker.getInstance(app.packageName, app.userId, true)) {
                        app.rulesCount = cb.entryCount();
                    }
                    newApps.add(app);
                }
            }
            // Add rest of the backup items, i.e., items that aren't installed
            for (Backup backup : backups.values()) {
                // Interrupt thread on request
                if (Thread.currentThread().isInterrupted()) return;

                if (backup == null) continue;
                App app = App.fromBackup(backup);
                try (ComponentsBlocker cb = ComponentsBlocker.getInstance(app.packageName, app.userId, true)) {
                    app.rulesCount = cb.entryCount();
                }
                newApps.add(app);
            }
            // Add new and delete old items
            List<App> oldApps = appDao.getAll();
            List<App> updatedApps = new ArrayList<>();
            ListIterator<App> oldAppsIterator = oldApps.listIterator();
            while (oldAppsIterator.hasNext()) {
                // Interrupt thread on request
                if (Thread.currentThread().isInterrupted()) return;

                App oldApp = oldAppsIterator.next();
                int index = newApps.indexOf(oldApp);
                if (index != -1) {
                    App newApp = newApps.get(index);
                    // DB already has this app
                    if (oldApp.isDifferentFrom(newApp)) {
                        // Change detected, replace old with new
                        updatedApps.add(newApp);
                    } // else no change between two versions, the app don't have to be updated or deleted
                    oldAppsIterator.remove();
                    newApps.remove(index);
                } // else the app is new
            }
            appDao.delete(oldApps);
            appDao.insert(newApps);
            appDao.insert(updatedApps);
            if (oldApps.size() > 0) {
                // Delete broadcast
                Intent intent = new Intent(PackageChangeReceiver.ACTION_PACKAGE_REMOVED);
                intent.setPackage(context.getPackageName());
                intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, getPackageNamesFromApps(oldApps));
                context.sendBroadcast(intent);
            }
            if (newApps.size() > 0) {
                // New apps
                Intent intent = new Intent(PackageChangeReceiver.ACTION_PACKAGE_ADDED);
                intent.setPackage(context.getPackageName());
                intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, getPackageNamesFromApps(newApps));
                context.sendBroadcast(intent);
            }
            if (updatedApps.size() > 0) {
                // Altered apps
                Intent intent = new Intent(PackageChangeReceiver.ACTION_PACKAGE_ALTERED);
                intent.setPackage(context.getPackageName());
                intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, getPackageNamesFromApps(updatedApps));
                context.sendBroadcast(intent);
            }
        }
    }

    @WorkerThread
    @NonNull
    private Map<String, Backup> getBackups(boolean loadBackups) {
        if (loadBackups) {
            try {
                // Very long operation
                return BackupUtils.storeAllAndGetLatestBackupMetadata();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            return BackupUtils.getAllLatestBackupMetadataFromDb();
        }
        return Collections.emptyMap();
    }

    @NonNull
    private static String[] getPackageNamesFromApps(@NonNull List<App> apps) {
        HashSet<String> packages = new HashSet<>(apps.size());
        for (App app : apps) {
            packages.add(app.packageName);
        }
        return packages.toArray(new String[0]);
    }
}
