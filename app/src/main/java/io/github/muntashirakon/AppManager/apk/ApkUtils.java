package io.github.muntashirakon.AppManager.apk;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
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
    @NonNull
    public static File getSharableApkFile(@NonNull PackageInfo packageInfo) throws Exception {
        ApplicationInfo info = packageInfo.applicationInfo;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && info.splitNames != null) {
            // Split apk
            File tmpPublicSource = File.createTempFile(info.packageName, ".apks", AppManager.getContext().getExternalCacheDir());
            SplitApkExporter.saveApks(packageInfo, tmpPublicSource);
            return tmpPublicSource;
        } else {
            // Regular apk
            File tmpPublicSource = File.createTempFile(info.packageName, ".apk", AppManager.getContext().getExternalCacheDir());
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
            PackageInfo packageInfo = AppManager.getContext().getPackageManager().getPackageInfo(packageName, 0);
            ApplicationInfo info = packageInfo.applicationInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && info.splitNames != null) {
                // Split apk
                File tmpPublicSource = new File(backupPath, info.packageName + ".apks");
                SplitApkExporter.saveApks(packageInfo, tmpPublicSource);
                return true;
            } else {
                // Regular apk
                File tmpPublicSource = new File(backupPath, info.packageName + ".apk");
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
}
