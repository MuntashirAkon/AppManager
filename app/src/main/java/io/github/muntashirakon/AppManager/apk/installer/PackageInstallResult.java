// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

public final class PackageInstallResult implements Parcelable {
    @NonNull
    private final String mOperationId;
    @NonNull
    private final String mPackageName;
    @PackageInstallerCompat.Status
    private final int mStatus;
    @Nullable
    private final String mBlockingPackage;
    @Nullable
    private final String mStatusMessage;

    public PackageInstallResult(@NonNull String operationId, @NonNull String packageName,
                                @PackageInstallerCompat.Status int status,
                                @Nullable String blockingPackage, @Nullable String statusMessage) {
        mOperationId = operationId;
        mPackageName = packageName;
        mStatus = status;
        mBlockingPackage = blockingPackage;
        mStatusMessage = statusMessage;
    }

    private PackageInstallResult(@NonNull Parcel in) {
        mOperationId = Objects.requireNonNull(in.readString());
        mPackageName = Objects.requireNonNull(in.readString());
        mStatus = in.readInt();
        mBlockingPackage = in.readString();
        mStatusMessage = in.readString();
    }

    @NonNull
    public String getOperationId() {
        return mOperationId;
    }

    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    @PackageInstallerCompat.Status
    public int getStatus() {
        return mStatus;
    }

    @Nullable
    public String getBlockingPackage() {
        return mBlockingPackage;
    }

    @Nullable
    public String getStatusMessage() {
        return mStatusMessage;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mOperationId);
        dest.writeString(mPackageName);
        dest.writeInt(mStatus);
        dest.writeString(mBlockingPackage);
        dest.writeString(mStatusMessage);
    }

    public static final Creator<PackageInstallResult> CREATOR = new Creator<PackageInstallResult>() {
        @Override
        @NonNull
        public PackageInstallResult createFromParcel(@NonNull Parcel in) {
            return new PackageInstallResult(in);
        }

        @Override
        @NonNull
        public PackageInstallResult[] newArray(int size) {
            return new PackageInstallResult[size];
        }
    };
}
