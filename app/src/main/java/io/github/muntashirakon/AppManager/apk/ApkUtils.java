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
}
