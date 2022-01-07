// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import android.content.pm.PackageInfo;
import android.os.Parcel;

import androidx.annotation.NonNull;

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
        packageInfo = in.readParcelable(PackageInfo.class.getClassLoader());
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppProcessItem)) {
            if (o instanceof ProcessItem) {
                return super.equals(o);
            }
            return false;
        }
        if (!super.equals(o)) return false;
        AppProcessItem that = (AppProcessItem) o;
        return packageInfo.equals(that.packageInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), packageInfo);
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(packageInfo, flags);
    }
}
