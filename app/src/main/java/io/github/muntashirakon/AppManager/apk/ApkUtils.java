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
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PackageInfoCompat;

import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.internal.zip.CentralDirectoryRecord;
import com.android.apksig.internal.zip.LocalFileRecord;
import com.android.apksig.internal.zip.ZipUtils;
import com.android.apksig.util.DataSource;
import com.android.apksig.util.DataSources;
import com.android.apksig.zip.ZipFormatException;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.io.BlockReader;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
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
    public static final String TAG = ApkUtils.class.getSimpleName();

    public static final String EXT_APK = ".apk";
    public static final String EXT_APKS = ".apks";

    private static final Object sLock = new Object();
    private static final String MANIFEST_FILE = "AndroidManifest.xml";

    @WorkerThread
    @NonNull
    public static Path getSharableApkFile(@NonNull Context ctx, @NonNull PackageInfo packageInfo) throws IOException {
        synchronized (sLock) {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = packageInfo.applicationInfo;
            String outputName = Paths.sanitizeFilename(getFormattedApkFilename(ctx, packageInfo, pm), "_",
                    Paths.SANITIZE_FLAG_FAT_ILLEGAL_CHARS | Paths.SANITIZE_FLAG_UNIX_RESERVED);
            if (outputName == null) outputName = info.packageName;
            Path tmpPublicSource;
            if (isSplitApk(info) || hasObbFiles(info.packageName, UserHandleHidden.getUserId(info.uid))) {
                // Split apk
                tmpPublicSource = Paths.get(new File(FileUtils.getExternalCachePath(ContextUtils.getContext()), outputName + EXT_APKS));
                SplitApkExporter.saveApks(packageInfo, tmpPublicSource);
            } else {
                // Regular apk
                tmpPublicSource = Paths.get(new File(FileUtils.getExternalCachePath(ContextUtils.getContext()), outputName + EXT_APK));
                IoUtils.copy(Paths.get(info.publicSourceDir), tmpPublicSource);
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
    public static ByteBuffer getManifestFromApk(File apkFile) throws ApkFile.ApkFileException {
        try (RandomAccessFile in = new RandomAccessFile(apkFile, "r")) {
            DataSource apk = DataSources.asDataSource(in);
            com.android.apksig.apk.ApkUtils.ZipSections apkSections;
            try {
                apkSections = com.android.apksig.apk.ApkUtils.findZipSections(apk);
            } catch (ZipFormatException e) {
                throw new ApkFile.ApkFileException("Malformed APK: not a ZIP archive", e);
            }
            List<CentralDirectoryRecord> cdRecords;
            try {
                cdRecords = ZipUtils.parseZipCentralDirectory(apk, apkSections);
            } catch (ApkFormatException e) {
                throw new ApkFile.ApkFileException(e.getMessage(), e);
            }
            try {
                return getAndroidManifestFromApk(
                        cdRecords,
                        apk.slice(0, apkSections.getZipCentralDirectoryOffset()));
            } catch (ApkFormatException e) {
                throw new ApkFile.ApkFileException(e.getMessage(), e);
            } catch (ZipFormatException e) {
                throw new ApkFile.ApkFileException("Failed to read " + MANIFEST_FILE, e);
            }
        } catch (IOException e) {
            throw new ApkFile.ApkFileException(e.getMessage(), e);
        }
    }

    @NonNull
    public static ByteBuffer getManifestFromApk(InputStream apkInputStream) throws ApkFile.ApkFileException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(apkInputStream))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (!zipEntry.getName().equals(MANIFEST_FILE)) {
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
        } catch (IOException e) {
            Log.w(TAG, "Could not fetch AndroidManifest.xml from APK stream, trying an alternative...", e);
        }
        // This could be due to a Zip error, try caching the APK
        File cachedApk;
        try {
            cachedApk = FileCache.getGlobalFileCache().getCachedFile(apkInputStream, "apk");
        } catch (IOException e) {
            throw new ApkFile.ApkFileException("Could not cache the APK file", e);
        }
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
            throws ApkFile.ApkFileException {
        try (BlockReader reader = new BlockReader(manifestBytes.array())) {
            HashMap<String, String> manifestAttrs = new HashMap<>();
            ResXmlDocument xmlBlock = new ResXmlDocument();
            try {
                xmlBlock.readBytes(reader);
            } catch (IOException e) {
                throw new ApkFile.ApkFileException(e);
            }
            xmlBlock.setPackageBlock(AndroidBinXmlDecoder.getFrameworkPackageBlock());
            ResXmlElement resManifestElement = xmlBlock.getDocumentElement();
            // manifest
            if (!"manifest".equals(resManifestElement.getName())) {
                throw new ApkFile.ApkFileException("No manifest found.");
            }
            Iterator<ResXmlAttribute> attrIt = resManifestElement.getAttributes();
            ResXmlAttribute attr;
            String attrName;
            while (attrIt.hasNext()) {
                attr = attrIt.next();
                attrName = attr.getName();
                if (TextUtils.isEmpty(attrName)) {
                    continue;
                }
                manifestAttrs.put(attrName, attr.getValueAsString());
            }
            // application
            ResXmlElement resApplicationElement = null;
            Iterator<ResXmlElement> resXmlElementIt = resManifestElement.getElements("application");
            if (resXmlElementIt.hasNext()) {
                resApplicationElement = resXmlElementIt.next();
            }
            if (resXmlElementIt.hasNext()) {
                throw new ApkFile.ApkFileException("\"manifest\" has duplicate \"application\" tags.");
            }
            if (resApplicationElement == null) {
                Log.w(TAG, "No application tag found while parsing APK.");
                return manifestAttrs;
            }
            attrIt = resApplicationElement.getAttributes();
            while (attrIt.hasNext()) {
                attr = attrIt.next();
                attrName = attr.getName();
                if (TextUtils.isEmpty(attrName)) {
                    continue;
                }
                if (manifestAttrs.containsKey(attrName)) {
                    Log.w(TAG, "Ignoring invalid attribute in the application tag: " + attrName);
                    continue;
                }
                manifestAttrs.put(attrName, attr.getValueAsString());
            }
            return manifestAttrs;
        }
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

    @NonNull
    private static ByteBuffer getAndroidManifestFromApk(
            @NonNull List<CentralDirectoryRecord> cdRecords, @NonNull DataSource lhfSection)
            throws IOException, ApkFormatException, ZipFormatException {
        CentralDirectoryRecord androidManifestCdRecord = findCdRecord(cdRecords, MANIFEST_FILE);
        if (androidManifestCdRecord == null) {
            throw new ApkFormatException("Missing " + MANIFEST_FILE);
        }
        return ByteBuffer.wrap(LocalFileRecord.getUncompressedData(
                lhfSection, androidManifestCdRecord, lhfSection.size()));
    }

    @Nullable
    private static CentralDirectoryRecord findCdRecord(
            @NonNull List<CentralDirectoryRecord> cdRecords, @NonNull String name) {
        for (CentralDirectoryRecord cdRecord : cdRecords) {
            if (name.equals(cdRecord.getName())) {
                return cdRecord;
            }
        }
        return null;
    }
}
