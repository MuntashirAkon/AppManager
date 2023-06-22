// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.list;

import android.graphics.Bitmap;

public class AppListItem {
    public final String packageName;
    private Bitmap mIcon;
    private String mPackageLabel;
    private long mVersionCode;
    private String mVersionName;
    private int mMinSdk;
    private int mTargetSdk;
    private String mSignatureSha256;
    private long mFirstInstallTime;
    private long mLastUpdateTime;
    private String mInstallerPackageName;
    private String mInstallerPackageLabel;

    public AppListItem(String packageName) {
        this.packageName = packageName;
    }

    public Bitmap getIcon() {
        return mIcon;
    }

    public void setIcon(Bitmap icon) {
        mIcon = icon;
    }

    public String getPackageLabel() {
        return mPackageLabel;
    }

    public void setPackageLabel(String packageLabel) {
        mPackageLabel = packageLabel;
    }

    public long getVersionCode() {
        return mVersionCode;
    }

    public void setVersionCode(long versionCode) {
        mVersionCode = versionCode;
    }

    public String getVersionName() {
        return mVersionName;
    }

    public void setVersionName(String versionName) {
        mVersionName = versionName;
    }

    public int getMinSdk() {
        return mMinSdk;
    }

    public void setMinSdk(int minSdk) {
        mMinSdk = minSdk;
    }

    public int getTargetSdk() {
        return mTargetSdk;
    }

    public void setTargetSdk(int targetSdk) {
        mTargetSdk = targetSdk;
    }

    public String getSignatureSha256() {
        return mSignatureSha256;
    }

    public void setSignatureSha256(String signatureSha256) {
        mSignatureSha256 = signatureSha256;
    }

    public long getFirstInstallTime() {
        return mFirstInstallTime;
    }

    public void setFirstInstallTime(long firstInstallTime) {
        mFirstInstallTime = firstInstallTime;
    }

    public long getLastUpdateTime() {
        return mLastUpdateTime;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        mLastUpdateTime = lastUpdateTime;
    }

    public String getInstallerPackageName() {
        return mInstallerPackageName;
    }

    public void setInstallerPackageName(String installerPackageName) {
        mInstallerPackageName = installerPackageName;
    }

    public String getInstallerPackageLabel() {
        return mInstallerPackageLabel;
    }

    public void setInstallerPackageLabel(String installerPackageLabel) {
        mInstallerPackageLabel = installerPackageLabel;
    }
}
