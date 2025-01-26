// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.os.Parcel;

import androidx.annotation.NonNull;

import java.util.Objects;

public class BatchAppOpsOptions implements IBatchOpOptions {
    @NonNull
    private int[] mAppOps;
    private int mMode;

    public BatchAppOpsOptions(@NonNull int[] appOps, int mode) {
        mAppOps = appOps;
        mMode = mode;
    }

    @NonNull
    public int[] getAppOps() {
        return mAppOps;
    }

    public int getMode() {
        return mMode;
    }

    protected BatchAppOpsOptions(@NonNull Parcel in) {
        mAppOps = Objects.requireNonNull(in.createIntArray());
        mMode = in.readInt();
    }

    public static final Creator<BatchAppOpsOptions> CREATOR = new Creator<BatchAppOpsOptions>() {
        @Override
        @NonNull
        public BatchAppOpsOptions createFromParcel(@NonNull Parcel in) {
            return new BatchAppOpsOptions(in);
        }

        @Override
        @NonNull
        public BatchAppOpsOptions[] newArray(int size) {
            return new BatchAppOpsOptions[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeIntArray(mAppOps);
        dest.writeInt(mMode);
    }
}
