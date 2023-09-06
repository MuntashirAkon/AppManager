// SPDX-License-Identifier: GPL-3.0-or-later

package aosp.android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Transfer a large list of Parcelable objects across an IPC.  Splits into
 * multiple transactions if needed.
 *
 * @see BaseParceledListSlice
 */
// Copyright 2011 The Android Open Source Project
@SuppressWarnings("rawtypes")
public class ParceledListSlice<T extends Parcelable> extends BaseParceledListSlice<T> {
    public ParceledListSlice(List<T> list) {
        super(list);
    }

    private ParceledListSlice(Parcel in) {
        super(in);
    }

    public static <T extends Parcelable> ParceledListSlice<T> emptyList() {
        return new ParceledListSlice<>(Collections.emptyList());
    }

    @Override
    public int describeContents() {
        int contents = 0;
        final List<T> list = getList();
        for (T t : list) {
            contents |= t.describeContents();
        }
        return contents;
    }

    @Override
    protected void writeElement(T parcelable, Parcel dest, int callFlags) {
        parcelable.writeToParcel(dest, callFlags);
    }

    @Override
    protected void writeParcelableCreator(T parcelable, Parcel dest) {
        ParcelUtils.writeParcelableCreator(parcelable, dest);
    }

    @Override
    protected Parcelable.Creator<?> readParcelableCreator(Parcel from, @Nullable ClassLoader loader) {
        return ParcelUtils.readParcelableCreator(from, loader);
    }

    public static final Parcelable.Creator<ParceledListSlice> CREATOR = new Parcelable.Creator<ParceledListSlice>() {
        @Override
        public ParceledListSlice createFromParcel(Parcel in) {
            return new ParceledListSlice(in);
        }

        @Override
        public ParceledListSlice[] newArray(int size) {
            return new ParceledListSlice[size];
        }
    };
}