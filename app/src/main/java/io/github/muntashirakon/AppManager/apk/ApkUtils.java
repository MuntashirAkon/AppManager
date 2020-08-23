/*
 * Copyright (C) 2020 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.apk;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.apk.splitapk.SplitApkExporter;
import io.github.muntashirakon.AppManager.utils.IOUtils;

import static io.github.muntashirakon.AppManager.backup.BackupStorageManager.getApkBackupDirectory;
import static io.github.muntashirakon.AppManager.utils.IOUtils.copy;

public final class ApkUtils {
    public static final String EXT_APK = ".apk";
    public static final String EXT_APKS = ".apks";

    @NonNull
    public static File getSharableApkFile(@NonNull PackageInfo packageInfo) throws Exception {
        ApplicationInfo info = packageInfo.applicationInfo;
        PackageManager pm = AppManager.getContext().getPackageManager();
        String outputName = info.loadLabel(pm).toString() + "_" + packageInfo.versionName;
        if (isSplitApk(info)) {
            // Split apk
            File tmpPublicSource = new File(AppManager.getContext().getExternalCacheDir(), outputName + EXT_APKS);
            SplitApkExporter.saveApks(packageInfo, tmpPublicSource);
            return tmpPublicSource;
        } else {
            // Regular apk
            File tmpPublicSource = new File(AppManager.getContext().getExternalCacheDir(), outputName + EXT_APK);
            try (FileInputStream apkInputStream = new FileInputStream(packageInfo.applicationInfo.publicSourceDir);
                 FileOutputStream apkOutputStream = new FileOutputStream(tmpPublicSource)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    FileUtils.copy(apkInputStream, apkOutputStream);
                } else copy(apkInputStream, apkOutputStream);
            }
            return tmpPublicSource;
        }
    }

    /**
     * Backup the given apk (both root and non root). This is similar to apk sharing feature except
     * that these are saved at /sdcard/AppManager/apks
     * @return true on success, false on failure
     */
    public static boolean backupApk(String packageName) {
        File backupPath = getApkBackupDirectory();
        if (!backupPath.exists()) {
            if (!backupPath.mkdirs()) return false;
        }
        // Fetch package info
        try {
            PackageManager pm = AppManager.getContext().getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(packageName, 0);
            ApplicationInfo info = packageInfo.applicationInfo;
            String outputName = info.loadLabel(pm).toString() + "_" + packageInfo.versionName;
            if (isSplitApk(info)) {
                // Split apk
                File tmpPublicSource = new File(backupPath, outputName + EXT_APKS);
                SplitApkExporter.saveApks(packageInfo, tmpPublicSource);
                return true;
            } else {
                // Regular apk
                File tmpPublicSource = new File(backupPath, outputName + EXT_APK);
                try (FileInputStream apkInputStream = new FileInputStream(info.publicSourceDir);
                     FileOutputStream apkOutputStream = new FileOutputStream(tmpPublicSource)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        FileUtils.copy(apkInputStream, apkOutputStream);
                    } else IOUtils.copy(apkInputStream, apkOutputStream);
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isSplitApk(ApplicationInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && info.splitNames != null;
    }
}
