// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity.EXTRA_INSTALL_EXISTING;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity.EXTRA_PACKAGE_NAME;
import static io.github.muntashirakon.AppManager.apk.installer.SupportedAppStores.isAppStoreSupported;

import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;

public class ApkQueueItem implements Parcelable {
    @NonNull
    static List<ApkQueueItem> fromIntent(@NonNull Intent intent,
                                         @Nullable String originatingPackage) {
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
        Uri originatingUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_ORIGINATING_URI, Uri.class);
        for (Uri uri : uris) {
            ApkQueueItem item = new ApkQueueItem(ApkSource.getCachedApkSource(uri, mimeType));
            item.mOriginatingUri = originatingUri;
            item.mOriginatingPackage = originatingPackage;
            apkQueueItems.add(item);
        }
        return apkQueueItems;
    }

    @NonNull
    public static ApkQueueItem fromApkSource(@NonNull ApkSource apkSource) {
        return new ApkQueueItem(apkSource.toCachedSource());
    }

    @Nullable
    private String mPackageName;
    @Nullable
    private String mAppLabel;
    private final boolean mInstallExisting;
    @Nullable
    private String mOriginatingPackage;
    @Nullable
    private Uri mOriginatingUri;
    @Nullable
    private ApkSource mApkSource;
    @Nullable
    private InstallerOptions mInstallerOptions;
    @Nullable
    private ArrayList<String> mSelectedSplits;

    private ApkQueueItem(@NonNull String packageName, boolean installExisting) {
        mPackageName = Objects.requireNonNull(packageName);
        mInstallExisting = installExisting;
        assert installExisting;
    }

    private ApkQueueItem(@NonNull ApkSource apkSource) {
        mApkSource = Objects.requireNonNull(apkSource);
        mInstallExisting = false;
    }

    protected ApkQueueItem(@NonNull Parcel in) {
        mPackageName = in.readString();
        mAppLabel = in.readString();
        mInstallExisting = in.readByte() != 0;
        mOriginatingPackage = in.readString();
        mOriginatingUri = ParcelCompat.readParcelable(in, Uri.class.getClassLoader(), Uri.class);
        mApkSource = ParcelCompat.readParcelable(in, ApkSource.class.getClassLoader(), ApkSource.class);
        mInstallerOptions = ParcelCompat.readParcelable(in, InstallerOptions.class.getClassLoader(), InstallerOptions.class);
        mSelectedSplits = new ArrayList<>();
        in.readStringList(mSelectedSplits);
    }

    @Nullable
    public String getPackageName() {
        return mPackageName;
    }

    public void setPackageName(@Nullable String packageName) {
        mPackageName = packageName;
    }

    public boolean isInstallExisting() {
        return mInstallExisting;
    }

    @Nullable
    public ApkSource getApkSource() {
        return mApkSource;
    }

    public void setApkSource(@Nullable ApkSource apkSource) {
        mApkSource = apkSource;
    }

    @Nullable
    public InstallerOptions getInstallerOptions() {
        return mInstallerOptions;
    }

    public void setInstallerOptions(@Nullable InstallerOptions installerOptions) {
        if (installerOptions != null) {
            installerOptions.setOriginatingPackage(mOriginatingPackage);
            installerOptions.setOriginatingUri(mOriginatingUri);
            // Set package source to PACKAGE_SOURCE_STORE if it's supported
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && mOriginatingPackage != null
                    && isAppStoreSupported(mOriginatingPackage)) {
                installerOptions.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE);
            }
        }
        mInstallerOptions = installerOptions;
    }

    public void setSelectedSplits(@NonNull ArrayList<String> selectedSplits) {
        mSelectedSplits = selectedSplits;
    }

    @Nullable
    public ArrayList<String> getSelectedSplits() {
        return mSelectedSplits;
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
        dest.writeString(mPackageName);
        dest.writeString(mAppLabel);
        dest.writeByte((byte) (mInstallExisting ? 1 : 0));
        dest.writeString(mOriginatingPackage);
        dest.writeParcelable(mOriginatingUri, flags);
        dest.writeParcelable(mApkSource, flags);
        dest.writeParcelable(mInstallerOptions, flags);
        dest.writeStringList(mSelectedSplits);
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
