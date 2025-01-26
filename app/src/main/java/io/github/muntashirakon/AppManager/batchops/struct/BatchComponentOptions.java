// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.os.Parcel;

import androidx.annotation.NonNull;

import java.util.Objects;

public class BatchComponentOptions implements IBatchOpOptions {
    private String[] mSignatures;

    public BatchComponentOptions(@NonNull String[] signatures) {
        mSignatures = signatures;
    }

    @NonNull
    public String[] getSignatures() {
        return mSignatures;
    }

    protected BatchComponentOptions(@NonNull Parcel in) {
        mSignatures = Objects.requireNonNull(in.createStringArray());
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeStringArray(mSignatures);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BatchComponentOptions> CREATOR = new Creator<BatchComponentOptions>() {
        @Override
        @NonNull
        public BatchComponentOptions createFromParcel(@NonNull Parcel in) {
            return new BatchComponentOptions(in);
        }

        @Override
        @NonNull
        public BatchComponentOptions[] newArray(int size) {
            return new BatchComponentOptions[size];
        }
    };
}
