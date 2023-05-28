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
    private Uri uri;
    @Nullable
    private String packageName;
    @Nullable
    private String appLabel;
    @Nullable
    private String mimeType;
    private boolean installExisting;
    private int apkFileKey = -1;
    private int userId;

    ApkQueueItem(@NonNull Uri uri, @Nullable String mimeType) {
        this.uri = uri;
        this.mimeType = mimeType;
    }

    ApkQueueItem(@NonNull String packageName, boolean installExisting) {
        this.packageName = packageName;
        this.installExisting = installExisting;
        assert installExisting;
    }

    ApkQueueItem(int apkFileKey) {
        this.apkFileKey = apkFileKey;
        assert apkFileKey != -1;
    }

    protected ApkQueueItem(@NonNull Parcel in) {
        uri = ParcelCompat.readParcelable(in, Uri.class.getClassLoader(), Uri.class);
        packageName = in.readString();
        appLabel = in.readString();
        mimeType = in.readString();
        installExisting = in.readByte() != 0;
        apkFileKey = in.readInt();
        userId = in.readInt();
    }

    @Nullable
    public Uri getUri() {
        return uri;
    }

    public void setUri(@Nullable Uri uri) {
        this.uri = uri;
    }

    @Nullable
    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(@Nullable String mimeType) {
        this.mimeType = mimeType;
    }

    @Nullable
    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(@Nullable String packageName) {
        this.packageName = packageName;
    }

    public void setInstallExisting(boolean installExisting) {
        this.installExisting = installExisting;
    }

    public boolean isInstallExisting() {
        return installExisting;
    }

    public int getApkFileKey() {
        return apkFileKey;
    }

    public void setApkFileKey(int apkFileKey) {
        this.apkFileKey = apkFileKey;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    @Nullable
    public String getAppLabel() {
        return appLabel;
    }

    public void setAppLabel(@Nullable String appLabel) {
        this.appLabel = appLabel;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(uri, flags);
        dest.writeString(packageName);
        dest.writeString(appLabel);
        dest.writeString(mimeType);
        dest.writeByte((byte) (installExisting ? 1 : 0));
        dest.writeInt(apkFileKey);
        dest.writeInt(userId);
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
