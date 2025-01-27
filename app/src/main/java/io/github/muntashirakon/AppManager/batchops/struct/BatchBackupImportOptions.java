// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.net.Uri;
import android.os.Parcel;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import io.github.muntashirakon.AppManager.backup.convert.ImportType;

public class BatchBackupImportOptions implements IBatchOpOptions {
    public static final String TAG = BatchBackupImportOptions.class.getSimpleName();

    @ImportType
    private int mImportType;
    @NonNull
    private Uri mDirectory;
    private boolean mRemoveImportedDirectory;

    public BatchBackupImportOptions(@ImportType int importType, @NonNull Uri directory,
                                    boolean removeImportedDirectory) {
        mImportType = importType;
        mDirectory = directory;
        mRemoveImportedDirectory = removeImportedDirectory;
    }

    @ImportType
    public int getImportType() {
        return mImportType;
    }

    @NonNull
    public Uri getDirectory() {
        return mDirectory;
    }

    public boolean isRemoveImportedDirectory() {
        return mRemoveImportedDirectory;
    }

    protected BatchBackupImportOptions(@NonNull Parcel in) {
        mImportType = in.readInt();
        mDirectory = Objects.requireNonNull(in.readParcelable(Uri.class.getClassLoader()));
        mRemoveImportedDirectory = in.readByte() != 0;
    }

    public static final Creator<BatchBackupImportOptions> CREATOR = new Creator<BatchBackupImportOptions>() {
        @Override
        @NonNull
        public BatchBackupImportOptions createFromParcel(@NonNull Parcel in) {
            return new BatchBackupImportOptions(in);
        }

        @Override
        @NonNull
        public BatchBackupImportOptions[] newArray(int size) {
            return new BatchBackupImportOptions[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mImportType);
        dest.writeParcelable(mDirectory, flags);
        dest.writeByte((byte) (mRemoveImportedDirectory ? 1 : 0));
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("import_type", mImportType);
        jsonObject.put("directory", mDirectory.toString());
        jsonObject.put("remove_imported_directory", mRemoveImportedDirectory);
        return jsonObject;
    }
}
