// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;

import java.io.File;
import java.util.Objects;

public class ApplicationInfoApkSource extends ApkSource {
    @NonNull
    private final ApplicationInfo mApplicationInfo;

    private int mApkFileKey;

    ApplicationInfoApkSource(@NonNull ApplicationInfo applicationInfo) {
        mApplicationInfo = Objects.requireNonNull(applicationInfo);
    }

    @NonNull
    @Override
    public ApkFile resolve() throws ApkFile.ApkFileException {
        ApkFile apkFile = ApkFile.getInstance(mApkFileKey);
        if (apkFile != null && !apkFile.isClosed()) {
            // Usable past instance
            return apkFile;
        }
        mApkFileKey = ApkFile.createInstance(mApplicationInfo);
        return Objects.requireNonNull(ApkFile.getInstance(mApkFileKey));
    }

    @NonNull
    @Override
    public ApkSource toCachedSource() {
        return new CachedApkSource(Uri.fromFile(new File(mApplicationInfo.publicSourceDir)),
                "application/vnd.android.package-archive");
    }

    protected ApplicationInfoApkSource(@NonNull Parcel in) {
        mApplicationInfo = Objects.requireNonNull(ParcelCompat.readParcelable(in,
                ApplicationInfo.class.getClassLoader(), ApplicationInfo.class));
        mApkFileKey = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mApplicationInfo, flags);
        dest.writeInt(mApkFileKey);
    }

    public static final Creator<ApplicationInfoApkSource> CREATOR = new Creator<ApplicationInfoApkSource>() {
        @Override
        public ApplicationInfoApkSource createFromParcel(Parcel source) {
            return new ApplicationInfoApkSource(source);
        }

        @Override
        public ApplicationInfoApkSource[] newArray(int size) {
            return new ApplicationInfoApkSource[size];
        }
    };
}
