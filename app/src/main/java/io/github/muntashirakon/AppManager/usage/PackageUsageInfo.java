// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PackageUsageInfo implements Parcelable {
    @NonNull
    public final String packageName;
    @UserIdInt
    public final int userId;
    @Nullable
    public final ApplicationInfo applicationInfo;
    @NonNull
    public final String appLabel;

    public long screenTime;
    public long lastUsageTime;
    public int timesOpened;
    @Nullable
    public AppUsageStatsManager.DataUsage mobileData;
    @Nullable
    public AppUsageStatsManager.DataUsage wifiData;
    @Nullable
    public List<Entry> entries;

    public PackageUsageInfo(@NonNull Context context, @NonNull String packageName, @UserIdInt int userId,
                            @Nullable ApplicationInfo applicationInfo) {
        this.packageName = packageName;
        this.userId = userId;
        this.applicationInfo = applicationInfo;
        if (applicationInfo != null) {
            appLabel = applicationInfo.loadLabel(context.getPackageManager()).toString();
        } else appLabel = packageName;
    }

    protected PackageUsageInfo(@NonNull Parcel in) {
        packageName = Objects.requireNonNull(in.readString());
        userId = in.readInt();
        applicationInfo = ParcelCompat.readParcelable(in, ApplicationInfo.class.getClassLoader(), ApplicationInfo.class);
        appLabel = in.readString();
        screenTime = in.readLong();
        lastUsageTime = in.readLong();
        timesOpened = in.readInt();
        mobileData = ParcelCompat.readParcelable(in, AppUsageStatsManager.DataUsage.class.getClassLoader(), AppUsageStatsManager.DataUsage.class);
        wifiData = ParcelCompat.readParcelable(in, AppUsageStatsManager.DataUsage.class.getClassLoader(), AppUsageStatsManager.DataUsage.class);
        int size = in.readInt();
        if (size != 0) {
            entries = new ArrayList<>(size);
            ParcelCompat.readList(in, entries, Entry.class.getClassLoader(), Entry.class);
        }
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(packageName);
        dest.writeInt(userId);
        dest.writeParcelable(applicationInfo, flags);
        dest.writeString(appLabel);
        dest.writeLong(screenTime);
        dest.writeLong(lastUsageTime);
        dest.writeInt(timesOpened);
        dest.writeParcelable(mobileData, flags);
        dest.writeParcelable(wifiData, flags);
        dest.writeInt(entries == null ? 0 : entries.size());
        if (entries != null) {
            dest.writeList(entries);
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

    public static class Entry implements Parcelable {
        public final long startTime;
        public final long endTime;

        public Entry(long startTime, long endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        protected Entry(Parcel in) {
            this.startTime = in.readLong();
            this.endTime = in.readLong();
        }

        public static final Creator<Entry> CREATOR = new Creator<Entry>() {
            @NonNull
            @Override
            public Entry createFromParcel(Parcel in) {
                return new Entry(in);
            }

            @NonNull
            @Override
            public Entry[] newArray(int size) {
                return new Entry[size];
            }
        };

        public long getDuration() {
            return endTime - startTime;
        }

        @NonNull
        @Override
        public String toString() {
            return "USEntry{" +
                    "startTime=" + startTime +
                    ", endTime=" + endTime +
                    '}';
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(startTime);
            dest.writeLong(endTime);
        }
    }
}
