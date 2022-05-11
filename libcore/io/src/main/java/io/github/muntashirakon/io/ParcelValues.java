// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.io;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

// Copyright 2022 John "topjohnwu" Wu
class ParcelValues extends ArrayList<Object> implements Parcelable {

    private static final ClassLoader cl = ParcelValues.class.getClassLoader();

    static final Creator<ParcelValues> CREATOR = new Creator<ParcelValues>() {
        @Override
        public ParcelValues createFromParcel(Parcel in) {
            return new ParcelValues(in);
        }

        @Override
        public ParcelValues[] newArray(int size) {
            return new ParcelValues[size];
        }
    };

    ParcelValues() {}

    private ParcelValues(Parcel in) {
        int size = in.readInt();
        ensureCapacity(size);
        for (int i = 0; i < size; ++i) {
            add(in.readValue(cl));
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getTyped(int index) {
        return (T) get(index);
    }

    @Override
    public int describeContents() {
        return CONTENTS_FILE_DESCRIPTOR;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(size());
        for (Object o : this) {
            dest.writeValue(o);
        }
    }
}