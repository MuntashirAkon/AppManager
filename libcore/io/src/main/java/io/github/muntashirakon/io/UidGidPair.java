// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Objects;

public class UidGidPair implements Parcelable {
    public final int uid;
    public final int gid;

    public UidGidPair(int uid, int gid) {
        this.uid = uid;
        this.gid = gid;
    }

    protected UidGidPair(Parcel in) {
        uid = in.readInt();
        gid = in.readInt();
    }

    public static final Creator<UidGidPair> CREATOR = new Creator<UidGidPair>() {
        @Override
        public UidGidPair createFromParcel(Parcel in) {
            return new UidGidPair(in);
        }

        @Override
        public UidGidPair[] newArray(int size) {
            return new UidGidPair[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(uid);
        dest.writeInt(gid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UidGidPair)) return false;
        UidGidPair that = (UidGidPair) o;
        return uid == that.uid && gid == that.gid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid, gid);
    }
}
