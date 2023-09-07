// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import android.net.Uri;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import java.util.Objects;

public class UriApkSource extends ApkSource {
    @NonNull
    private final Uri mUri;
    @Nullable
    private final String mMimeType;

    private int mApkFileKey;

    public UriApkSource(@NonNull Uri uri, @Nullable String mimeType) {
        mUri = Objects.requireNonNull(uri);
        mMimeType = mimeType;
    }

    @NonNull
    @Override
    public ApkFile resolve() throws ApkFile.ApkFileException {
        ApkFile apkFile = ApkFile.getInstance(mApkFileKey);
        if (apkFile != null && !apkFile.isClosed()) {
            // Usable past instance
            return apkFile;
        }
        mApkFileKey = ApkFile.createInstance(mUri, mMimeType);
        return Objects.requireNonNull(ApkFile.getInstance(mApkFileKey));
    }

    @NonNull
    @Override
    public ApkSource toCachedSource() {
        return new CachedApkSource(mUri, mMimeType);
    }

    protected UriApkSource(@NonNull Parcel in) {
        mUri = Objects.requireNonNull(ParcelCompat.readParcelable(in, Uri.class.getClassLoader(), Uri.class));
        mMimeType = in.readString();
        mApkFileKey = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mUri, flags);
        dest.writeString(mMimeType);
        dest.writeInt(mApkFileKey);
    }

    public static final Creator<UriApkSource> CREATOR = new Creator<UriApkSource>() {
        @Override
        public UriApkSource createFromParcel(Parcel source) {
            return new UriApkSource(source);
        }

        @Override
        public UriApkSource[] newArray(int size) {
            return new UriApkSource[size];
        }
    };
}