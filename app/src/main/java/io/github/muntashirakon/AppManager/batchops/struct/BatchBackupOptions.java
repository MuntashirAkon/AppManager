// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.annotation.UserIdInt;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.struct.BackupOpOptions;
import io.github.muntashirakon.AppManager.backup.struct.DeleteOpOptions;
import io.github.muntashirakon.AppManager.backup.struct.RestoreOpOptions;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class BatchBackupOptions implements IBatchOpOptions {
    public static final String TAG = BatchBackupOptions.class.getSimpleName();

    @BackupFlags.BackupFlag
    private final int mFlags;
    @Nullable
    private final String[] mBackupNames;
    @Nullable
    private final String[] mRelativeDirs;

    public BatchBackupOptions(@BackupFlags.BackupFlag int flags,
                              @Nullable String[] backupNames,
                              @Nullable String[] relativeDirs) {
        mFlags = flags;
        mBackupNames = backupNames;
        mRelativeDirs = relativeDirs;
    }

    public BackupOpOptions getBackupOpOptions(@NonNull String packageName, @UserIdInt int userId) {
        String backupName;
        boolean customBackup = (mFlags & BackupFlags.BACKUP_MULTIPLE) != 0;
        if (mBackupNames != null && mBackupNames.length > 0) {
            backupName = mBackupNames[0];
        } else {
            backupName = customBackup ? DateUtils.formatMediumDateTime(ContextUtils.getContext(), System.currentTimeMillis()) : null;
        }
        return new BackupOpOptions(packageName, userId, mFlags, backupName, !customBackup);
    }

    public RestoreOpOptions getRestoreOpOptions(@NonNull String packageName, @UserIdInt int userId) {
        // For restore operation, backup names (v4) and relative dirs are only set for single
        // package backups. In all other cases, it only uses base backups.
        String relativeDir;
        if (mRelativeDirs != null && mRelativeDirs.length > 0) {
            relativeDir = mRelativeDirs[0];
        } else {
            if (mBackupNames == null || mBackupNames.length == 0) {
                // Base backup
                relativeDir = null;
            } else {
                // Generate relative directories
                Backup backup = BackupUtils.retrieveLatestBackupFromDb(userId, mBackupNames[0], packageName);
                if (backup == null) {
                    throw new IllegalArgumentException("Backup with name " + mBackupNames[0] + " doesn't exist.");
                }
                relativeDir = backup.relativeDir;
            }
        }
        return new RestoreOpOptions(packageName, userId, relativeDir, mFlags);
    }

    public DeleteOpOptions getDeleteOpOptions(@NonNull String packageName, @UserIdInt int userId) {
        // For delete operation, backup names (v4) and relative dirs are only set for single
        // package backups. In all other cases, it only uses base backups.
        String[] relativeDirs;
        if (mRelativeDirs != null) {
            relativeDirs = mRelativeDirs;
        } else {
            if (mBackupNames == null || mBackupNames.length == 0) {
                // Base backup
                relativeDirs = null;
            } else {
                // Generate relative directories
                relativeDirs = new String[mBackupNames.length];
                for (int i = 0; i < relativeDirs.length; ++i) {
                    Backup backup = BackupUtils.retrieveLatestBackupFromDb(userId, mBackupNames[i], packageName);
                    if (backup == null) {
                        throw new IllegalArgumentException("Backup with name " + mBackupNames[i] + " doesn't exist.");
                    }
                    relativeDirs[i] = backup.relativeDir;
                }
            }
        }
        return new DeleteOpOptions(packageName, userId, relativeDirs);
    }

    protected BatchBackupOptions(@NonNull Parcel in) {
        mFlags = in.readInt();
        mBackupNames = in.createStringArray();
        mRelativeDirs = in.createStringArray();
    }

    public static final Creator<BatchBackupOptions> CREATOR = new Creator<BatchBackupOptions>() {
        @Override
        @NonNull
        public BatchBackupOptions createFromParcel(@NonNull Parcel in) {
            return new BatchBackupOptions(in);
        }

        @Override
        @NonNull
        public BatchBackupOptions[] newArray(int size) {
            return new BatchBackupOptions[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mFlags);
        dest.writeStringArray(mBackupNames);
        dest.writeStringArray(mRelativeDirs);
    }

    public BatchBackupOptions(@NonNull JSONObject jsonObject) throws JSONException {
        assert jsonObject.getString("tag").equals(TAG);
        mFlags = jsonObject.getInt("flags");
        mBackupNames = JSONUtils.getArray(String.class, jsonObject.optJSONArray("backup_names"));
        mRelativeDirs = JSONUtils.getArray(String.class, jsonObject.optJSONArray("relative_dirs"));
    }

    public static final JsonDeserializer.Creator<BatchBackupOptions> DESERIALIZER
            = BatchBackupOptions::new;

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("flags", mFlags);
        jsonObject.put("backup_names", JSONUtils.getJSONArray(mBackupNames));
        jsonObject.put("relative_dirs", JSONUtils.getJSONArray(mRelativeDirs));
        return jsonObject;
    }
}
