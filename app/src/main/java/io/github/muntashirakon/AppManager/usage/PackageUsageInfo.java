// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.utils.DateUtils;

public class PackageUsageInfo implements Parcelable {
    @NonNull
    public String packageName;
    public String appLabel;
    public Long screenTime = 0L;
    public Long lastUsageTime = 0L;
    public Integer timesOpened = 0;
    public AppUsageStatsManager.DataUsage mobileData;
    public AppUsageStatsManager.DataUsage wifiData;
    @Nullable
    public List<Entry> entries;

    public PackageUsageInfo(@NonNull String packageName) {
        this.packageName = packageName;
    }

    protected PackageUsageInfo(@NonNull Parcel in) {
        packageName = Objects.requireNonNull(in.readString());
        appLabel = in.readString();
        screenTime = in.readByte() == 0 ? 0L : in.readLong();
        lastUsageTime = in.readByte() == 0 ? 0L : in.readLong();
        timesOpened = in.readByte() == 0 ? 0 : in.readInt();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(packageName);
        dest.writeString(appLabel);
        if (screenTime == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeLong(screenTime);
        }
        if (lastUsageTime == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeLong(lastUsageTime);
        }
        if (timesOpened == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeInt(timesOpened);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<PackageUsageInfo> CREATOR = new Creator<PackageUsageInfo>() {
        @NonNull
        @Override
        public PackageUsageInfo createFromParcel(Parcel in) {
            return new PackageUsageInfo(in);
        }

        @NonNull
        @Override
        public PackageUsageInfo[] newArray(int size) {
            return new PackageUsageInfo[size];
        }
    };

    public void copyOthers(@NonNull PackageUsageInfo packageUS) {
        screenTime = packageUS.screenTime;
        lastUsageTime = packageUS.lastUsageTime;
        timesOpened = packageUS.timesOpened;
        mobileData = packageUS.mobileData;
        wifiData = packageUS.wifiData;
    }

    @NonNull
    @Override
    public String toString() {
        return "PackageUS{" +
                "packageName='" + packageName + '\'' +
                ", appLabel='" + appLabel + '\'' +
                ", screenTime=" + screenTime +
                ", lastUsageTime=" + lastUsageTime +
                ", timesOpened=" + timesOpened +
                ", txData=" + mobileData +
                ", rxData=" + wifiData +
                ", entries=" + entries +
                '}';
    }

    public static class Entry {
        public final long startTime;
        public final long endTime;

        public Entry(long startTime, long endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public long getDuration() {
            return endTime - startTime;
        }

        @NonNull
        @Override
        public String toString() {
            return "USEntry{" +
                    "startTime=" + DateUtils.formatDateTime(startTime) +
                    ", endTime=" + DateUtils.formatDateTime(endTime) +
                    '}';
        }
    }
}
