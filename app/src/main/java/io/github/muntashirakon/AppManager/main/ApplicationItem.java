// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.Manifest;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.UserHandleHidden;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import aosp.libcore.util.EmptyArray;
import io.github.muntashirakon.AppManager.backup.BackupManager;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.compat.UsageStatsManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.io.Path;

/**
 * Stores an application info
 */
public class ApplicationItem extends PackageItemInfo {
    /**
     * Version name
     */
    public String versionName;
    /**
     * Version code
     */
    public long versionCode;
    /**
     * Backup info
     */
    @Nullable
    public Backup backup;
    /**
     * Application flags.
     * See {@link android.content.pm.ApplicationInfo#flags}
     */
    public int flags = 0;
    /**
     * Kernel user id.
     * See {@link android.content.pm.ApplicationInfo#uid}
     */
    public int uid = 0;
    /**
     * Shared user id name.
     * See {@link android.content.pm.PackageInfo#sharedUserId}
     */
    public String sharedUserId;
    /**
     * Application label (or name)
     */
    public String label;
    /**
     * True if debuggable, false otherwise
     */
    public boolean debuggable = false;
    /**
     * First install time
     */
    public long firstInstallTime = 0L;
    /**
     * Last update time
     */
    public Long lastUpdateTime = 0L;
    /**
     * Target SDK version
     */
    public Integer sdk;
    /**
     * Issuer and signature
     */
    @Nullable
    public Pair<String, String> sha;
    /**
     * Blocked components count
     */
    public Integer blockedCount = 0;
    public Integer trackerCount = 0;
    public Long lastActionTime = 0L;
    public Long dataUsage = 0L;
    public Long totalSize = 0L;
    public int openCount = 0;
    public Long screenTime = 0L;
    public Long lastUsageTime = 0L;
    /**
     * Whether the item is a user app (or system app)
     */
    public boolean isUser;
    /**
     * Whether the app is disabled
     */
    public boolean isDisabled;
    /**
     * Whether the app is currently running
     */
    public boolean isRunning = false;
    /**
     * Whether the app is installed
     */
    public boolean isInstalled = true;
    /**
     * Whether the app has any activities
     */
    public boolean hasActivities = false;
    /**
     * Whether the app has any splits
     */
    public boolean hasSplits = false;
    public boolean hasKeystore = false;
    public boolean usesSaf = false;
    public String ssaid = null;
    /**
     * Whether the item is selected
     */
    public boolean isSelected = false;

    @NonNull
    public int[] userIds = EmptyArray.INT;

    // Other info
    public boolean isStopped;
    public boolean isSystem;
    public boolean isPersistent;
    public boolean usesCleartextTraffic;
    public boolean canReadLogs;
    public boolean allowClearingUserData;
    public boolean isAppInactive;
    public String uidOrAppIds;
    public String issuerShortName;
    public String versionTag;
    public String appTypePostfix;
    public String sdkString;
    public long diffInstallUpdateInDays;
    public long lastBackupDays;
    public StringBuilder backupFlagsStr;

    public ApplicationItem() {
        super();
    }

    public void generateOtherInfo() {
        isStopped = (flags & ApplicationInfo.FLAG_STOPPED) != 0;
        isSystem = (flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        isPersistent = (flags & ApplicationInfo.FLAG_PERSISTENT) != 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            usesCleartextTraffic = (flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0;
        }
        for (int userId : userIds) {
            canReadLogs |= (PermissionCompat.checkPermission(Manifest.permission.READ_LOGS, packageName, userId) == PackageManager.PERMISSION_GRANTED);
            isAppInactive |= UsageStatsManagerCompat.isAppInactive(packageName, userId);
        }
        allowClearingUserData = (flags & ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA) != 0;
        // UID
        if (userIds.length > 1) {
            int appId = UserHandleHidden.getAppId(uid);
            uidOrAppIds = userIds.length + "+" + appId;
        } else if (userIds.length == 1) {
            uidOrAppIds = String.valueOf(uid);
        } else uidOrAppIds = "";
        // Cert short name
        if (sha != null) {
            try {
                issuerShortName = "CN=" + (sha.first).split("CN=", 2)[1];
            } catch (ArrayIndexOutOfBoundsException e) {
                issuerShortName = sha.first;
            }
            if (TextUtils.isEmpty(sha.second)) {
                sha = null;
            }
        }
        // Version info
        versionTag = versionName;
        if (isInstalled && (flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) == 0)
            versionTag = "_" + versionTag;
        if ((flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) versionTag = "debug" + versionTag;
        if ((flags & ApplicationInfo.FLAG_TEST_ONLY) != 0) versionTag = "~" + versionTag;
        // App type flags
        appTypePostfix = "";
        if ((flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0) appTypePostfix += "#";
        if ((flags & ApplicationInfo.FLAG_SUSPENDED) != 0) appTypePostfix += "°";
        if ((flags & ApplicationInfo.FLAG_MULTIARCH) != 0) appTypePostfix += "X";
        if ((flags & ApplicationInfo.FLAG_HAS_CODE) == 0) appTypePostfix += "0";
        if ((flags & ApplicationInfo.FLAG_VM_SAFE_MODE) != 0) appTypePostfix += "?";
        // Sdk
        if (sdk != null && sdk > 0) {
            sdkString = "SDK " + sdk;
        }
        diffInstallUpdateInDays = TimeUnit.DAYS.convert(lastUpdateTime - firstInstallTime, TimeUnit.MILLISECONDS);
        // Backup
        if (backup != null) {
            lastBackupDays = TimeUnit.DAYS.convert(System.currentTimeMillis() - backup.backupTime, TimeUnit.MILLISECONDS);
            backupFlagsStr = new StringBuilder();
            if (backup.getFlags().backupApkFiles()) backupFlagsStr.append("apk");
            if (backup.getFlags().backupData()) {
                if (backupFlagsStr.length() > 0) backupFlagsStr.append("+");
                backupFlagsStr.append("data");
            }
            if (backup.hasRules) {
                if (backupFlagsStr.length() > 0) backupFlagsStr.append("+");
                backupFlagsStr.append("rules");
            }
        }
    }

    @WorkerThread
    @Override
    public Drawable loadIcon(PackageManager pm) {
        if (userIds.length > 0) {
            try {
                ApplicationInfo info = PackageManagerCompat.getApplicationInfo(packageName,
                        MATCH_UNINSTALLED_PACKAGES
                                | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userIds[0]);
                return info.loadIcon(pm);
            } catch (Exception ignore) {
            }
        }
        if (backup != null) {
            try {
                Path iconFile = backup.getBackupPath().findFile(BackupManager.ICON_FILE);
                if (iconFile.exists()) {
                    try (InputStream is = iconFile.openInputStream()) {
                        Drawable drawable = Drawable.createFromStream(is, name);
                        if (drawable != null) {
                            return drawable;
                        }
                    }
                }
            } catch (Throwable ignore) {
            }
        }
        return pm.getDefaultActivityIcon();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApplicationItem)) return false;
        ApplicationItem item = (ApplicationItem) o;
        return packageName.equals(item.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName);
    }
}
