// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

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