// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageItemInfo;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;

import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.shortcut.ShortcutInfo;

@SuppressWarnings("rawtypes")
public class PackageItemShortcutInfo<T extends PackageItemInfo & Parcelable> extends ShortcutInfo {
    private final T mPackageItemInfo;
    private final Class<T> mClazz;
    @UserIdInt
    private final int mUserId;
    private final boolean mLaunchViaAssist;

    public PackageItemShortcutInfo(@NonNull T packageItemInfo, @NonNull Class<T> clazz, @UserIdInt int userId) {
        this(packageItemInfo, clazz, userId, false);
    }

    public PackageItemShortcutInfo(@NonNull T packageItemInfo, @NonNull Class<T> clazz, @UserIdInt int userId, boolean launchViaAssist) {
        mPackageItemInfo = packageItemInfo;
        mClazz = clazz;
        mUserId = userId;
        if (packageItemInfo instanceof ActivityInfo) {
            mLaunchViaAssist = launchViaAssist;
        } else mLaunchViaAssist = false;
    }

    @SuppressWarnings("unchecked")
    public PackageItemShortcutInfo(Parcel in) {
        super(in);
        mClazz = (Class<T>) Objects.requireNonNull(ParcelCompat.readSerializable(in, Class.class.getClassLoader(), Class.class));
        mPackageItemInfo = ParcelCompat.readParcelable(in, mClazz.getClassLoader(), mClazz);
        mUserId = in.readInt();
        mLaunchViaAssist = ParcelCompat.readBoolean(in);
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeSerializable(mClazz);
        dest.writeParcelable(mPackageItemInfo, flags);
        dest.writeInt(mUserId);
        ParcelCompat.writeBoolean(dest, mLaunchViaAssist);
    }

    @Override
    public Intent toShortcutIntent(@NonNull Context context) {
        return requireProxy() ? getProxyIntent(context) : getIntent();
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
    private Intent getIntent() {
        Intent intent = new Intent();
        intent.setClassName(mPackageItemInfo.packageName, mPackageItemInfo.name);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    @NonNull
    private Intent getProxyIntent(@NonNull Context context) {
        return ActivityLauncherShortcutActivity.getShortcutIntent(context, mPackageItemInfo.packageName,
                mPackageItemInfo.name, mUserId, mLaunchViaAssist);
    }

    private boolean requireProxy() {
        return !BuildConfig.APPLICATION_ID.equals(mPackageItemInfo.packageName) && mUserId != UserHandleHidden.myUserId();
    }
}
