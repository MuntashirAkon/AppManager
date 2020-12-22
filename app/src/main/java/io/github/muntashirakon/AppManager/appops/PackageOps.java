/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.appops;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

public class PackageOps implements Parcelable {
    private final String mPackageName;
    private final int mUid;
    private final List<OpEntry> mEntries;
    private SparseArray<OpEntry> mSparseEntries = null;

    public PackageOps(String packageName, int uid, List<OpEntry> entries) {
        mPackageName = packageName;
        mUid = uid;
        mEntries = entries;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public int getUid() {
        return mUid;
    }

    public List<OpEntry> getOps() {
        return mEntries;
    }

    public boolean hasOp(int op) {
        if (mSparseEntries == null) {
            mSparseEntries = new SparseArray<>();
            if (mEntries != null) {
                for (OpEntry entry : mEntries) {
                    mSparseEntries.put(entry.getOp(), entry);
                }
            }
        }
        return mSparseEntries.indexOfKey(op) >= 0;
    }

    @NonNull
    @Override
    public String toString() {
        return "PackageOps{" +
                "mPackageName='" + mPackageName + '\'' +
                ", mUid=" + mUid +
                ", mEntries=" + mEntries +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(this.mPackageName);
        dest.writeInt(this.mUid);
        dest.writeList(this.mEntries);
    }

    protected PackageOps(@NonNull Parcel in) {
        this.mPackageName = in.readString();
        this.mUid = in.readInt();
        this.mEntries = new ArrayList<>();
        in.readList(this.mEntries, OpEntry.class.getClassLoader());
    }

    public static final Parcelable.Creator<PackageOps> CREATOR = new Parcelable.Creator<PackageOps>() {
        @NonNull
        @Override
        public PackageOps createFromParcel(Parcel source) {
            return new PackageOps(source);
        }

        @NonNull
        @Override
        public PackageOps[] newArray(int size) {
            return new PackageOps[size];
        }
    };
}