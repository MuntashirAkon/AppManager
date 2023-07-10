// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.UserHandleHidden;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

import java.io.Serializable;
import java.util.Objects;

import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

@SuppressWarnings("NotNullFieldNotInitialized")
@Entity(tableName = "app", primaryKeys = {"package_name", "user_id"})
public class App implements Serializable {
    @ColumnInfo(name = "package_name")
    @NonNull
    public String packageName;

    @ColumnInfo(name = "user_id", defaultValue = "" + UserHandleHidden.USER_NULL)
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

    @ColumnInfo(name = "has_keystore", defaultValue = "false")
    public boolean hasKeystore;

    @ColumnInfo(name = "uses_saf", defaultValue = "false")
    public boolean usesSaf;

    @ColumnInfo(name = "ssaid", defaultValue = "")
    public String ssaid;

    @ColumnInfo(name = "code_size", defaultValue = "0")
    public long codeSize;

    @ColumnInfo(name = "data_size", defaultValue = "0")
    public long dataSize;

    @ColumnInfo(name = "mobile_data", defaultValue = "0")
    public long mobileDataUsage;

    @ColumnInfo(name = "wifi_data", defaultValue = "0")
    public long wifiDataUsage;

    @ColumnInfo(name = "rules_count", defaultValue = "0")
    public int rulesCount;

    @ColumnInfo(name = "tracker_count", defaultValue = "0")
    public int trackerCount;

    @ColumnInfo(name = "open_count", defaultValue = "0")
    public int openCount;

    @ColumnInfo(name = "screen_time", defaultValue = "0")
    public long screenTime;

    @ColumnInfo(name = "last_usage_time", defaultValue = "0")
    public long lastUsageTime;

    @ColumnInfo(name = "last_action_time", defaultValue = "0")
    public long lastActionTime;

    public boolean isSystemApp() {
        return (flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    public boolean isDebuggable() {
        return (flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    @NonNull
    public static App fromPackageInfo(@NonNull Context context, @NonNull PackageInfo packageInfo) {
        App app = new App();
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        app.packageName = applicationInfo.packageName;
        app.uid = applicationInfo.uid;
        app.userId = UserHandleHidden.getUserId(app.uid);
        app.isInstalled = ApplicationInfoCompat.isInstalled(applicationInfo);
        app.flags = applicationInfo.flags;
        app.isEnabled = !FreezeUtils.isFrozen(applicationInfo);
        app.packageLabel = ApplicationInfoCompat.loadLabelSafe(applicationInfo, context.getPackageManager()).toString();
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
    public static App fromBackup(@NonNull Backup backup) {
        App app = new App();
        app.packageName = backup.packageName;
        app.uid = 0;
        app.userId = backup.userId;
        app.isInstalled = false;
        if (backup.isSystem) {
            app.flags |= ApplicationInfo.FLAG_SYSTEM;
        }
        app.isEnabled = true;
        app.packageLabel = backup.label;
        app.sdk = 0;
        app.versionName = backup.versionName;
        app.versionCode = backup.versionCode;
        app.sharedUserId = null;
        app.certName = "";
        app.certAlgo = "";
        app.firstInstallTime = backup.backupTime;
        app.lastUpdateTime = backup.backupTime;
        app.hasActivities = false;
        app.hasSplits = backup.hasSplits;
        app.rulesCount = 0;
        app.trackerCount = 0;
        app.lastActionTime = backup.backupTime;
        app.hasKeystore = backup.hasKeyStore;
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
}
