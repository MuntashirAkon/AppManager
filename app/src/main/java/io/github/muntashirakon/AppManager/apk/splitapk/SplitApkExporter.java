// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.splitapk;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

/**
 * Used to generate app bundle with .apks extension. This file has all the apks as well as 3 other
 * file, such as icon.png, meta.sai_v1.json, meta.sai_v2.json.<br />
 * meta.sai_v1.json contains the following properties: export_timestamp (long), label (string),
 * package (string), version_code (long) and version_name (string).<br />
 * meta.sai_v2.json contains the following properties: export_timestamp (long), split_apk (boolean),
 * label (string), meta_version (long), min_sdk (long), package (string), target_sdk (long),
 * version_code (long), version_name (string), backup_components [ size (long), type (string) ]
 */
public final class SplitApkExporter {
    @WorkerThread
    public static void saveApks(@NonNull PackageInfo packageInfo, @NonNull Path apksFile) throws IOException {
        try (OutputStream outputStream = apksFile.openOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.setMethod(ZipOutputStream.DEFLATED);
            zipOutputStream.setLevel(Deflater.BEST_COMPRESSION);

            saveApkInternal(zipOutputStream, packageInfo);
        }
    }

    static void saveApkInternal(@NonNull ZipOutputStream zipOutputStream, @NonNull PackageInfo packageInfo) throws IOException {
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        List<Path> apkFiles = getAllApkFiles(applicationInfo);
        Collections.sort(apkFiles);

        // Metadata
        ApksMetadata apksMetadata = new ApksMetadata(packageInfo);
        apksMetadata.writeMetadata(zipOutputStream);
        
        // Add icon
        Bitmap bitmap = UIUtils.getBitmapFromDrawable(applicationInfo.loadIcon(ContextUtils.getContext().getPackageManager()));
        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngOutputStream);
        addBytes(zipOutputStream, pngOutputStream.toByteArray(), ApksMetadata.ICON_FILE, apksMetadata.exportTimestamp);

        // Add apk files
        for (Path apkFile : apkFiles) {
            addFile(zipOutputStream, apkFile, apkFile.getName(), apksMetadata.exportTimestamp);
        }

        // Add OBB files if possible
        Path obbDir = null;
        try {
            obbDir = ApkUtils.getObbDir(packageInfo.packageName, UserHandleHidden.getUserId(applicationInfo.uid));
        } catch (IOException ignore) {
        }
        if (obbDir != null) {
            Path[] obbFiles = obbDir.listFiles();
            for (Path obbFile : obbFiles) {
                addFile(zipOutputStream, obbFile, obbFile.getName(), apksMetadata.exportTimestamp);
            }
        }
    }

    static void addFile(@NonNull ZipOutputStream zipOutputStream, @NonNull Path filePath, @NonNull String name,
                               long timestamp) throws IOException {
        ZipEntry zipEntry = new ZipEntry(name);
        zipEntry.setMethod(ZipEntry.DEFLATED);
        zipEntry.setSize(filePath.length());
        zipEntry.setCrc(DigestUtils.calculateCrc32(filePath));
        zipEntry.setTime(timestamp);
        zipOutputStream.putNextEntry(zipEntry);
        try (InputStream apkInputStream = filePath.openInputStream()) {
            IoUtils.copy(apkInputStream, zipOutputStream);
        }
        zipOutputStream.closeEntry();
    }

    static void addBytes(@NonNull ZipOutputStream zipOutputStream, @NonNull byte[] bytes, @NonNull String name,
                               long timestamp) throws IOException {
        ZipEntry zipEntry = new ZipEntry(name);
        zipEntry.setMethod(ZipEntry.DEFLATED);
        zipEntry.setSize(bytes.length);
        zipEntry.setCrc(DigestUtils.calculateCrc32(bytes));
        zipEntry.setTime(timestamp);
        zipOutputStream.putNextEntry(zipEntry);
        zipOutputStream.write(bytes);
        zipOutputStream.closeEntry();
    }

    @NonNull
    private static List<Path> getAllApkFiles(@NonNull ApplicationInfo applicationInfo) {
        List<Path> apkFiles = new ArrayList<>();
        apkFiles.add(Paths.get(applicationInfo.publicSourceDir));
        if (applicationInfo.splitPublicSourceDirs != null) {
            // FIXME: 8/5/22 This does not work for disabled apps
            for (String splitPath : applicationInfo.splitPublicSourceDirs)
                apkFiles.add(Paths.get(splitPath));
        }
        return apkFiles;
    }
}
