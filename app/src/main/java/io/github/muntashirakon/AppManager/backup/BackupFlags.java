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

import android.content.Context;
import android.text.SpannableStringBuilder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

public final class BackupFlags {
    @IntDef(flag = true, value = {
            BACKUP_NOTHING,
            BACKUP_CUSTOM_USERS,
            BACKUP_SOURCE,
            BACKUP_APK_FILES,
            BACKUP_INT_DATA,
            BACKUP_EXT_DATA,
            BACKUP_EXT_OBB_MEDIA,
            BACKUP_EXCLUDE_CACHE,
            BACKUP_EXTRAS,
            BACKUP_CACHE,
            BACKUP_MULTIPLE,
            BACKUP_RULES,
            BACKUP_NO_SIGNATURE_CHECK,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface BackupFlag {
    }

    public static final int BACKUP_NOTHING = 0;
    @SuppressWarnings("PointlessBitwiseExpression")
    @Deprecated
    public static final int BACKUP_SOURCE = 1 << 0;
    public static final int BACKUP_INT_DATA = 1 << 1;
    public static final int BACKUP_EXT_DATA = 1 << 2;
    @Deprecated
    public static final int BACKUP_EXCLUDE_CACHE = 1 << 3;
    public static final int BACKUP_RULES = 1 << 4;
    public static final int BACKUP_NO_SIGNATURE_CHECK = 1 << 5;
    public static final int BACKUP_APK_FILES = 1 << 6;
    public static final int BACKUP_EXT_OBB_MEDIA = 1 << 7;
    public static final int BACKUP_CUSTOM_USERS = 1 << 8;
    public static final int BACKUP_MULTIPLE = 1 << 9;
    public static final int BACKUP_EXTRAS = 1 << 10;
    public static final int BACKUP_CACHE = 1 << 11;

    public static final List<Integer> backupFlags = new ArrayList<>();

    @NonNull
    public static CharSequence[] getFormattedFlagNames(@NonNull Context context) {
        // Reset backup flags
        LinkedHashMap<Integer, Pair<Integer, Integer>> backupFlagsMap = getBackupFlagsMap();
        CharSequence[] flagNames = new CharSequence[backupFlags.size()];
        for (int i = 0; i < flagNames.length; ++i) {
            Pair<Integer, Integer> flagNamePair = Objects.requireNonNull(backupFlagsMap.get(backupFlags.get(i)));
            flagNames[i] = new SpannableStringBuilder(context.getText(flagNamePair.first))
                    .append("\n").append(getSecondaryText(context, getSmallerText(
                            context.getText(flagNamePair.second))));
        }
        return flagNames;
    }

    @BackupFlag
    private int flags;

    @NonNull
    public static BackupFlags fromPref() {
        int flags = (Integer) AppPref.get(AppPref.PrefKey.PREF_BACKUP_FLAGS_INT);
        return new BackupFlags(getSanitizedFlags(flags));
    }

    public BackupFlags(@BackupFlag int flags) {
        this.flags = flags;
    }

    public int getFlags() {
        return flags;
    }

    public void addFlag(@BackupFlag int flag) {
        this.flags |= flag;
    }

    public void removeFlag(@BackupFlag int flag) {
        this.flags &= ~flag;
    }

    @NonNull
    public boolean[] flagsToCheckedItems() {
        boolean[] checkedItems = new boolean[backupFlags.size()];
        Arrays.fill(checkedItems, false);
        for (int i = 0; i < checkedItems.length; ++i) {
            if ((flags & backupFlags.get(i)) != 0) checkedItems[i] = true;
        }
        return checkedItems;
    }

    public boolean isEmpty() {
        return flags == 0;
    }

    public boolean backupApkFiles() {
        return (flags & BACKUP_APK_FILES) != 0;
    }

    public boolean backupInternalData() {
        return (flags & BACKUP_INT_DATA) != 0;
    }

    public boolean backupExternalData() {
        return (flags & BACKUP_EXT_DATA) != 0;
    }

    public boolean backupMediaObb() {
        return (flags & BACKUP_EXT_OBB_MEDIA) != 0;
    }

    public boolean backupData() {
        return backupInternalData() || backupExternalData() || backupMediaObb();
    }

    public boolean backupRules() {
        return (flags & BACKUP_RULES) != 0;
    }

    public boolean backupExtras() {
        return (flags & BACKUP_EXTRAS) != 0;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean backupCache() {
        return (flags & BACKUP_CACHE) != 0 || (flags & BACKUP_EXCLUDE_CACHE) == 0;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean skipSignatureCheck() {
        return (flags & BACKUP_NO_SIGNATURE_CHECK) != 0;
    }

    public boolean backupMultiple() {
        return (flags & BACKUP_MULTIPLE) != 0;
    }

    public boolean backupCustomUsers() {
        return (flags & BACKUP_CUSTOM_USERS) != 0;
    }

    @NonNull
    public CharSequence toLocalisedString(Context context) {
        StringBuilder sb = new StringBuilder();
        boolean append = false;
        if (backupApkFiles()) {
            sb.append("APK");
            append = true;
        }
        if (backupInternalData()) {
            sb.append(append ? "+" : "").append("Int");
            append = true;
        }
        if (backupExternalData()) {
            sb.append(append ? "+" : "").append("Ext");
            append = true;
        }
        if (backupMediaObb()) {
            sb.append(append ? "+" : "").append("OBB");
            append = true;
        }
        if (backupRules()) {
            sb.append(append ? "+" : "").append("Rules");
            append = true;
        }
        if (backupExtras()) {
            sb.append(append ? "+" : "").append("Extras");
            append = true;
        }
        if (backupCache()) {
            sb.append(append ? "+" : "").append("Caches");
        }
        return sb;
    }

    @NonNull
    private static LinkedHashMap<Integer, Pair<Integer, Integer>> getBackupFlagsMap() {
        backupFlags.clear();
        return new LinkedHashMap<Integer, Pair<Integer, Integer>>() {
            {
                backupFlags.add(BACKUP_APK_FILES);
                put(BACKUP_APK_FILES, new Pair<>(R.string.backup_apk_files, R.string.backup_apk_files_description));
                if (AppPref.isRootEnabled()) {
                    backupFlags.add(BACKUP_INT_DATA);
                    put(BACKUP_INT_DATA, new Pair<>(R.string.internal_data, R.string.backup_internal_data_description));
                }
                backupFlags.add(BACKUP_EXT_DATA);
                put(BACKUP_EXT_DATA, new Pair<>(R.string.external_data, R.string.backup_external_data_description));
                backupFlags.add(BACKUP_EXT_OBB_MEDIA);
                put(BACKUP_EXT_OBB_MEDIA, new Pair<>(R.string.backup_obb_media, R.string.backup_obb_media_description));
                backupFlags.add(BACKUP_CACHE);
                put(BACKUP_CACHE, new Pair<>(R.string.cache, R.string.backup_cache_description));
                if (AppPref.isRootEnabled()) {
                    // Display extra backups only in root mode
                    backupFlags.add(BACKUP_EXTRAS);
                    put(BACKUP_EXTRAS, new Pair<>(R.string.backup_extras, R.string.backup_extras_description));
                    backupFlags.add(BACKUP_RULES);
                    put(BACKUP_RULES, new Pair<>(R.string.rules, R.string.backup_rules_description));
                }
                backupFlags.add(BACKUP_MULTIPLE);
                put(BACKUP_MULTIPLE, new Pair<>(R.string.backup_multiple, R.string.backup_multiple_description));
                if (Users.getUsersHandles().length > 1) {
                    // Display custom users only if multiple users present
                    backupFlags.add(BACKUP_CUSTOM_USERS);
                    put(BACKUP_CUSTOM_USERS, new Pair<>(R.string.backup_custom_users, R.string.backup_custom_users_description));
                }
                backupFlags.add(BACKUP_NO_SIGNATURE_CHECK);
                put(BACKUP_NO_SIGNATURE_CHECK, new Pair<>(R.string.skip_signature_checks, R.string.backup_skip_signature_checks_description));
            }
        };
    }

    /**
     * Remove unsupported flags from the given list of flags
     */
    private static int getSanitizedFlags(int flags) {
        if (!AppPref.isRootEnabled()) {
            flags &= ~BACKUP_INT_DATA;
            flags &= ~BACKUP_EXTRAS;
            flags &= ~BACKUP_RULES;
        }
        if (Users.getUsersHandles().length == 1) {
            flags &= ~BACKUP_CUSTOM_USERS;
        }
        return flags | BACKUP_EXCLUDE_CACHE;
    }
}
