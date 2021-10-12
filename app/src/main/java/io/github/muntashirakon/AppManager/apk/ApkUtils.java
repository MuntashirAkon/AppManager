// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlParser;
import io.github.muntashirakon.AppManager.apk.splitapk.SplitApkExporter;
import io.github.muntashirakon.AppManager.backup.BackupFiles;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagMatchUninstalled;

public final class ApkUtils {
    public static final String EXT_APK = ".apk";
    public static final String EXT_APKS = ".apks";

    private static final String MANIFEST_FILE = "AndroidManifest.xml";

    @WorkerThread
    @NonNull
    public static Path getSharableApkFile(@NonNull PackageInfo packageInfo) throws Exception {
        ApplicationInfo info = packageInfo.applicationInfo;
        Context ctx = AppManager.getContext();
        PackageManager pm = ctx.getPackageManager();
        String outputName = FileUtils.getSanitizedFileName(info.loadLabel(pm).toString() + "_" +
                packageInfo.versionName, false);
        if (outputName == null) outputName = info.packageName;
        Path tmpPublicSource;
        if (isSplitApk(info)) {
            // Split apk
            tmpPublicSource = new Path(ctx, new File(AppManager.getContext().getExternalCacheDir(), outputName + EXT_APKS));
            SplitApkExporter.saveApks(packageInfo, tmpPublicSource);
        } else {
            // Regular apk
            tmpPublicSource = new Path(ctx, new File(packageInfo.applicationInfo.publicSourceDir));
        }
        return tmpPublicSource;
    }

    /**
     * Backup the given apk (both root and non root). This is similar to apk sharing feature except
     * that these are saved at /sdcard/AppManager/apks
     *
     * @return true on success, false on failure
     */
    @WorkerThread
    public static boolean backupApk(String packageName, int userHandle) {
        Path backupPath;
        try {
            backupPath = BackupFiles.getApkBackupDirectory();
        } catch (IOException e) {
            return false;
        }
        // Fetch package info
        Context ctx = AppManager.getContext();
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo packageInfo = PackageManagerCompat.getPackageInfo(packageName, flagMatchUninstalled, userHandle);
            ApplicationInfo info = packageInfo.applicationInfo;
            String outputName = FileUtils.getSanitizedFileName(info.loadLabel(pm).toString() + "_" +
                    packageInfo.versionName, false);
            if (outputName == null) outputName = packageName;
            Path apkFile;
            if (isSplitApk(info)) {
                // Split apk
                apkFile = backupPath.createNewFile(outputName + EXT_APKS, null);
                SplitApkExporter.saveApks(packageInfo, apkFile);
            } else {
                // Regular apk
                apkFile = backupPath.createNewFile(outputName + EXT_APK, null);
                FileUtils.copy(new Path(ctx, new File(info.publicSourceDir)), apkFile);
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
        try (ZipFile zipFile = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> archiveEntries = zipFile.entries();
            ZipEntry zipEntry;
            while (archiveEntries.hasMoreElements()) {
                zipEntry = archiveEntries.nextElement();
                if (!zipEntry.getName().equals(MANIFEST_FILE)) {
                    continue;
                }
                try (InputStream zipInputStream = zipFile.getInputStream(zipEntry)) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] buf = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
                    int n;
                    while (-1 != (n = zipInputStream.read(buf))) {
                        buffer.write(buf, 0, n);
                    }
                    return ByteBuffer.wrap(buffer.toByteArray());
                }
            }
        }
        throw new IOException("Error getting the manifest file.");
    }

    @NonNull
    public static ByteBuffer getManifestFromApk(InputStream apkInputStream) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(apkInputStream))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (!FileUtils.getLastPathComponent(zipEntry.getName()).equals(MANIFEST_FILE)) {
                    continue;
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] buf = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
                int n;
                while (-1 != (n = zipInputStream.read(buf))) {
                    buffer.write(buf, 0, n);
                }
                return ByteBuffer.wrap(buffer.toByteArray());
            }
        }
        // This could be due to a Zip error, try caching the APK
        File cachedApk = FileUtils.getCachedFile(apkInputStream);
        ByteBuffer byteBuffer;
        try {
            byteBuffer = getManifestFromApk(cachedApk);
        } finally {
            FileUtils.deleteSilently(cachedApk);
        }
        return byteBuffer;
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
