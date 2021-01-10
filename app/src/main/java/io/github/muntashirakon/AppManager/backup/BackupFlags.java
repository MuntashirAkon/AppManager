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
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

public final class BackupFlags {
    @IntDef(flag = true, value = {
            BACKUP_NOTHING,
            BACKUP_CUSTOM_USERS,
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
    public static final int BACKUP_CUSTOM_USERS = 1 << 8;
    public static final int BACKUP_MULTIPLE = 1 << 9;
    public static final int BACKUP_EXTRAS = 1 << 10;

    public static final List<Integer> backupFlags = new ArrayList<>();

    private static final LinkedHashMap<Integer, Pair<Integer, Integer>> backupFlagsMap =
            new LinkedHashMap<Integer, Pair<Integer, Integer>>() {
                {
                    backupFlags.add(BACKUP_SOURCE);
                    put(BACKUP_SOURCE, new Pair<>(R.string.source, R.string.backup_source_description));
                    backupFlags.add(BACKUP_SOURCE_APK_ONLY);
                    put(BACKUP_SOURCE_APK_ONLY, new Pair<>(R.string.backup_apk_only, R.string.backup_apk_only_description));
                    backupFlags.add(BACKUP_DATA);
                    put(BACKUP_DATA, new Pair<>(R.string.data, R.string.backup_data_description));
                    backupFlags.add(BACKUP_EXT_DATA);
                    put(BACKUP_EXT_DATA, new Pair<>(R.string.external_data, R.string.backup_external_data_description));
                    backupFlags.add(BACKUP_EXT_OBB_MEDIA);
                    put(BACKUP_EXT_OBB_MEDIA, new Pair<>(R.string.backup_obb_media, R.string.backup_obb_media_description));
                    backupFlags.add(BACKUP_EXCLUDE_CACHE);
                    put(BACKUP_EXCLUDE_CACHE, new Pair<>(R.string.exclude_cache, R.string.backup_exclude_cache_description));
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

    @NonNull
    public static CharSequence[] getFormattedFlagNames(@NonNull Context context) {
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
        return new BackupFlags((Integer) AppPref.get(AppPref.PrefKey.PREF_BACKUP_FLAGS_INT));
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

    public boolean backupExtras() {
        return (flags & BACKUP_EXTRAS) != 0;
    }

    public boolean excludeCache() {
        return (flags & BACKUP_EXCLUDE_CACHE) != 0;
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
}
