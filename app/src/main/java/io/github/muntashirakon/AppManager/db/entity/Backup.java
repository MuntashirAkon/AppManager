//SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

import java.io.IOException;
import java.util.Objects;

import io.github.muntashirakon.AppManager.backup.BackupItems;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.utils.TarUtils;

@SuppressWarnings("NotNullFieldNotInitialized")
@Entity(tableName = "backup", primaryKeys = {"backup_name", "package_name"})
public class Backup {
    @ColumnInfo(name = "package_name")
    @NonNull
    public String packageName;

    @ColumnInfo(name = "backup_name")
    @NonNull
    public String backupName;

    @ColumnInfo(name = "label")
    public String label;

    @ColumnInfo(name = "version_name")
    public String versionName;

    @ColumnInfo(name = "version_code")
    public long versionCode;

    @ColumnInfo(name = "is_system")
    public boolean isSystem;

    @ColumnInfo(name = "has_splits")
    public boolean hasSplits;

    @ColumnInfo(name = "has_rules")
    public boolean hasRules;

    @ColumnInfo(name = "backup_time")
    public long backupTime;

    @ColumnInfo(name = "crypto")
    @CryptoUtils.Mode
    public String crypto;

    @ColumnInfo(name = "meta_version")
    public int version;

    @ColumnInfo(name = "flags")
    public int flags;

    @ColumnInfo(name = "user_id")
    public int userId;

    @ColumnInfo(name = "tar_type")
    @TarUtils.TarType
    public String tarType;

    @ColumnInfo(name = "has_key_store")
    public boolean hasKeyStore;

    @ColumnInfo(name = "installer_app")
    @Nullable
    public String installer;

    @ColumnInfo(name = "info_hash")
    public String relativeDir;

    public BackupFlags getFlags() {
        return new BackupFlags(flags);
    }

    @NonNull
    public BackupItems.BackupItem getItem() throws IOException {
        String relativeDir;
        if (TextUtils.isEmpty(this.relativeDir)) {
            if (version >= 5) {
                // In backup v5 onwards, relativeDir must be set
                throw new IOException("relativeDir not set.");
            }
            // Relative directory needs to be inferred.
            relativeDir = BackupUtils.getV4RelativeDir(userId, backupName, packageName);
        } else relativeDir = this.relativeDir;
        return BackupItems.findBackupItem(relativeDir);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Backup)) return false;
        Backup backup = (Backup) o;
        return packageName.equals(backup.packageName)
                && userId == backup.userId
                && backupName.equals(backup.backupName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, userId, backupName);
    }

    @NonNull
    public static Backup fromBackupMetadata(@NonNull BackupMetadataV2 metadata) {
        Backup backup = new Backup();
        backup.packageName = metadata.packageName;
        backup.backupName = metadata.backupName != null ? metadata.backupName : "";
        backup.label = metadata.label;
        backup.versionName = metadata.versionName;
        backup.versionCode = metadata.versionCode;
        backup.isSystem = metadata.isSystem;
        backup.hasSplits = metadata.isSplitApk;
        backup.hasRules = metadata.hasRules;
        backup.backupTime = metadata.backupTime;
        backup.crypto = metadata.crypto;
        backup.version = metadata.version;
        backup.flags = metadata.flags.getFlags();
        backup.userId = metadata.userId;
        backup.tarType = metadata.tarType;
        backup.hasKeyStore = metadata.keyStore;
        backup.installer = metadata.installer;
        backup.relativeDir = metadata.backupItem.getRelativeDir();
        return backup;
    }

    @NonNull
    public static Backup fromBackupMetadataV5(@NonNull BackupMetadataV5 metadata) {
        return fromBackupInfoAndMeta(metadata.info, metadata.metadata);
    }

    @NonNull
    public static Backup fromBackupInfoAndMeta(@NonNull BackupMetadataV5.Info info, @NonNull BackupMetadataV5.Metadata metadata) {
        Backup backup = new Backup();
        backup.packageName = metadata.packageName;
        backup.backupName = metadata.backupName != null ? metadata.backupName : "";
        backup.label = metadata.label;
        backup.versionName = metadata.versionName;
        backup.versionCode = metadata.versionCode;
        backup.isSystem = metadata.isSystem;
        backup.hasSplits = metadata.isSplitApk;
        backup.hasRules = metadata.hasRules;
        backup.backupTime = info.backupTime;
        backup.crypto = info.crypto;
        backup.version = metadata.version;
        backup.flags = info.flags.getFlags();
        backup.userId = info.userId;
        backup.tarType = info.tarType;
        backup.hasKeyStore = metadata.keyStore;
        backup.installer = metadata.installer;
        backup.relativeDir = info.getRelativeDir();
        return backup;
    }
}
