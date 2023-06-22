// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

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
    private static final int BACKUP_SOURCE = 1 << 0;
    public static final int BACKUP_INT_DATA = 1 << 1;
    public static final int BACKUP_EXT_DATA = 1 << 2;
    @Deprecated
    private static final int BACKUP_EXCLUDE_CACHE = 1 << 3;
    public static final int BACKUP_RULES = 1 << 4;
    public static final int BACKUP_NO_SIGNATURE_CHECK = 1 << 5;
    public static final int BACKUP_APK_FILES = 1 << 6;
    public static final int BACKUP_EXT_OBB_MEDIA = 1 << 7;
    public static final int BACKUP_CUSTOM_USERS = 1 << 8;
    public static final int BACKUP_MULTIPLE = 1 << 9;
    public static final int BACKUP_EXTRAS = 1 << 10;
    public static final int BACKUP_CACHE = 1 << 11;

    private static final LinkedHashMap<Integer, Pair<Integer, Integer>> sBackupFlagsMap = new LinkedHashMap<Integer, Pair<Integer, Integer>>() {{
        put(BACKUP_APK_FILES, new Pair<>(R.string.backup_apk_files, R.string.backup_apk_files_description));
        put(BACKUP_INT_DATA, new Pair<>(R.string.internal_data, R.string.backup_internal_data_description));
        put(BACKUP_EXT_DATA, new Pair<>(R.string.external_data, R.string.backup_external_data_description));
        put(BACKUP_EXT_OBB_MEDIA, new Pair<>(R.string.backup_obb_media, R.string.backup_obb_media_description));
        put(BACKUP_CACHE, new Pair<>(R.string.cache, R.string.backup_cache_description));
        put(BACKUP_EXTRAS, new Pair<>(R.string.backup_extras, R.string.backup_extras_description));
        put(BACKUP_RULES, new Pair<>(R.string.rules, R.string.backup_rules_description));
        put(BACKUP_MULTIPLE, new Pair<>(R.string.backup_multiple, R.string.backup_multiple_description));
        put(BACKUP_CUSTOM_USERS, new Pair<>(R.string.backup_custom_users, R.string.backup_custom_users_description));
        put(BACKUP_NO_SIGNATURE_CHECK, new Pair<>(R.string.skip_signature_checks, R.string.backup_skip_signature_checks_description));
    }};

    @BackupFlag
    public static int getSupportedBackupFlags() {
        List<Integer> backupFlags = getSupportedBackupFlagsAsArray();
        int flags = 0;
        for (int flag : backupFlags) {
            flags |= flag;
        }
        return flags;
    }

    public static List<Integer> getSupportedBackupFlagsAsArray() {
        List<Integer> backupFlags = new ArrayList<>();
        backupFlags.add(BACKUP_APK_FILES);
        if (SelfPermissions.canWriteToDataData()) {
            backupFlags.add(BACKUP_INT_DATA);
        }
        backupFlags.add(BACKUP_EXT_DATA);
        backupFlags.add(BACKUP_EXT_OBB_MEDIA);
        backupFlags.add(BACKUP_CACHE);
        backupFlags.add(BACKUP_EXTRAS);
        backupFlags.add(BACKUP_RULES);
        backupFlags.add(BACKUP_MULTIPLE);
        if (Users.getUsersIds().length > 1) {
            // Display custom users only if multiple users present
            backupFlags.add(BACKUP_CUSTOM_USERS);
        }
        backupFlags.add(BACKUP_NO_SIGNATURE_CHECK);
        return backupFlags;
    }

    @NonNull
    public static List<Integer> getBackupFlagsAsArray(@BackupFlag int flags) {
        flags = migrate(flags);
        List<Integer> backupFlags = new ArrayList<>();
        if ((flags & BACKUP_APK_FILES) != 0) {
            backupFlags.add(BACKUP_APK_FILES);
        }
        if ((flags & BACKUP_INT_DATA) != 0) {
            backupFlags.add(BACKUP_INT_DATA);
        }
        if ((flags & BACKUP_EXT_DATA) != 0) {
            backupFlags.add(BACKUP_EXT_DATA);
        }
        if ((flags & BACKUP_EXT_OBB_MEDIA) != 0) {
            backupFlags.add(BACKUP_EXT_OBB_MEDIA);
        }
        if ((flags & BACKUP_CACHE) != 0) {
            backupFlags.add(BACKUP_CACHE);
        }
        if ((flags & BACKUP_EXTRAS) != 0) {
            backupFlags.add(BACKUP_EXTRAS);
        }
        if ((flags & BACKUP_RULES) != 0) {
            backupFlags.add(BACKUP_RULES);
        }
        if ((flags & BACKUP_MULTIPLE) != 0) {
            backupFlags.add(BACKUP_MULTIPLE);
        }
        if ((flags & BACKUP_CUSTOM_USERS) != 0) {
            backupFlags.add(BACKUP_CUSTOM_USERS);
        }
        if ((flags & BACKUP_NO_SIGNATURE_CHECK) != 0) {
            backupFlags.add(BACKUP_NO_SIGNATURE_CHECK);
        }
        return backupFlags;
    }

    @NonNull
    public static CharSequence[] getFormattedFlagNames(@NonNull Context context, List<Integer> backupFlags) {
        // Reset backup flags
        CharSequence[] flagNames = new CharSequence[backupFlags.size()];
        for (int i = 0; i < flagNames.length; ++i) {
            Pair<Integer, Integer> flagNamePair = Objects.requireNonNull(sBackupFlagsMap.get(backupFlags.get(i)));
            flagNames[i] = new SpannableStringBuilder()
                    .append(context.getText(flagNamePair.first))
                    .append("\n")
                    .append(getSmallerText(context.getText(flagNamePair.second)));
        }
        return flagNames;
    }

    @BackupFlag
    private int mFlags;

    @NonNull
    public static BackupFlags fromPref() {
        return new BackupFlags(getSanitizedFlags(Prefs.BackupRestore.getBackupFlags()));
    }

    public BackupFlags(@BackupFlag int flags) {
        mFlags = flags;
    }

    @BackupFlag
    public int getFlags() {
        return mFlags;
    }

    public void addFlag(@BackupFlag int flag) {
        mFlags |= flag;
    }

    public void removeFlag(@BackupFlag int flag) {
        mFlags &= ~flag;
    }

    public void setFlags(int flags) {
        mFlags = flags;
    }

    @NonNull
    public int[] flagsToCheckedIndexes(@NonNull List<Integer> enabledFlags) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < enabledFlags.size(); ++i) {
            int flag = enabledFlags.get(i);
            if ((mFlags & flag) != 0) {
                indexes.add(i);
            }
        }
        return ArrayUtils.convertToIntArray(indexes);
    }

    public boolean isEmpty() {
        return mFlags == 0;
    }

    public boolean backupApkFiles() {
        return (mFlags & BACKUP_APK_FILES) != 0;
    }

    public boolean backupInternalData() {
        return (mFlags & BACKUP_INT_DATA) != 0;
    }

    public boolean backupExternalData() {
        return (mFlags & BACKUP_EXT_DATA) != 0;
    }

    public boolean backupMediaObb() {
        return (mFlags & BACKUP_EXT_OBB_MEDIA) != 0;
    }

    public boolean backupData() {
        return backupInternalData() || backupExternalData() || backupMediaObb();
    }

    public boolean backupRules() {
        return (mFlags & BACKUP_RULES) != 0;
    }

    public boolean backupExtras() {
        return (mFlags & BACKUP_EXTRAS) != 0;
    }

    public boolean backupCache() {
        return (mFlags & BACKUP_CACHE) != 0;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean skipSignatureCheck() {
        return (mFlags & BACKUP_NO_SIGNATURE_CHECK) != 0;
    }

    public boolean backupMultiple() {
        return (mFlags & BACKUP_MULTIPLE) != 0;
    }

    public boolean backupCustomUsers() {
        return (mFlags & BACKUP_CUSTOM_USERS) != 0;
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

    /**
     * Remove unsupported flags from the given list of flags
     */
    private static int getSanitizedFlags(int flags) {
        if (!SelfPermissions.canWriteToDataData()) {
            flags &= ~BACKUP_INT_DATA;
        }
        if (Users.getUsersIds().length == 1) {
            flags &= ~BACKUP_CUSTOM_USERS;
        }
        return migrate(flags);
    }

    private static int migrate(int flags) {
        if ((flags & BACKUP_SOURCE) != 0) {
            // BACKUP_SOURCE is replaced with BACKUP_APK_FILES
            flags &= ~BACKUP_SOURCE;
            flags |= BACKUP_APK_FILES;
        }
        if ((flags & BACKUP_EXCLUDE_CACHE) != 0) {
            // BACKUP_EXCLUDE_CACHE is inversely replaced with BACKUP_CACHE
            flags &= ~BACKUP_EXCLUDE_CACHE;
            flags &= ~BACKUP_CACHE;
        }
        return flags;
    }
}
