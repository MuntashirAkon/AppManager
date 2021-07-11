// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.InputStream;
import java.util.Objects;

import aosp.libcore.util.EmptyArray;
import io.github.muntashirakon.AppManager.backup.BackupManager;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
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
    /**
     * Whether the item is selected
     */
    public boolean isSelected = false;

    public int[] userHandles = EmptyArray.INT;

    public ApplicationItem() {
        super();
    }

    public ApplicationItem(PackageItemInfo orig) {
        super(orig);
    }

    @WorkerThread
    @Override
    public Drawable loadIcon(PackageManager pm) {
        if (userHandles.length > 0) {
            try {
                ApplicationInfo info = PackageManagerCompat.getApplicationInfo(packageName,
                        PackageUtils.flagMatchUninstalled, userHandles[0]);
                return info.loadIcon(pm);
            } catch (Exception ignore) {
            }
        }
        if (backup != null) {
            try {
                Path iconFile = backup.getBackupPath().findFile(BackupManager.ICON_FILE);
                if (iconFile.exists()) {
                    try (InputStream is = iconFile.openInputStream()) {
                        return Drawable.createFromStream(is, name);
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
