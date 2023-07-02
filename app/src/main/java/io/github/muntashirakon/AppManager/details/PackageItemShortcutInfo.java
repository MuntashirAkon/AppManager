// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageItemInfo;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.shortcut.ShortcutInfo;

@SuppressWarnings("rawtypes")
public class PackageItemShortcutInfo<T extends PackageItemInfo & Parcelable> extends ShortcutInfo {
    private final T mPackageItemInfo;
    private final Class<T> mClazz;

    public PackageItemShortcutInfo(@NonNull T packageItemInfo, @NonNull Class<T> clazz) {
        mPackageItemInfo = packageItemInfo;
        mClazz = clazz;
    }

    @SuppressWarnings("unchecked")
    public PackageItemShortcutInfo(Parcel in) {
        super(in);
        mClazz = (Class<T>) Objects.requireNonNull(ParcelCompat.readSerializable(in, Class.class.getClassLoader(), Class.class));
        mPackageItemInfo = ParcelCompat.readParcelable(in, mClazz.getClassLoader(), mClazz);
    }

    @Override
    public void writeToParcel(@NonNull @NotNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeSerializable(mClazz);
        dest.writeParcelable(mPackageItemInfo, flags);
    }

    @Override
    public Intent toShortcutIntent(@NonNull @NotNull Context context) {
        return requireProxy(mPackageItemInfo) ? getProxyIntent(context, mPackageItemInfo) : getIntent(mPackageItemInfo);
    }

    public static final Creator<PackageItemShortcutInfo> CREATOR = new Creator<PackageItemShortcutInfo>() {
        @Override
        public PackageItemShortcutInfo createFromParcel(Parcel source) {
            return new PackageItemShortcutInfo(source);
        }

        @Override
        public PackageItemShortcutInfo[] newArray(int size) {
            return new PackageItemShortcutInfo[size];
        }
    };


    @NonNull
    private static Intent getIntent(@NonNull PackageItemInfo itemInfo) {
        Intent intent = new Intent();
        intent.setClassName(itemInfo.packageName, itemInfo.name);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    @NonNull
    private static Intent getProxyIntent(@NonNull Context context, @NonNull PackageItemInfo itemInfo) {
        Intent intent = new Intent();
        intent.setClass(context, ActivityLauncherShortcutActivity.class);
        intent.putExtra(ActivityLauncherShortcutActivity.EXTRA_PKG, itemInfo.packageName);
        intent.putExtra(ActivityLauncherShortcutActivity.EXTRA_CLS, itemInfo.name);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    private static boolean requireProxy(@NonNull PackageItemInfo itemInfo) {
        return !BuildConfig.APPLICATION_ID.equals(itemInfo.packageName);
    }
}
