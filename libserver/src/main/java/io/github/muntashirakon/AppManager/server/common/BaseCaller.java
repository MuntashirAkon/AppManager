// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

// Copyright 2017 Zheng Li
public class BaseCaller implements Parcelable {

    public static final int TYPE_CLOSE = -10;
    public static final int TYPE_SHELL = 5;

    private final int type;
    private byte[] rawBytes;

    public BaseCaller(@NonNull Caller method) {
        this.type = method.getType();
        this.rawBytes = ParcelableUtil.marshall(method);
    }

    public BaseCaller(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(this.type);
        dest.writeByteArray(this.rawBytes);
    }


    protected BaseCaller(@NonNull Parcel in) {
        this.type = in.readInt();
        this.rawBytes = in.createByteArray();
    }

    public static final Parcelable.Creator<BaseCaller> CREATOR = new Parcelable.Creator<BaseCaller>() {
        @NonNull
        @Override
        public BaseCaller createFromParcel(Parcel source) {
            return new BaseCaller(source);
        }

        @NonNull
        @Override
        public BaseCaller[] newArray(int size) {
            return new BaseCaller[size];
        }
    };
}
