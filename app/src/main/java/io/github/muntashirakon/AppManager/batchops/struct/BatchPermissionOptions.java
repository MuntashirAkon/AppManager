// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.os.Parcel;

import androidx.annotation.NonNull;

import java.util.Objects;

public class BatchPermissionOptions implements IBatchOpOptions {
    private String[] mPermissions;

    public BatchPermissionOptions(@NonNull String[] permissions) {
        mPermissions = permissions;
    }

    @NonNull
    public String[] getPermissions() {
        return mPermissions;
    }

    protected BatchPermissionOptions(@NonNull Parcel in) {
        mPermissions = Objects.requireNonNull(in.createStringArray());
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeStringArray(mPermissions);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BatchPermissionOptions> CREATOR = new Creator<BatchPermissionOptions>() {
        @Override
        @NonNull
        public BatchPermissionOptions createFromParcel(@NonNull Parcel in) {
            return new BatchPermissionOptions(in);
        }

        @Override
        @NonNull
        public BatchPermissionOptions[] newArray(int size) {
            return new BatchPermissionOptions[size];
        }
    };
}
