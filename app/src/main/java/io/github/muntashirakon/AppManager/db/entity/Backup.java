//SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

import java.io.IOException;
import java.util.Objects;

import io.github.muntashirakon.AppManager.backup.BackupFiles;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.io.Path;

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
    public String installer;

    @ColumnInfo(name = "info_hash")
    public String uuid;

    public BackupFlags getFlags() {
        return new BackupFlags(flags);
    }

    public boolean isBaseBackup() {
        return String.valueOf(UserHandleHidden.myUserId()).equals(backupName);
    }

    @NonNull
    public Path getBackupPath() throws IOException {
        String backupUuid = TextUtils.isEmpty(uuid) ? null : uuid;
        return BackupFiles.findBackupDirectory(backupName, packageName, backupUuid);
    }

    public MetadataManager.Metadata getMetadata() throws IOException {
        return MetadataManager.getMetadata(getBackupPath());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Backup)) return false;
        Backup backup = (Backup) o;
        return packageName.equals(backup.packageName) && backupName.equals(backup.backupName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, backupName);
    }

    @NonNull
    public static Backup fromBackupMetadata(@NonNull MetadataManager.Metadata metadata) {
        Backup backup = new Backup();
        backup.packageName = metadata.packageName;
        backup.backupName = metadata.backupName != null ? metadata.backupName : String.valueOf(metadata.userHandle);
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
        backup.userId = metadata.userHandle;
        backup.tarType = metadata.tarType;
        backup.hasKeyStore = metadata.keyStore;
        backup.installer = metadata.installer;
        backup.uuid = "";
        return backup;
    }
}
