// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

// Copyright 2017 Zheng Li
public class CallerResult implements Parcelable {
    private byte[] reply;
    private Throwable throwable;
    private Object replyObj;

    public byte[] getReply() {
        return reply;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public Object getReplyObj() {
        if (replyObj == null && reply != null) {
            replyObj = ParcelableUtil.readValue(reply);
        }
        return replyObj;
    }

    public void setReply(byte[] reply) {
        this.reply = reply;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeByteArray(this.reply);
        dest.writeSerializable(this.throwable);
    }

    public CallerResult() {}

    protected CallerResult(@NonNull Parcel in) {
        this.reply = in.createByteArray();
        this.throwable = (Throwable) in.readSerializable();
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
                ", throwable=" + throwable +
                '}';
    }
}
