// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

// Copyright 2017 Zheng Li
public class ParcelableUtil {
    @NonNull
    public static byte[] marshall(@NonNull Parcelable parcelable) {
        Parcel parcel = Parcel.obtain();
        try {
            parcelable.writeToParcel(parcel, 0);
            return parcel.marshall();
        } finally {
            parcel.recycle();
        }
    }

    @Contract("!null,_ -> !null")
    @Nullable
    public static <T extends Parcelable> T unmarshall(@Nullable byte[] bytes, @NonNull Parcelable.Creator<T> creator) {
        if (bytes == null) {
            return null;
        }
        Parcel parcel = unmarshall(bytes);
        return creator.createFromParcel(parcel);
    }

    @Contract("!null -> !null")
    @Nullable
    public static Parcel unmarshall(@Nullable byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);
        return parcel;
    }

    @Nullable
    public static Object readValue(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        Parcel unmarshall = unmarshall(bytes);
        try {
            return unmarshall.readValue(ParcelableUtil.class.getClassLoader());
        } finally {
            unmarshall.recycle();
        }
    }
}