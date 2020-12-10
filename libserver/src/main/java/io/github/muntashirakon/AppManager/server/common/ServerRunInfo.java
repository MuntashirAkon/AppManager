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

import androidx.annotation.NonNull;

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
