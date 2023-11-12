// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PackageInfoCompat;

import com.reandroid.xml.XMLAttribute;
import com.reandroid.xml.XMLDocument;
import com.reandroid.xml.XMLElement;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlDecoder;
import io.github.muntashirakon.AppManager.apk.splitapk.SplitApkExporter;
import io.github.muntashirakon.AppManager.backup.BackupFiles;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public final class ApkUtils {
    public static final String EXT_APK = ".apk";
    public static final String EXT_APKS = ".apks";

    private static final Object sLock = new Object();
    private static final String MANIFEST_FILE = "AndroidManifest.xml";

    @WorkerThread
    @NonNull
    public static Path getSharableApkFile(@NonNull Context ctx, @NonNull PackageInfo packageInfo) throws IOException {
        synchronized (sLock) {
            ApplicationInfo info = packageInfo.applicationInfo;
            PackageManager pm = ctx.getPackageManager();
            String outputName = Paths.sanitizeFilename(info.loadLabel(pm).toString() + "_" +
                    packageInfo.versionName, "_");
            if (outputName == null) outputName = info.packageName;
            Path tmpPublicSource;
            if (isSplitApk(info) || hasObbFiles(info.packageName, UserHandleHidden.getUserId(info.uid))) {
                // Split apk
                tmpPublicSource = Paths.get(new File(FileUtils.getExternalCachePath(ContextUtils.getContext()), outputName + EXT_APKS));
                SplitApkExporter.saveApks(packageInfo, tmpPublicSource);
            } else {
                // Regular apk
                tmpPublicSource = Paths.get(packageInfo.applicationInfo.publicSourceDir);
            }
            return tmpPublicSource;
        }
    }

    /**
     * Backup the given apk (both root and no-root). This is similar to apk sharing feature except
     * that these are saved at /sdcard/AppManager/apks
     */
    @WorkerThread
    public static void backupApk(@NonNull Context ctx, @NonNull String packageName, @UserIdInt int userId)
            throws IOException, PackageManager.NameNotFoundException, RemoteException {
        Path backupPath = BackupFiles.getApkBackupDirectory();
        // Fetch package info
        PackageManager pm = ctx.getPackageManager();
        PackageInfo packageInfo = PackageManagerCompat.getPackageInfo(packageName,
                MATCH_UNINSTALLED_PACKAGES | PackageManager.GET_SHARED_LIBRARY_FILES
                        | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
        ApplicationInfo info = packageInfo.applicationInfo;
        String outputName = Paths.sanitizeFilename(getFormattedApkFilename(ctx, packageInfo, pm), "_",
                Paths.SANITIZE_FLAG_FAT_ILLEGAL_CHARS | Paths.SANITIZE_FLAG_UNIX_RESERVED);
        if (outputName == null) outputName = packageName;
        Path apkFile;
        if (isSplitApk(info) || hasObbFiles(packageName, userId)) {
            // Split apk
            apkFile = backupPath.createNewFile(outputName + EXT_APKS, null);
            SplitApkExporter.saveApks(packageInfo, apkFile);
        } else {
            // Regular apk
            apkFile = backupPath.createNewFile(outputName + EXT_APK, null);
            IoUtils.copy(Paths.get(info.publicSourceDir), apkFile);
        }
    }

    @NonNull
    private static String getFormattedApkFilename(@NonNull Context context, @NonNull PackageInfo packageInfo,
                                                  @NonNull PackageManager pm) {
        // TODO: 15/3/22 Optimize this
        String apkName = AppPref.getString(AppPref.PrefKey.PREF_SAVED_APK_FORMAT_STR)
                .replaceAll("%label%", packageInfo.applicationInfo.loadLabel(pm).toString())
                .replaceAll("%package_name%", packageInfo.packageName)
                .replaceAll("%version%", packageInfo.versionName)
                .replaceAll("%version_code%", String.valueOf(PackageInfoCompat.getLongVersionCode(packageInfo)))
                .replaceAll("%target_sdk%", String.valueOf(packageInfo.applicationInfo.targetSdkVersion))
                .replaceAll("%datetime%", DateUtils.formatDateTime(context, System.currentTimeMillis()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return apkName.replaceAll("%min_sdk%", String.valueOf(packageInfo.applicationInfo.minSdkVersion));
        }
        return apkName;
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
                if (!Paths.getLastPathSegment(zipEntry.getName()).equals(MANIFEST_FILE)) {
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
        File cachedApk = FileCache.getGlobalFileCache().getCachedFile(apkInputStream, "apk");
        ByteBuffer byteBuffer;
        try {
            byteBuffer = getManifestFromApk(cachedApk);
        } finally {
            FileCache.getGlobalFileCache().delete(cachedApk);
        }
        return byteBuffer;
    }

    @NonNull
    public static HashMap<String, String> getManifestAttributes(@NonNull ByteBuffer manifestBytes)
            throws ApkFile.ApkFileException, IOException {
        HashMap<String, String> manifestAttrs = new HashMap<>();
        XMLDocument xmlDocument = AndroidBinXmlDecoder.decodeToXml(manifestBytes);
        XMLElement manifestElement = xmlDocument.getDocumentElement();
        if (!"manifest".equals(manifestElement.getName())) {
            throw new ApkFile.ApkFileException("No manifest found.");
        }
        for (XMLAttribute attribute : manifestElement.listAttributes()) {
            if (attribute.getName().isEmpty()) {
                continue;
            }
            manifestAttrs.put(attribute.getName(), attribute.getValue());
        }
        XMLElement androidElement = null;
        for (XMLElement elem : manifestElement.getChildElementList()) {
            if ("application".equals(elem.getName())) {
                androidElement = elem;
                break;
            }
        }
        if (androidElement == null) {
            Log.w("ApkUtils", "No application element found while parsing APK.");
            return manifestAttrs;
        }
        for (XMLAttribute attribute : androidElement.listAttributes()) {
            if (attribute.getName().isEmpty()) {
                continue;
            }
            manifestAttrs.put(attribute.getName(), attribute.getValue());
        }
        return manifestAttrs;
    }

    public static boolean hasObbFiles(@NonNull String packageName, @UserIdInt int userId) {
        try {
            return getObbDir(packageName, userId).listFiles().length > 0;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    @NonNull
    public static Path getObbDir(@NonNull String packageName, @UserIdInt int userId) throws FileNotFoundException {
        // Get writable OBB directory
        Path obbDir = getWritableExternalDirectory(userId)
                .findFile("Android")
                .findFile("obb")
                .findFile(packageName);
        return Paths.get(obbDir.getUri());
    }

    @NonNull
    public static Path getOrCreateObbDir(@NonNull String packageName, @UserIdInt int userId) throws IOException {
        // Get writable OBB directory
        Path obbDir = getWritableExternalDirectory(userId)
                .findOrCreateDirectory("Android")
                .findOrCreateDirectory("obb")
                .findOrCreateDirectory(packageName);
        return Paths.get(obbDir.getUri());
    }

    @NonNull
    public static Path getWritableExternalDirectory(@UserIdInt int userId) throws FileNotFoundException {
        // Get the first writable external storage directory
        OsEnvironment.UserEnvironment userEnvironment = OsEnvironment.getUserEnvironment(userId);
        Path[] extDirs = userEnvironment.getExternalDirs();
        Path writableExtDir = null;
        for (Path extDir : extDirs) {
            if (extDir.canWrite() || Objects.requireNonNull(extDir.getFilePath()).startsWith("/storage/emulated")) {
                writableExtDir = extDir;
                break;
            }
        }
        if (writableExtDir == null) {
            throw new FileNotFoundException("Couldn't find any writable Obb dir");
        }
        return writableExtDir;
    }

    public static int getDensityFromName(@Nullable String densityName) {
        Integer density = StaticDataset.DENSITY_NAME_TO_DENSITY.get(densityName);
        if (density == null) {
            throw new IllegalArgumentException("Unknown density " + densityName);
        }
        return density;
    }
}
