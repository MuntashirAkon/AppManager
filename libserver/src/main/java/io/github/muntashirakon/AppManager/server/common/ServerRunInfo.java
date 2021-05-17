// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcel;

import androidx.annotation.NonNull;

// Copyright 2017 Zheng Li
public class ServerRunInfo implements android.os.Parcelable {
    public String protocolVersion = DataTransmission.PROTOCOL_VERSION;

    public String startArgs;
    public long startTime;
    public long startRealTime;
    public long rxBytes;  // Received
    public long txBytes;  // Sent
    public long successCount;
    public long errorCount;


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(this.protocolVersion);
        dest.writeString(this.startArgs);
        dest.writeLong(this.startTime);
        dest.writeLong(this.startRealTime);
        dest.writeLong(this.rxBytes);
        dest.writeLong(this.txBytes);
        dest.writeLong(this.successCount);
        dest.writeLong(this.errorCount);
    }

    public ServerRunInfo() {
    }

    protected ServerRunInfo(@NonNull Parcel in) {
        this.protocolVersion = in.readString();
        this.startArgs = in.readString();
        this.startTime = in.readLong();
        this.startRealTime = in.readLong();
        this.rxBytes = in.readLong();
        this.txBytes = in.readLong();
        this.successCount = in.readLong();
        this.errorCount = in.readLong();
    }

    public static final Creator<ServerRunInfo> CREATOR = new Creator<ServerRunInfo>() {
        @NonNull
        @Override
        public ServerRunInfo createFromParcel(Parcel source) {
            return new ServerRunInfo(source);
        }

        @NonNull
        @Override
        public ServerRunInfo[] newArray(int size) {
            return new ServerRunInfo[size];
        }
    };

    @NonNull
    @Override
    public String toString() {
        return "ServerRunInfo{" +
                "protocolVersion='" + protocolVersion + '\'' +
                ", startArgs='" + startArgs + '\'' +
                ", startTime=" + startTime +
                ", startRealTime=" + startRealTime +
                ", rxBytes=" + rxBytes +
                ", txBytes=" + txBytes +
                ", successCount=" + successCount +
                ", errorCount=" + errorCount +
                '}';
    }
}
