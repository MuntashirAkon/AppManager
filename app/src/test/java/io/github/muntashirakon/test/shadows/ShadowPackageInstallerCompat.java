// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.test.shadows;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import io.github.muntashirakon.AppManager.apk.installer.InstallerOptions;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@Implements(PackageInstallerCompat.class)
public class ShadowPackageInstallerCompat {
    @Implementation
    public boolean install(@NonNull Path[] apkFiles, @NonNull String packageName, @NonNull InstallerOptions options,
                           @Nullable ProgressHandler progressHandler) {
        PackageManager packageManager = RuntimeEnvironment.getApplication().getPackageManager();
        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(packageManager);
        String apkPath = apkFiles[0].getFilePath();
        if (apkPath != null) {
            PackageInfo packageInfo = packageManager.getPackageArchiveInfo(apkFiles[0].getFilePath(), 0);
            if (packageInfo != null) {
                packageInfo.packageName = packageName;
                ApplicationInfo applicationInfo = Objects.requireNonNull(packageInfo.applicationInfo);
                File roboDir = RoboUtils.getTestBaseDir();
                File apkDir = new File(roboDir.getAbsoluteFile() + "/data/app/" + packageName);
                if (!apkDir.exists() && !apkDir.mkdirs()) {
                    return false;
                }
                String[] filenames = new String[apkFiles.length];
                int i = 0;
                for (Path apkFile : apkFiles) {
                    String filename = apkFile.getName();
                    filenames[i] = filename;
                    File dupApkFile = new File(apkDir, filename);
                    try {
                        FileUtils.copy(apkFile, Paths.get(dupApkFile), null);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return false;
                    }
                    ++i;
                }
                if (filenames.length > 1) {
                    // Split APK
                    applicationInfo.splitPublicSourceDirs = new String[filenames.length - 1];
                    for (i = 1; i < filenames.length; ++i) {
                        applicationInfo.splitPublicSourceDirs[i - 1] = new File(apkDir, filenames[i]).getAbsolutePath();
                    }
                    applicationInfo.splitSourceDirs = applicationInfo.splitPublicSourceDirs;
                }
                applicationInfo.publicSourceDir = new File(apkDir, filenames[0]).getAbsolutePath();
                applicationInfo.sourceDir = applicationInfo.publicSourceDir;
                applicationInfo.deviceProtectedDataDir = "/data/user_de/0/" + packageName;
                applicationInfo.dataDir = "/data/data/" + packageName;
                shadowPackageManager.installPackage(packageInfo);
                return true;
            }
        }
        return false;
    }
}
