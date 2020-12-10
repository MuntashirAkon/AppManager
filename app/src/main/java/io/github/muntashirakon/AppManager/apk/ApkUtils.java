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

import com.android.apksig.internal.apk.AndroidBinXmlParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.apk.splitapk.SplitApkExporter;
import io.github.muntashirakon.AppManager.backup.BackupFiles;
import io.github.muntashirakon.AppManager.servermanager.ApiSupporter;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.utils.IOUtils;

import static io.github.muntashirakon.AppManager.utils.IOUtils.copy;

public final class ApkUtils {
    public static final String EXT_APK = ".apk";
    public static final String EXT_APKS = ".apks";

    private static final String MANIFEST_FILE = "AndroidManifest.xml";

    @NonNull
    public static File getSharableApkFile(@NonNull PackageInfo packageInfo) throws Exception {
        ApplicationInfo info = packageInfo.applicationInfo;
        PackageManager pm = AppManager.getContext().getPackageManager();
        String outputName = info.loadLabel(pm).toString() + "_" + packageInfo.versionName;
        File tmpPublicSource;
        if (isSplitApk(info)) {
            // Split apk
            tmpPublicSource = new File(AppManager.getContext().getExternalCacheDir(), outputName + EXT_APKS);
            SplitApkExporter.saveApks(packageInfo, tmpPublicSource);
        } else {
            // Regular apk
            tmpPublicSource = new File(AppManager.getContext().getExternalCacheDir(), outputName + EXT_APK);
            try (FileInputStream apkInputStream = new FileInputStream(packageInfo.applicationInfo.publicSourceDir);
                 FileOutputStream apkOutputStream = new FileOutputStream(tmpPublicSource)) {
                copy(apkInputStream, apkOutputStream);
            }
        }
        return tmpPublicSource;
    }

    /**
     * Backup the given apk (both root and non root). This is similar to apk sharing feature except
     * that these are saved at /sdcard/AppManager/apks
     *
     * @return true on success, false on failure
     */
    public static boolean backupApk(String packageName, int userHandle) {
        File backupPath = BackupFiles.getApkBackupDirectory();
        if (!backupPath.exists()) {
            if (!backupPath.mkdirs()) return false;
        }
        // Fetch package info
        try {
            PackageManager pm = AppManager.getContext().getPackageManager();
            PackageInfo packageInfo = ApiSupporter.getInstance(LocalServer.getInstance())
                    .getPackageInfo(packageName, 0, userHandle);
            ApplicationInfo info = packageInfo.applicationInfo;
            String outputName = info.loadLabel(pm).toString() + "_" + packageInfo.versionName;
            File tmpPublicSource;
            if (isSplitApk(info)) {
                // Split apk
                tmpPublicSource = new File(backupPath, outputName + EXT_APKS);
                SplitApkExporter.saveApks(packageInfo, tmpPublicSource);
            } else {
                // Regular apk
                tmpPublicSource = new File(backupPath, outputName + EXT_APK);
                try (FileInputStream apkInputStream = new FileInputStream(info.publicSourceDir);
                     FileOutputStream apkOutputStream = new FileOutputStream(tmpPublicSource)) {
                    IOUtils.copy(apkInputStream, apkOutputStream);
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isSplitApk(@NonNull ApplicationInfo info) {
        return info.splitPublicSourceDirs != null && info.splitPublicSourceDirs.length > 0;
    }

    @NonNull
    public static ByteBuffer getManifestFromApk(File apkFile) throws IOException {
        try (FileInputStream apkInputStream = new FileInputStream(apkFile)) {
            return getManifestFromApk(apkInputStream);
        }
    }

    @NonNull
    public static ByteBuffer getManifestFromApk(InputStream apkInputStream) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(apkInputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (!IOUtils.getLastPathComponent(zipEntry.getName()).equals(MANIFEST_FILE)) {
                    zipInputStream.closeEntry();
                    continue;
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] buf = new byte[1024 * 4];
                int n;
                while (-1 != (n = zipInputStream.read(buf))) {
                    buffer.write(buf, 0, n);
                }
                zipInputStream.closeEntry();
                return ByteBuffer.wrap(buffer.toByteArray());
            }
        }
        throw new IOException("Error getting the manifest file.");
    }

    @NonNull
    public static HashMap<String, String> getManifestAttributes(@NonNull ByteBuffer manifestBytes)
            throws ApkFile.ApkFileException, AndroidBinXmlParser.XmlParserException {
        HashMap<String, String> manifestAttrs = new HashMap<>();
        AndroidBinXmlParser parser = new AndroidBinXmlParser(manifestBytes);
        int eventType = parser.getEventType();
        boolean seenManifestElement = false;
        while (eventType != AndroidBinXmlParser.EVENT_END_DOCUMENT) {
            if (eventType == AndroidBinXmlParser.EVENT_START_ELEMENT) {
                if (parser.getName().equals("manifest") && parser.getDepth() == 1 && parser.getNamespace().isEmpty()) {
                    if (seenManifestElement) {
                        throw new ApkFile.ApkFileException("Duplicate manifest found.");
                    }
                    seenManifestElement = true;
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if (parser.getAttributeName(i).isEmpty())
                            continue;
                        String namespace = "" + (parser.getAttributeNamespace(i).isEmpty() ? "" : (parser.getAttributeNamespace(i) + ":"));
                        manifestAttrs.put(namespace + parser.getAttributeName(i), parser.getAttributeStringValue(i));
                    }
                }
            }
            eventType = parser.next();
        }
        if (!seenManifestElement) throw new ApkFile.ApkFileException("No manifest found.");
        return manifestAttrs;
    }

    public static int getDensityFromName(@Nullable String densityName) {
        Integer density = StaticDataset.DENSITY_NAME_TO_DENSITY.get(densityName);
        if (density == null) {
            throw new IllegalArgumentException("Unknown density " + densityName);
        }
        return density;
    }
}
