// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.appops;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Objects;

// FIXME: 3/9/21 Merge with OpEntryCompat
public class OpEntry implements Parcelable {
    private final int mOp;
    private final int mMode;
    private final long mTimes;
    private final long mRejectTimes;
    private final long mDuration;
    private final int mProxyUid;
    private final boolean mRunning;
    private final String mProxyPackageName;

    public OpEntry(int op, int mode, long time, long rejectTime, long duration, int proxyUid, String proxyPackage) {
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