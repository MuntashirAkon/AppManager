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

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

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
