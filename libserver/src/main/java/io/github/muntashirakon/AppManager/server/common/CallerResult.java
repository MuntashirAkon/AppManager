// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// Copyright 2017 Zheng Li
public class CallerResult implements Parcelable {
    @Nullable
    private byte[] mReply;
    @Nullable
    private Throwable mThrowable;
    @Nullable
    private Object mReplyObj;

    @Nullable
    public byte[] getReply() {
        return mReply;
    }

    @Nullable
    public Throwable getThrowable() {
        return mThrowable;
    }

    @Nullable
    public Object getReplyObj() {
        if (mReplyObj == null && mReply != null) {
            mReplyObj = ParcelableUtil.readValue(mReply);
        }
        return mReplyObj;
    }

    public void setReply(byte[] reply) {
        this.mReply = reply;
    }

    public void setThrowable(Throwable throwable) {
        this.mThrowable = throwable;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeByteArray(this.mReply);
        dest.writeSerializable(this.mThrowable);
    }

    public CallerResult() {}

    protected CallerResult(@NonNull Parcel in) {
        this.mReply = in.createByteArray();
        this.mThrowable = (Throwable) in.readSerializable();
    }

    public static final Creator<CallerResult> CREATOR = new Creator<CallerResult>() {
        @NonNull
        @Override
        public CallerResult createFromParcel(Parcel source) {
            return new CallerResult(source);
        }

        @NonNull
        @Override
        public CallerResult[] newArray(int size) {
            return new CallerResult[size];
        }
    };

    @NonNull
    @Override
    public String toString() {
        return "CallerResult{" +
                "reply=" + getReplyObj() +
                ", throwable=" + mThrowable +
                '}';
    }
}
