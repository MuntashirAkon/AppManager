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

package io.github.muntashirakon.AppManager.backup;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.utils.AppPref;

public final class BackupFlags {
    @IntDef(flag = true, value = {
            BACKUP_NOTHING,
            BACKUP_ALL_USERS,
            BACKUP_SOURCE,
            BACKUP_SOURCE_APK_ONLY,
            BACKUP_DATA,
            BACKUP_EXT_DATA,
            BACKUP_EXT_OBB_MEDIA,
            BACKUP_EXCLUDE_CACHE,
            BACKUP_MULTIPLE,
            BACKUP_RULES,
            BACKUP_NO_SIGNATURE_CHECK,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface BackupFlag {
    }

    public static final int BACKUP_NOTHING = 0;
    @SuppressWarnings("PointlessBitwiseExpression")
    public static final int BACKUP_SOURCE = 1 << 0;
    public static final int BACKUP_DATA = 1 << 1;
    public static final int BACKUP_EXT_DATA = 1 << 2;
    public static final int BACKUP_EXCLUDE_CACHE = 1 << 3;
    public static final int BACKUP_RULES = 1 << 4;
    public static final int BACKUP_NO_SIGNATURE_CHECK = 1 << 5;
    public static final int BACKUP_SOURCE_APK_ONLY = 1 << 6;
    public static final int BACKUP_EXT_OBB_MEDIA = 1 << 7;
    public static final int BACKUP_ALL_USERS = 1 << 8;
    public static final int BACKUP_MULTIPLE = 1 << 9;
    public static final int BACKUP_PERMISSIONS = 1 << 10;

    public static final int BACKUP_TOTAL = 11;

    @BackupFlag
    private int flags;

    @NonNull
    public static BackupFlags fromPref() {
        return new BackupFlags((Integer) AppPref.get(AppPref.PrefKey.PREF_BACKUP_FLAGS_INT));
    }

    BackupFlags(@BackupFlag int flags) {
        this.flags = flags;
    }

    public int getFlags() {
        return flags;
    }

    public void addFlag(@BackupFlag int flag) {
        this.flags |= (1 << flag);
    }

    public void removeFlag(@BackupFlag int flag) {
        this.flags &= ~(1 << flag);
    }

    @NonNull
    public boolean[] flagsToCheckedItems() {
        boolean[] checkedItems = new boolean[BackupFlags.BACKUP_TOTAL];
        Arrays.fill(checkedItems, false);
        for (int i = 0; i < BackupFlags.BACKUP_TOTAL; ++i) {
            if ((flags & (1 << i)) != 0) checkedItems[i] = true;
        }
        return checkedItems;
    }

    public boolean isEmpty() {
        return flags == 0;
    }

    public boolean backupSource() {
        return (flags & BACKUP_SOURCE) != 0;
    }

    public boolean backupOnlyApk() {
        return (flags & BACKUP_SOURCE_APK_ONLY) != 0;
    }

    public boolean backupData() {
        return (flags & BACKUP_DATA) != 0;
    }

    public boolean backupExtData() {
        return (flags & BACKUP_EXT_DATA) != 0;
    }

    public boolean backupMediaObb() {
        return (flags & BACKUP_EXT_OBB_MEDIA) != 0;
    }

    public boolean backupRules() {
        return (flags & BACKUP_RULES) != 0;
    }

    public boolean backupPermissions() {
        return (flags & BACKUP_PERMISSIONS) != 0;
    }

    public boolean excludeCache() {
        return (flags & BACKUP_EXCLUDE_CACHE) != 0;
    }

    public boolean skipSignatureCheck() {
        return (flags & BACKUP_NO_SIGNATURE_CHECK) != 0;
    }

    public boolean backupMultiple() {
        return (flags & BACKUP_MULTIPLE) != 0;
    }

    public boolean backupAllUsers() {
        return (flags & BACKUP_ALL_USERS) != 0;
    }
}
