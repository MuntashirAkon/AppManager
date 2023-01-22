// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagMatchUninstalled;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagSigningInfo;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagSigningInfoApk;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.UserHandleHidden;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.io.IoUtils;

public class PackageInstallerViewModel extends AndroidViewModel {
    private final PackageManager packageManager;
    private PackageInfo newPackageInfo;
    private PackageInfo installedPackageInfo;
    private int apkFileKey;
    private ApkFile apkFile;
    private String packageName;
    private String appLabel;
    private Drawable appIcon;
    private boolean isSignatureDifferent = false;
    @Nullable
    private List<UserInfo> users;
    private int trackerCount;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<PackageInfo> packageInfoLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> packageUninstalledLiveData = new MutableLiveData<>();

    public PackageInstallerViewModel(@NonNull Application application) {
        super(application);
        packageManager = application.getPackageManager();
    }

    @Override
    protected void onCleared() {
        IoUtils.closeQuietly(apkFile);
        executor.shutdownNow();
        super.onCleared();
    }

    public LiveData<PackageInfo> packageInfoLiveData() {
        return packageInfoLiveData;
    }

    public LiveData<Boolean> packageUninstalledLiveData() {
        return packageUninstalledLiveData;
    }

    @AnyThread
    public void getPackageInfo(ApkQueueItem apkQueueItem) {
        executor.submit(() -> {
            try {
                // Three possibilities: 1. Install-existing, 2. ApkFile, 3. Uri
                if (apkQueueItem.isInstallExisting()) {
                    if (apkQueueItem.getPackageName() == null) {
                        throw new IllegalArgumentException("Package name not set for install-existing.");
                    }
                    getExistingPackageInfoInternal(apkQueueItem.getPackageName());
                } else if (apkQueueItem.getApkFileKey() != -1) {
                    apkFileKey = apkQueueItem.getApkFileKey();
                    getPackageInfoInternal();
                } else if (apkQueueItem.getUri() != null) {
                    apkFileKey = ApkFile.createInstance(apkQueueItem.getUri(), apkQueueItem.getMimeType());
                    getPackageInfoInternal();
                } else {
                    throw new IllegalArgumentException("Invalid queue item.");
                }
                apkQueueItem.setApkFileKey(apkFileKey);
                apkQueueItem.setPackageName(packageName);
                apkQueueItem.setAppLabel(appLabel);
            } catch (Throwable th) {
                Log.e("PIVM", "Couldn't fetch package info", th);
                packageInfoLiveData.postValue(null);
            }
        });
    }

    public void uninstallPackage() {
        executor.submit(() -> {
            PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
            installer.setAppLabel(appLabel);
            packageUninstalledLiveData.postValue(installer.uninstall(packageName, UserHandleHidden.USER_ALL, false));
        });
    }

    public PackageInfo getNewPackageInfo() {
        return newPackageInfo;
    }

    @Nullable
    public PackageInfo getInstalledPackageInfo() {
        return installedPackageInfo;
    }

    public String getAppLabel() {
        return appLabel;
    }

    public Drawable getAppIcon() {
        return appIcon;
    }

    public String getPackageName() {
        return packageName;
    }

    public ApkFile getApkFile() {
        return apkFile;
    }

    public int getTrackerCount() {
        return trackerCount;
    }

    public int getApkFileKey() {
        return apkFileKey;
    }

    public boolean isSignatureDifferent() {
        return isSignatureDifferent;
    }

    @Nullable
    public List<UserInfo> getUsers() {
        return users;
    }

    private void getPackageInfoInternal() throws PackageManager.NameNotFoundException, IOException {
        apkFile = ApkFile.getInstance(this.apkFileKey);
        newPackageInfo = loadNewPackageInfo();
        packageName = newPackageInfo.packageName;
        try {
            installedPackageInfo = loadInstalledPackageInfo(packageName);
        } catch (PackageManager.NameNotFoundException ignore) {
        }
        appLabel = packageManager.getApplicationLabel(newPackageInfo.applicationInfo).toString();
        appIcon = packageManager.getApplicationIcon(newPackageInfo.applicationInfo);
        trackerCount = ComponentUtils.getTrackerComponentsForPackageInfo(newPackageInfo).size();
        if (newPackageInfo != null && installedPackageInfo != null) {
            isSignatureDifferent = PackageUtils.isSignatureDifferent(newPackageInfo, installedPackageInfo);
        }
        users = Users.getUsers();
        packageInfoLiveData.postValue(newPackageInfo);
    }

    private void getExistingPackageInfoInternal(@NonNull String packageName) throws PackageManager.NameNotFoundException, IOException, ApkFile.ApkFileException {
        this.packageName = packageName;
        installedPackageInfo = loadInstalledPackageInfo(packageName);
        apkFileKey = ApkFile.createInstance(installedPackageInfo.applicationInfo);
        apkFile = ApkFile.getInstance(this.apkFileKey);
        newPackageInfo = loadNewPackageInfo();
        appLabel = packageManager.getApplicationLabel(newPackageInfo.applicationInfo).toString();
        appIcon = packageManager.getApplicationIcon(newPackageInfo.applicationInfo);
        trackerCount = ComponentUtils.getTrackerComponentsForPackageInfo(newPackageInfo).size();
        if (newPackageInfo != null && installedPackageInfo != null) {
            isSignatureDifferent = PackageUtils.isSignatureDifferent(newPackageInfo, installedPackageInfo);
        }
        users = Users.getUsers();
        packageInfoLiveData.postValue(newPackageInfo);
    }

    @WorkerThread
    @NonNull
    private PackageInfo loadNewPackageInfo() throws PackageManager.NameNotFoundException, IOException {
        String apkPath = apkFile.getBaseEntry().getSignedFile().getAbsolutePath();
        @SuppressLint("WrongConstant")
        PackageInfo packageInfo = packageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_PERMISSIONS
                | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                | PackageManager.GET_SERVICES | flagDisabledComponents | flagSigningInfoApk
                | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_SHARED_LIBRARY_FILES);
        if (packageInfo == null) {
            throw new PackageManager.NameNotFoundException("Package cannot be parsed.");
        }
        packageInfo.applicationInfo.sourceDir = apkPath;
        packageInfo.applicationInfo.publicSourceDir = apkPath;
        return packageInfo;
    }

    @WorkerThread
    @NonNull
    private PackageInfo loadInstalledPackageInfo(String packageName) throws PackageManager.NameNotFoundException {
        @SuppressLint("WrongConstant")
        PackageInfo packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS
                | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                | PackageManager.GET_SERVICES | flagDisabledComponents | flagSigningInfo | flagMatchUninstalled
                | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_SHARED_LIBRARY_FILES);
        if (packageInfo == null) throw new PackageManager.NameNotFoundException("Package not found.");
        return packageInfo;
    }
}
