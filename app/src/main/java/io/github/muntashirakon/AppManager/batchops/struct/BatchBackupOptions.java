// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class BatchBackupOptions implements IBatchOpOptions {
    public static final String TAG = BatchBackupOptions.class.getSimpleName();

    @BackupFlags.BackupFlag
    private int mFlags;
    @Nullable
    private String[] mBackupNames;

    public BatchBackupOptions(@BackupFlags.BackupFlag int flags, @Nullable String[] backupNames) {
        mFlags = flags;
        mBackupNames = backupNames;
    }

    public int getFlags() {
        return mFlags;
    }

    @Nullable
    public String[] getBackupNames() {
        return mBackupNames;
    }

    protected BatchBackupOptions(@NonNull Parcel in) {
        mFlags = in.readInt();
        mBackupNames = in.createStringArray();
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
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("flags", mFlags);
        jsonObject.put("backup_names", JSONUtils.getJSONArray(mBackupNames));
        return jsonObject;
    }
}
