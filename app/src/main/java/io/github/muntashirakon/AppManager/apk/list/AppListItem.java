// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.list;

import android.graphics.Bitmap;

public class AppListItem {
    public final String packageName;
    private Bitmap icon;
    private String packageLabel;
    private long versionCode;
    private String versionName;
    private int minSdk;
    private int targetSdk;
    private String signatureSha256;
    private long firstInstallTime;
    private long lastUpdateTime;
    private String installerPackageName;
    private String installerPackageLabel;

    public AppListItem(String packageName) {
        this.packageName = packageName;
    }

    public Bitmap getIcon() {
        return icon;
    }

    public void setIcon(Bitmap icon) {
        this.icon = icon;
    }

    public String getPackageLabel() {
        return packageLabel;
    }

    public void setPackageLabel(String packageLabel) {
        this.packageLabel = packageLabel;
    }

    public long getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(long versionCode) {
        this.versionCode = versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public int getMinSdk() {
        return minSdk;
    }

    public void setMinSdk(int minSdk) {
        this.minSdk = minSdk;
    }

    public int getTargetSdk() {
        return targetSdk;
    }

    public void setTargetSdk(int targetSdk) {
        this.targetSdk = targetSdk;
    }

    public String getSignatureSha256() {
        return signatureSha256;
    }

    public void setSignatureSha256(String signatureSha256) {
        this.signatureSha256 = signatureSha256;
    }

    public long getFirstInstallTime() {
        return firstInstallTime;
    }

    public void setFirstInstallTime(long firstInstallTime) {
        this.firstInstallTime = firstInstallTime;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public String getInstallerPackageName() {
        return installerPackageName;
    }

    public void setInstallerPackageName(String installerPackageName) {
        this.installerPackageName = installerPackageName;
    }

    public String getInstallerPackageLabel() {
        return installerPackageLabel;
    }

    public void setInstallerPackageLabel(String installerPackageLabel) {
        this.installerPackageLabel = installerPackageLabel;
    }
}
