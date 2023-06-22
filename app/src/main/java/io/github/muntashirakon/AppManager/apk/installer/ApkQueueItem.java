// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity.EXTRA_INSTALL_EXISTING;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity.EXTRA_PACKAGE_NAME;

import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.intercept.IntentCompat;

public class ApkQueueItem implements Parcelable {
    @NonNull
    static List<ApkQueueItem> fromIntent(@NonNull Intent intent) {
        List<ApkQueueItem> apkQueueItems = new ArrayList<>();
        boolean installExisting = intent.getBooleanExtra(EXTRA_INSTALL_EXISTING, false);
        String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        if (installExisting && packageName != null) {
            apkQueueItems.add(new ApkQueueItem(packageName, true));
        }
        List<Uri> uris = IntentCompat.getDataUris(intent);
        if (uris == null) {
            return apkQueueItems;
        }
        String mimeType = intent.getType();
        for (Uri uri : uris) {
            apkQueueItems.add(new ApkQueueItem(uri, mimeType));
        }
        return apkQueueItems;
    }

    @Nullable
    private Uri mUri;
    @Nullable
    private String mPackageName;
    @Nullable
    private String mAppLabel;
    @Nullable
    private String mMimeType;
    private boolean mInstallExisting;
    private int mApkFileKey = -1;
    private int mUserId;

    ApkQueueItem(@NonNull Uri uri, @Nullable String mimeType) {
        mUri = uri;
        mMimeType = mimeType;
    }

    ApkQueueItem(@NonNull String packageName, boolean installExisting) {
        mPackageName = packageName;
        mInstallExisting = installExisting;
        assert installExisting;
    }

    ApkQueueItem(int apkFileKey) {
        mApkFileKey = apkFileKey;
        assert apkFileKey != -1;
    }

    protected ApkQueueItem(@NonNull Parcel in) {
        mUri = ParcelCompat.readParcelable(in, Uri.class.getClassLoader(), Uri.class);
        mPackageName = in.readString();
        mAppLabel = in.readString();
        mMimeType = in.readString();
        mInstallExisting = in.readByte() != 0;
        mApkFileKey = in.readInt();
        mUserId = in.readInt();
    }

    @Nullable
    public Uri getUri() {
        return mUri;
    }

    public void setUri(@Nullable Uri uri) {
        mUri = uri;
    }

    @Nullable
    public String getMimeType() {
        return mMimeType;
    }

    public void setMimeType(@Nullable String mimeType) {
        mMimeType = mimeType;
    }

    @Nullable
    public String getPackageName() {
        return mPackageName;
    }

    public void setPackageName(@Nullable String packageName) {
        mPackageName = packageName;
    }

    public void setInstallExisting(boolean installExisting) {
        mInstallExisting = installExisting;
    }

    public boolean isInstallExisting() {
        return mInstallExisting;
    }

    public int getApkFileKey() {
        return mApkFileKey;
    }

    public void setApkFileKey(int apkFileKey) {
        mApkFileKey = apkFileKey;
    }

    public int getUserId() {
        return mUserId;
    }

    public void setUserId(int userId) {
        mUserId = userId;
    }

    @Nullable
    public String getAppLabel() {
        return mAppLabel;
    }

    public void setAppLabel(@Nullable String appLabel) {
        mAppLabel = appLabel;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mUri, flags);
        dest.writeString(mPackageName);
        dest.writeString(mAppLabel);
        dest.writeString(mMimeType);
        dest.writeByte((byte) (mInstallExisting ? 1 : 0));
        dest.writeInt(mApkFileKey);
        dest.writeInt(mUserId);
    }

    public static final Creator<ApkQueueItem> CREATOR = new Creator<ApkQueueItem>() {
        @Override
        @NonNull
        public ApkQueueItem createFromParcel(@NonNull Parcel in) {
            return new ApkQueueItem(in);
        }

        @Override
        @NonNull
        public ApkQueueItem[] newArray(int size) {
            return new ApkQueueItem[size];
        }
    };
}
