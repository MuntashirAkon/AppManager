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

import java.util.Objects;

import androidx.annotation.NonNull;

public class OpEntry implements Parcelable {
    private final int mOp;
    private final int mMode;
    private final long mTimes;
    private final long mRejectTimes;
    private final long mDuration;
    private final int mProxyUid;
    private final boolean mRunning;
    private final String mProxyPackageName;

    public OpEntry(int op, int mode, long time, long rejectTime, long duration,
                   int proxyUid, String proxyPackage) {
        mOp = op;
        mMode = mode;
        mTimes = time;
        mRejectTimes = rejectTime;
        mDuration = duration;
        mRunning = duration == -1;
        mProxyUid = proxyUid;
        mProxyPackageName = proxyPackage;
    }


    public int getOp() {
        return mOp;
    }

    public int getMode() {
        return mMode;
    }

    public long getTime() {
        return mTimes;
    }

    public long getRejectTime() {
        return mRejectTimes;
    }

    public boolean isRunning() {
        return mDuration == -1;
    }

    public long getDuration() {
        return mDuration;
    }

    public int getProxyUid() {
        return mProxyUid;
    }

    public String getProxyPackageName() {
        return mProxyPackageName;
    }

    @NonNull
    @Override
    public String toString() {
        return "OpEntry{" +
                "mOp=" + mOp +
                ", mMode=" + mMode +
                ", mTime=" + mTimes +
                ", mRejectTime=" + mRejectTimes +
                ", mDuration=" + mDuration +
                ", mProxyUid=" + mProxyUid +
                ", mProxyPackageName='" + mProxyPackageName + '\'' +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(this.mOp);
        dest.writeInt(this.mMode);
        dest.writeLong(this.mTimes);
        dest.writeLong(this.mRejectTimes);
        dest.writeLong(this.mDuration);
        dest.writeByte((byte) (this.mRunning ? 1 : 0));
        dest.writeInt(this.mProxyUid);
        dest.writeString(this.mProxyPackageName);
    }

    protected OpEntry(@NonNull Parcel in) {
        this.mOp = in.readInt();
        this.mMode = in.readInt();
        this.mTimes = in.readLong();
        this.mRejectTimes = in.readLong();
        this.mDuration = in.readLong();
        this.mRunning = in.readByte() != 0;
        this.mProxyUid = in.readInt();
        this.mProxyPackageName = in.readString();
    }

    public static final Creator<OpEntry> CREATOR = new Creator<OpEntry>() {
        @NonNull
        @Override
        public OpEntry createFromParcel(Parcel source) {
            return new OpEntry(source);
        }

        @NonNull
        @Override
        public OpEntry[] newArray(int size) {
            return new OpEntry[size];
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OpEntry)) return false;
        OpEntry opEntry = (OpEntry) o;
        return mOp == opEntry.mOp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mOp);
    }
}