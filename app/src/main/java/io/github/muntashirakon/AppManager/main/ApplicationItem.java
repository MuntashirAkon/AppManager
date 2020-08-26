/*
 * Copyright (C) 2020 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.main;

import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import java.util.Objects;

import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.utils.Tuple;

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
    public @Nullable MetadataManager.MetadataV1 metadataV1;
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
    public @Nullable Tuple<String, String> sha;
    /**
     * Blocked components count
     */
    public Integer blockedCount = 0;
    /**
     * Whether the item is a user app (or system app)
     */
    public boolean isUser;
    /**
     * Whether the app is disabled
     */
    public boolean isDisabled;
    /**
     * Whether the app is installed
     */
    public boolean isInstalled = true;
    /**
     * Whether the app has any activities
     */
    public boolean hasActivities = false;
    /**
     * Whether the item is selected
     */
    public boolean isSelected = false;

    public ApplicationItem() {
        super();
    }

    public ApplicationItem(PackageItemInfo orig) {
        super(orig);
    }

    @Override
    public Drawable loadIcon(PackageManager pm) {
        if (isInstalled) {
            try {
                return pm.getApplicationIcon(packageName);
            } catch (PackageManager.NameNotFoundException ignore) {}
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
