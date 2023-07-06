// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.annotation.UserIdInt;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.settings.Prefs;

public class InstallerOptions implements Parcelable {
    @UserIdInt
    private int mUserId;
    private int mInstallLocation;
    @Nullable
    private String mInstallerName;
    private boolean mSignApkFiles;
    private boolean mForceDexOpt;
    private boolean mBlockTrackers;

    public InstallerOptions() {
        mUserId = UserHandleHidden.myUserId();
        mInstallLocation = Prefs.Installer.getInstallLocation();
        mInstallerName = Prefs.Installer.getInstallerPackageName();
        mSignApkFiles = Prefs.Installer.canSignApk();
        mForceDexOpt = Prefs.Installer.forceDexOpt();
        mBlockTrackers = Prefs.Installer.blockTrackers();
    }

    protected InstallerOptions(Parcel in) {
        mUserId = in.readInt();
        mInstallLocation = in.readInt();
        mInstallerName = in.readString();
        mSignApkFiles = in.readByte() != 0;
        mForceDexOpt = in.readByte() != 0;
        mBlockTrackers = in.readByte() != 0;
    }

    public void copy(@NonNull InstallerOptions options) {
        mUserId = options.mUserId;
        mInstallLocation = options.mInstallLocation;
        mInstallerName = options.mInstallerName;
        mSignApkFiles = options.mSignApkFiles;
        mForceDexOpt = options.mForceDexOpt;
        mBlockTrackers = options.mBlockTrackers;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mUserId);
        dest.writeInt(mInstallLocation);
        dest.writeString(mInstallerName);
        dest.writeByte((byte) (mSignApkFiles ? 1 : 0));
        dest.writeByte((byte) (mForceDexOpt ? 1 : 0));
        dest.writeByte((byte) (mBlockTrackers ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<InstallerOptions> CREATOR = new Creator<InstallerOptions>() {
        @Override
        public InstallerOptions createFromParcel(Parcel in) {
            return new InstallerOptions(in);
        }

        @Override
        public InstallerOptions[] newArray(int size) {
            return new InstallerOptions[size];
        }
    };

    @UserIdInt
    public int getUserId() {
        return mUserId;
    }

    public void setUserId(@UserIdInt int userId) {
        mUserId = userId;
    }

    public int getInstallLocation() {
        return mInstallLocation;
    }

    public void setInstallLocation(int installLocation) {
        mInstallLocation = installLocation;
    }

    @NonNull
    public String getInstallerName() {
        return !TextUtils.isEmpty(mInstallerName) ? mInstallerName : BuildConfig.APPLICATION_ID;
    }

    public void setInstallerName(@Nullable String installerName) {
        mInstallerName = installerName;
    }

    public boolean isSignApkFiles() {
        return mSignApkFiles;
    }

    public void setSignApkFiles(boolean signApkFiles) {
        mSignApkFiles = signApkFiles;
    }

    public boolean isForceDexOpt() {
        return mForceDexOpt;
    }

    public void setForceDexOpt(boolean forceDexOpt) {
        mForceDexOpt = forceDexOpt;
    }

    public boolean isBlockTrackers() {
        return mBlockTrackers;
    }

    public void setBlockTrackers(boolean blockTrackers) {
        mBlockTrackers = blockTrackers;
    }
}
