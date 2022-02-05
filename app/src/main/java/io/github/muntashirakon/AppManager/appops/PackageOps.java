// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.appops;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class PackageOps implements Parcelable {
    private final String mPackageName;
    private final int mUid;
    private final List<OpEntry> mEntries;

    /* package */ PackageOps(String packageName, int uid, List<OpEntry> entries) {
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