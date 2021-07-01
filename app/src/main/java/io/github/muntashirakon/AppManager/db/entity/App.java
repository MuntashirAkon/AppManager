// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

import java.io.File;
import java.io.Serializable;
import java.util.Objects;

import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.Utils;

@SuppressWarnings("NotNullFieldNotInitialized")
@Entity(tableName = "app", primaryKeys = {"package_name", "user_id"})
public class App implements Serializable {
    @ColumnInfo(name = "package_name")
    @NonNull
    public String packageName;

    @ColumnInfo(name = "user_id", defaultValue = "" + Users.USER_NULL)
    public int userId;

    @ColumnInfo(name = "label")
    public String packageLabel;

    @ColumnInfo(name = "version_name")
    public String versionName;

    @ColumnInfo(name = "version_code")
    public long versionCode;

    @ColumnInfo(name = "flags", defaultValue = "0")
    public int flags;

    @ColumnInfo(name = "uid", defaultValue = "0")
    public int uid;

    @ColumnInfo(name = "shared_uid", defaultValue = "NULL")
    @Nullable
    public String sharedUserId;

    @ColumnInfo(name = "first_install_time", defaultValue = "0")
    public long firstInstallTime;

    @ColumnInfo(name = "last_update_time", defaultValue = "0")
    public long lastUpdateTime;

    @ColumnInfo(name = "target_sdk", defaultValue = "0")
    public int sdk;

    @ColumnInfo(name = "cert_name", defaultValue = "''")
    public String certName;

    @ColumnInfo(name = "cert_algo", defaultValue = "''")
    public String certAlgo;

    @ColumnInfo(name = "is_installed", defaultValue = "true")
    public boolean isInstalled;

    @ColumnInfo(name = "is_enabled", defaultValue = "false")
    public boolean isEnabled;

    @ColumnInfo(name = "has_activities", defaultValue = "false")
    public boolean hasActivities;

    @ColumnInfo(name = "has_splits", defaultValue = "false")
    public boolean hasSplits;

    @ColumnInfo(name = "rules_count", defaultValue = "0")
    public int rulesCount;

    @ColumnInfo(name = "tracker_count", defaultValue = "0")
    public int trackerCount;

    @ColumnInfo(name = "last_action_time", defaultValue = "0")
    public long lastActionTime;

    @NonNull
    public static App fromApp(@NonNull App app) {
        App newApp = new App();
        newApp.packageName = app.packageName;
        newApp.uid = app.uid;
        newApp.userId = app.userId;
        newApp.isInstalled = app.isInstalled;
        newApp.flags = app.flags;
        newApp.isEnabled = app.isEnabled;
        newApp.packageLabel = app.packageLabel;
        newApp.sdk = app.sdk;
        newApp.versionName = app.versionName;
        newApp.versionCode = app.versionCode;
        newApp.sharedUserId = app.sharedUserId;
        newApp.certName = app.certName;
        newApp.certAlgo = app.certAlgo;
        newApp.firstInstallTime = app.firstInstallTime;
        newApp.lastUpdateTime = app.lastUpdateTime;
        newApp.hasActivities = app.hasActivities;
        newApp.hasSplits = app.hasSplits;
        newApp.rulesCount = 0;
        newApp.trackerCount = app.trackerCount;
        newApp.lastActionTime = System.currentTimeMillis();
        return newApp;
    }

    @NonNull
    public static App fromPackageInfo(@NonNull Context context, @NonNull PackageInfo packageInfo) {
        App app = new App();
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        app.packageName = applicationInfo.packageName;
        app.uid = applicationInfo.uid;
        app.userId = Users.getUserId(app.uid);
        app.isInstalled = (applicationInfo.flags & ApplicationInfo.FLAG_INSTALLED) != 0
                && applicationInfo.publicSourceDir != null && new File(applicationInfo.publicSourceDir).exists();
        app.flags = applicationInfo.flags;
        app.isEnabled = applicationInfo.enabled;
        app.packageLabel = applicationInfo.loadLabel(context.getPackageManager()).toString();
        app.sdk = applicationInfo.targetSdkVersion;
        app.versionName = packageInfo.versionName;
        app.versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
        app.sharedUserId = packageInfo.sharedUserId;
        Pair<String, String> issuerAndAlgoPair = Utils.getIssuerAndAlg(packageInfo);
        app.certName = issuerAndAlgoPair.first;
        app.certAlgo = issuerAndAlgoPair.second;
        app.firstInstallTime = packageInfo.firstInstallTime;
        app.lastUpdateTime = packageInfo.lastUpdateTime;
        app.hasActivities = packageInfo.activities != null;
        app.hasSplits = applicationInfo.splitSourceDirs != null;
        app.rulesCount = 0;
        app.trackerCount = ComponentUtils.getTrackerComponentsForPackage(packageInfo).size();
        app.lastActionTime = System.currentTimeMillis();
        return app;
    }

    @NonNull
    public static App fromBackupMetadata(@NonNull MetadataManager.Metadata metadata) {
        App app = new App();
        app.packageName = metadata.packageName;
        app.uid = 0;
        app.userId = metadata.userHandle;
        app.isInstalled = false;
        if (metadata.isSystem) {
            app.flags |= ApplicationInfo.FLAG_SYSTEM;
        }
        app.isEnabled = true;
        app.packageLabel = metadata.label;
        app.sdk = 0;
        app.versionName = metadata.versionName;
        app.versionCode = metadata.versionCode;
        app.sharedUserId = null;
        app.certName = "";
        app.certAlgo = "";
        app.firstInstallTime = metadata.backupTime;
        app.lastUpdateTime = metadata.backupTime;
        app.hasActivities = false;
        app.hasSplits = metadata.isSplitApk;
        app.rulesCount = 0;
        app.trackerCount = 0;
        app.lastActionTime = metadata.backupTime;
        return app;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof App)) return false;
        App app = (App) o;
        return userId == app.userId && packageName.equals(app.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, userId);
    }

    public int getHashCode() {
        return Objects.hash(packageName, userId, packageLabel, versionName, versionCode, flags, uid, sharedUserId, firstInstallTime, lastUpdateTime, sdk, certName, certAlgo, isInstalled, isEnabled, hasActivities, hasSplits, rulesCount, lastActionTime);
    }
}
