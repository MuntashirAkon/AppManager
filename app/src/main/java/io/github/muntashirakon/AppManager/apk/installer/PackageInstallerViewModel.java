/*
 * Copyright (c) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.apk.installer;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagMatchUninstalled;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagSigningInfo;

public class PackageInstallerViewModel extends AndroidViewModel {
    private final PackageManager packageManager;
    private PackageInfo newPackageInfo;
    private PackageInfo installedPackageInfo;
    private int apkFileKey;
    private ApkFile apkFile;
    private String packageName;
    private String appLabel;
    private Drawable appIcon;
    private String versionWithTrackers;
    private boolean closeApkFile = true;
    private boolean isSignatureDifferent = false;
    @Nullable
    private List<UserInfo> users;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public PackageInstallerViewModel(@NonNull Application application) {
        super(application);
        packageManager = application.getPackageManager();
    }

    @Override
    protected void onCleared() {
        if (closeApkFile && apkFile != null) apkFile.close();
        executor.shutdownNow();
        super.onCleared();
    }

    @UiThread
    public LiveData<PackageInfo> getPackageInfo(int apkFileKey, @Nullable Uri apkUri, @Nullable String mimeType) {
        MutableLiveData<PackageInfo> packageInfoMutableLiveData = new MutableLiveData<>();
        executor.submit(() -> {
            try {
                if (apkUri != null) {
                    this.apkFileKey = ApkFile.createInstance(apkUri, mimeType);
                } else if (apkFileKey != -1) {
                    this.apkFileKey = apkFileKey;
                    closeApkFile = false;  // Internal request, don't close the ApkFile
                } else throw new Exception("Both Uri and APK file key is empty");
                apkFile = ApkFile.getInstance(this.apkFileKey);
                newPackageInfo = loadNewPackageInfo();
                packageName = newPackageInfo.packageName;
                try {
                    installedPackageInfo = loadInstalledPackageInfo(packageName);
                } catch (PackageManager.NameNotFoundException ignore) {
                }
                appLabel = packageManager.getApplicationLabel(newPackageInfo.applicationInfo).toString();
                appIcon = packageManager.getApplicationIcon(newPackageInfo.applicationInfo);
                versionWithTrackers = loadVersionInfoWithTrackers();
                if (newPackageInfo != null && installedPackageInfo != null) {
                    isSignatureDifferent = PackageUtils.isSignatureDifferent(newPackageInfo, installedPackageInfo);
                }
                users = Users.getUsers();
                packageInfoMutableLiveData.postValue(newPackageInfo);
            } catch (Throwable th) {
                Log.e("PIVM", "Couldn't fetch package info", th);
                packageInfoMutableLiveData.postValue(null);
            }
        });
        return packageInfoMutableLiveData;
    }

    public PackageInfo getNewPackageInfo() {
        return newPackageInfo;
    }

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

    public String getVersionWithTrackers() {
        return versionWithTrackers;
    }

    public int getApkFileKey() {
        return apkFileKey;
    }

    public boolean isSignatureDifferent() {
        return isSignatureDifferent;
    }

    public boolean isCloseApkFile() {
        return closeApkFile;
    }

    public void setCloseApkFile(boolean closeApkFile) {
        this.closeApkFile = closeApkFile;
    }

    @Nullable
    public List<UserInfo> getUsers() {
        return users;
    }

    @WorkerThread
    @NonNull
    private String loadVersionInfoWithTrackers() {
        Resources res = getApplication().getResources();
        long newVersionCode = PackageInfoCompat.getLongVersionCode(newPackageInfo);
        String newVersionName = newPackageInfo.versionName;
        int trackers = ComponentUtils.getTrackerComponentsForPackageInfo(newPackageInfo).size();
        StringBuilder sb = new StringBuilder(res.getString(R.string.version_name_with_code, newVersionName, newVersionCode));
        if (trackers > 0) {
            sb.append(", ").append(res.getQuantityString(R.plurals.no_of_trackers, trackers, trackers));
        }
        return sb.toString();
    }

    @WorkerThread
    @SuppressWarnings("deprecation")
    @NonNull
    private PackageInfo loadNewPackageInfo() throws PackageManager.NameNotFoundException, IOException, RemoteException {
        String apkPath = apkFile.getBaseEntry().getSignedFile(getApplication()).getAbsolutePath();
        @SuppressLint("WrongConstant")
        PackageInfo packageInfo = packageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_PERMISSIONS
                | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                | flagDisabledComponents | PackageManager.GET_SIGNATURES | PackageManager.GET_CONFIGURATIONS
                | PackageManager.GET_SHARED_LIBRARY_FILES);
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
                | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS | flagDisabledComponents
                | flagSigningInfo | flagMatchUninstalled | PackageManager.GET_CONFIGURATIONS
                | PackageManager.GET_SHARED_LIBRARY_FILES);
        if (packageInfo == null) throw new PackageManager.NameNotFoundException("Package not found.");
        return packageInfo;
    }
}
