// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.GET_SIGNING_CERTIFICATES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.GET_SIGNING_CERTIFICATES_APK;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

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
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.IoUtils;

public class PackageInstallerViewModel extends AndroidViewModel {
    private final PackageManager mPm;
    private PackageInfo mNewPackageInfo;
    private PackageInfo mInstalledPackageInfo;
    private int mApkFileKey;
    private ApkFile mApkFile;
    private String mPackageName;
    private String mAppLabel;
    private Drawable mAppIcon;
    private boolean mIsSignatureDifferent = false;
    @Nullable
    private List<UserInfo> mUsers;
    private int mTrackerCount;
    @Nullable
    private Future<?> mPackageInfoResult;
    private final MutableLiveData<PackageInfo> mPackageInfoLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mPackageUninstalledLiveData = new MutableLiveData<>();

    public PackageInstallerViewModel(@NonNull Application application) {
        super(application);
        mPm = application.getPackageManager();
    }

    @Override
    protected void onCleared() {
        IoUtils.closeQuietly(mApkFile);
        if (mPackageInfoResult != null) {
            mPackageInfoResult.cancel(true);
        }
        super.onCleared();
    }

    public LiveData<PackageInfo> packageInfoLiveData() {
        return mPackageInfoLiveData;
    }

    public LiveData<Boolean> packageUninstalledLiveData() {
        return mPackageUninstalledLiveData;
    }

    @AnyThread
    public void getPackageInfo(ApkQueueItem apkQueueItem) {
        if (mPackageInfoResult != null) {
            mPackageInfoResult.cancel(true);
        }
        mPackageInfoResult = ThreadUtils.postOnBackgroundThread(() -> {
            try {
                // Three possibilities: 1. Install-existing, 2. ApkFile, 3. Uri
                if (apkQueueItem.isInstallExisting()) {
                    if (apkQueueItem.getPackageName() == null) {
                        throw new IllegalArgumentException("Package name not set for install-existing.");
                    }
                    getExistingPackageInfoInternal(apkQueueItem.getPackageName());
                } else if (apkQueueItem.getApkFileKey() != -1) {
                    mApkFileKey = apkQueueItem.getApkFileKey();
                    getPackageInfoInternal();
                } else if (apkQueueItem.getUri() != null) {
                    mApkFileKey = ApkFile.createInstance(apkQueueItem.getUri(), apkQueueItem.getMimeType());
                    getPackageInfoInternal();
                } else {
                    throw new IllegalArgumentException("Invalid queue item.");
                }
                apkQueueItem.setApkFileKey(mApkFileKey);
                apkQueueItem.setPackageName(mPackageName);
                apkQueueItem.setAppLabel(mAppLabel);
            } catch (Throwable th) {
                Log.e("PIVM", "Couldn't fetch package info", th);
                mPackageInfoLiveData.postValue(null);
            }
        });
    }

    public void uninstallPackage() {
        ThreadUtils.postOnBackgroundThread(() -> {
            PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
            installer.setAppLabel(mAppLabel);
            mPackageUninstalledLiveData.postValue(installer.uninstall(mPackageName, UserHandleHidden.USER_ALL, false));
        });
    }

    public PackageInfo getNewPackageInfo() {
        return mNewPackageInfo;
    }

    @Nullable
    public PackageInfo getInstalledPackageInfo() {
        return mInstalledPackageInfo;
    }

    public String getAppLabel() {
        return mAppLabel;
    }

    public Drawable getAppIcon() {
        return mAppIcon;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public ApkFile getApkFile() {
        return mApkFile;
    }

    public int getTrackerCount() {
        return mTrackerCount;
    }

    public int getApkFileKey() {
        return mApkFileKey;
    }

    public boolean isSignatureDifferent() {
        return mIsSignatureDifferent;
    }

    @Nullable
    public List<UserInfo> getUsers() {
        return mUsers;
    }

    private void getPackageInfoInternal() throws PackageManager.NameNotFoundException, IOException {
        mApkFile = ApkFile.getInstance(mApkFileKey);
        mNewPackageInfo = loadNewPackageInfo();
        mPackageName = mNewPackageInfo.packageName;
        if (ThreadUtils.isInterrupted()) {
            return;
        }
        try {
            mInstalledPackageInfo = loadInstalledPackageInfo(mPackageName);
            if (ThreadUtils.isInterrupted()) {
                return;
            }
        } catch (PackageManager.NameNotFoundException ignore) {
        }
        mAppLabel = mPm.getApplicationLabel(mNewPackageInfo.applicationInfo).toString();
        mAppIcon = mPm.getApplicationIcon(mNewPackageInfo.applicationInfo);
        mTrackerCount = ComponentUtils.getTrackerComponentsForPackage(mNewPackageInfo).size();
        if (ThreadUtils.isInterrupted()) {
            return;
        }
        if (mNewPackageInfo != null && mInstalledPackageInfo != null) {
            mIsSignatureDifferent = PackageUtils.isSignatureDifferent(mNewPackageInfo, mInstalledPackageInfo);
        }
        mUsers = Users.getUsers();
        mPackageInfoLiveData.postValue(mNewPackageInfo);
    }

    private void getExistingPackageInfoInternal(@NonNull String packageName) throws PackageManager.NameNotFoundException, IOException, ApkFile.ApkFileException {
        mPackageName = packageName;
        mInstalledPackageInfo = loadInstalledPackageInfo(packageName);
        mApkFileKey = ApkFile.createInstance(mInstalledPackageInfo.applicationInfo);
        mApkFile = ApkFile.getInstance(mApkFileKey);
        mNewPackageInfo = loadNewPackageInfo();
        mAppLabel = mPm.getApplicationLabel(mNewPackageInfo.applicationInfo).toString();
        mAppIcon = mPm.getApplicationIcon(mNewPackageInfo.applicationInfo);
        mTrackerCount = ComponentUtils.getTrackerComponentsForPackage(mNewPackageInfo).size();
        if (mNewPackageInfo != null && mInstalledPackageInfo != null) {
            mIsSignatureDifferent = PackageUtils.isSignatureDifferent(mNewPackageInfo, mInstalledPackageInfo);
        }
        mUsers = Users.getUsers();
        mPackageInfoLiveData.postValue(mNewPackageInfo);
    }

    @WorkerThread
    @NonNull
    private PackageInfo loadNewPackageInfo() throws PackageManager.NameNotFoundException, IOException {
        String apkPath = mApkFile.getBaseEntry().getSignedFile().getAbsolutePath();
        @SuppressLint("WrongConstant")
        PackageInfo packageInfo = mPm.getPackageArchiveInfo(apkPath, PackageManager.GET_PERMISSIONS
                | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                | PackageManager.GET_SERVICES | MATCH_DISABLED_COMPONENTS | GET_SIGNING_CERTIFICATES_APK
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
        PackageInfo packageInfo = mPm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS
                | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                | PackageManager.GET_SERVICES | MATCH_DISABLED_COMPONENTS | GET_SIGNING_CERTIFICATES | MATCH_UNINSTALLED_PACKAGES
                | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_SHARED_LIBRARY_FILES);
        if (packageInfo == null) throw new PackageManager.NameNotFoundException("Package not found.");
        return packageInfo;
    }
}
