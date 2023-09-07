// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.Paths;

public class CachedApkSource extends ApkSource {
    @NonNull
    private final Uri mUri;
    private final String mMimeType;

    private int mApkFileKey;
    private File mCachedFile;

    CachedApkSource(@NonNull Uri uri, @Nullable String mimeType) {
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
        // May need to cache the APK if it's not from our own content provider
        if (mCachedFile != null && mCachedFile.exists()) {
            mApkFileKey = ApkFile.createInstance(Uri.fromFile(mCachedFile), mMimeType);
        } else if (ContentResolver.SCHEME_FILE.equals(mUri.getScheme())) {
            mApkFileKey = ApkFile.createInstance(mUri, mMimeType);
        } else if (ContentResolver.SCHEME_CONTENT.equals(mUri.getScheme())
                && FmProvider.AUTHORITY.equals(mUri.getAuthority())) {
            mApkFileKey = ApkFile.createInstance(mUri, mMimeType);
        } else {
            // Need caching
            try {
                mCachedFile = FileCache.getGlobalFileCache().getCachedFile(Paths.get(mUri));
                mApkFileKey = ApkFile.createInstance(Uri.fromFile(mCachedFile), mMimeType);
            } catch (IOException | SecurityException e) {
                throw new ApkFile.ApkFileException(e);
            }
        }
        return Objects.requireNonNull(ApkFile.getInstance(mApkFileKey));
    }

    @NonNull
    @Override
    public ApkSource toCachedSource() {
        Uri uri;
        if (mCachedFile != null && mCachedFile.exists()) {
            uri = Uri.fromFile(mCachedFile);
        } else uri = mUri;
        return new CachedApkSource(uri, mMimeType);
    }

    public void cleanup() {
        FileUtils.deleteSilently(mCachedFile);
    }

    protected CachedApkSource(@NonNull Parcel in) {
        mUri = Objects.requireNonNull(ParcelCompat.readParcelable(in, Uri.class.getClassLoader(), Uri.class));
        mMimeType = in.readString();
        mApkFileKey = in.readInt();
        String file = in.readString();
        if (file != null) {
            mCachedFile = new File(file);
        }
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
        String file = mCachedFile != null ? mCachedFile.getAbsolutePath() : null;
        dest.writeString(file);
    }

    public static final Creator<CachedApkSource> CREATOR = new Creator<CachedApkSource>() {
        @Override
        public CachedApkSource createFromParcel(Parcel source) {
            return new CachedApkSource(source);
        }

        @Override
        public CachedApkSource[] newArray(int size) {
            return new CachedApkSource[size];
        }
    };
}
