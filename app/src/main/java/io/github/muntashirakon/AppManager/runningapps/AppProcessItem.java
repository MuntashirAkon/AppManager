// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import android.content.pm.PackageInfo;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;

import java.util.Objects;

import io.github.muntashirakon.AppManager.ipc.ps.ProcessEntry;

public class AppProcessItem extends ProcessItem {
    @NonNull
    public final PackageInfo packageInfo;

    public AppProcessItem(@NonNull ProcessEntry processEntry, @NonNull PackageInfo packageInfo) {
        super(processEntry);
        this.packageInfo = packageInfo;
    }

    protected AppProcessItem(@NonNull Parcel in) {
        super(in);
        packageInfo = Objects.requireNonNull(ParcelCompat.readParcelable(in, PackageInfo.class.getClassLoader(), PackageInfo.class));
    }

    public static final Creator<AppProcessItem> CREATOR = new Creator<AppProcessItem>() {
        @NonNull
        @Override
        public AppProcessItem createFromParcel(Parcel in) {
            return new AppProcessItem(in);
        }

        @NonNull
        @Override
        public AppProcessItem[] newArray(int size) {
            return new AppProcessItem[size];
        }
    };

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(packageInfo, flags);
    }
}
