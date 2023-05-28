// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageManager;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.os.ParcelCompat;

public class InstallSourceInfoCompat implements Parcelable {

    @Nullable
    private final String mInitiatingPackageName;

    @RequiresApi(Build.VERSION_CODES.P)
    @Nullable
    private SigningInfo mInitiatingPackageSigningInfo;

    @Nullable
    private final String mOriginatingPackageName;

    @Nullable
    private final String mInstallingPackageName;

    @RequiresApi(Build.VERSION_CODES.R)
    public InstallSourceInfoCompat(@Nullable InstallSourceInfo installSourceInfo) {
        if (installSourceInfo != null) {
            mInitiatingPackageName = installSourceInfo.getInitiatingPackageName();
            mInitiatingPackageSigningInfo = installSourceInfo.getInitiatingPackageSigningInfo();
            mOriginatingPackageName = installSourceInfo.getOriginatingPackageName();
            mInstallingPackageName = installSourceInfo.getInstallingPackageName();
        } else {
            mInitiatingPackageName = null;
            mOriginatingPackageName = null;
            mInstallingPackageName = null;
        }
    }

    public InstallSourceInfoCompat(@Nullable String installingPackageName) {
        mInitiatingPackageName = null;
        mOriginatingPackageName = null;
        mInstallingPackageName = installingPackageName;
    }

    @Override
    public int describeContents() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && mInitiatingPackageSigningInfo != null) {
            return mInitiatingPackageSigningInfo.describeContents();
        }
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mInitiatingPackageName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dest.writeParcelable(mInitiatingPackageSigningInfo, flags);
        }
        dest.writeString(mOriginatingPackageName);
        dest.writeString(mInstallingPackageName);
    }

    private InstallSourceInfoCompat(Parcel source) {
        mInitiatingPackageName = source.readString();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mInitiatingPackageSigningInfo = ParcelCompat.readParcelable(source, SigningInfo.class.getClassLoader(), SigningInfo.class);
        }
        mOriginatingPackageName = source.readString();
        mInstallingPackageName = source.readString();
    }

    /**
     * The name of the package that requested the installation, or null if not available.
     * <p>
     * This is normally the same as the installing package name. If the installing package name
     * is changed, for example by calling
     * {@link PackageManager#setInstallerPackageName(String, String)}, the initiating package name
     * remains unchanged. It continues to identify the actual package that performed the install
     * or update.
     * <p>
     * Null may be returned if the app was not installed by a package (e.g. a system app or an app
     * installed via adb) or if the initiating package has itself been uninstalled.
     */
    @Nullable
    public String getInitiatingPackageName() {
        return mInitiatingPackageName;
    }

    /**
     * Information about the signing certificates used to sign the initiating package, if available.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    @Nullable
    public SigningInfo getInitiatingPackageSigningInfo() {
        return mInitiatingPackageSigningInfo;
    }

    /**
     * The name of the package on behalf of which the initiating package requested the installation,
     * or null if not available.
     * <p>
     * For example if a downloaded APK is installed via the Package Installer this could be the
     * app that performed the download. This value is provided by the initiating package and not
     * verified by the framework.
     * <p>
     * Note that the {@code InstallSourceInfo} returned by
     * {@link PackageManager#getInstallSourceInfo(String)} will not have this information
     * available unless the calling application holds the INSTALL_PACKAGES permission.
     */
    @Nullable
    public String getOriginatingPackageName() {
        return mOriginatingPackageName;
    }

    /**
     * The name of the package responsible for the installation (the installer of record), or null
     * if not available.
     * Note that this may differ from the initiating package name and can be modified via
     * {@link PackageManager#setInstallerPackageName(String, String)}.
     * <p>
     * Null may be returned if the app was not installed by a package (e.g. a system app or an app
     * installed via adb) or if the installing package has itself been uninstalled.
     */
    @Nullable
    public String getInstallingPackageName() {
        return mInstallingPackageName;
    }

    @NonNull
    public static final Parcelable.Creator<InstallSourceInfoCompat> CREATOR = new Creator<InstallSourceInfoCompat>() {
        @Override
        public InstallSourceInfoCompat createFromParcel(Parcel source) {
            return new InstallSourceInfoCompat(source);
        }

        @Override
        public InstallSourceInfoCompat[] newArray(int size) {
            return new InstallSourceInfoCompat[size];
        }
    };
}
