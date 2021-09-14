// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.core.content.pm.PackageInfoCompat;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.backup.BackupFiles;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.SplitOutputStream;

import static io.github.muntashirakon.AppManager.backup.BackupManager.CERT_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.DATA_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.ICON_FILE;
import static io.github.muntashirakon.AppManager.backup.BackupManager.SOURCE_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.getExt;
import static io.github.muntashirakon.AppManager.utils.TarUtils.DEFAULT_SPLIT_SIZE;
import static io.github.muntashirakon.AppManager.utils.TarUtils.TAR_BZIP2;
import static io.github.muntashirakon.AppManager.utils.TarUtils.TAR_GZIP;

public class SBConvert extends Convert {
    public static final String TAG = SBConvert.class.getSimpleName();

    @NonNull
    private final Context context = AppManager.getContext();
    private final Path backupLocation;
    private final int userHandle;
    private final String packageName;
    private final long backupTime;
    private final PackageManager pm;

    private Crypto crypto;
    private BackupFiles.Checksum checksum;
    private MetadataManager.Metadata sourceMetadata;
    private MetadataManager.Metadata destMetadata;
    private Path tmpBackupPath;
    private PackageInfo packageInfo;
    private Path cachedApk;

    public SBConvert(@NonNull Path xmlFile) {
        backupLocation = xmlFile.getParentFile();
        packageName = FileUtils.trimExtension(xmlFile.getName());
        backupTime = xmlFile.lastModified();
        userHandle = UserHandleHidden.myUserId();
        pm = AppManager.getContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return packageName;
    }

    @Override
    public void convert() throws BackupException {
        // Source metadata
        sourceMetadata = new MetadataManager.Metadata();
        generateMetadata();
        // Destination metadata
        destMetadata = new MetadataManager.Metadata(sourceMetadata);
        MetadataManager metadataManager = MetadataManager.getNewInstance();
        metadataManager.setMetadata(destMetadata);
        // Simulate a backup creation
        // If the package has another backup named SB, another backup will be created
        BackupFiles backupFiles;
        BackupFiles.BackupFile[] backupFileList;
        try {
            backupFiles = new BackupFiles(packageName, userHandle, new String[]{"SB"});
            backupFileList = backupFiles.getBackupPaths(true);
        } catch (IOException e) {
            throw new BackupException("Could not get backup files.", e);
        }
        for (BackupFiles.BackupFile backupFile : backupFileList) {
            // We're iterating over a singleton list
            try {
                tmpBackupPath = backupFile.getBackupPath();
                crypto = ConvertUtils.setupCrypto(destMetadata);
                checksum = new BackupFiles.Checksum(backupFile.getChecksumFile(CryptoUtils.MODE_NO_ENCRYPTION), "w");
                // Backup icon
                backupIcon();
                if (destMetadata.flags.backupApkFiles()) {
                    backupApkFile();
                }
                if (destMetadata.flags.backupData()) {
                    backupData();
                }
                // Write modified metadata
                metadataManager.setMetadata(destMetadata);
                try {
                    metadataManager.writeMetadata(backupFile);
                } catch (IOException e) {
                    throw new BackupException("Failed to write metadata.");
                }
                // Store checksum for metadata
                checksum.add(MetadataManager.META_FILE, DigestUtils.getHexDigest(destMetadata.checksumAlgo, backupFile.getMetadataFile()));
                checksum.close();
                // Encrypt checksum
                Path checksumFile = backupFile.getChecksumFile(CryptoUtils.MODE_NO_ENCRYPTION);
                if (!crypto.encrypt(new Path[]{checksumFile})) {
                    throw new BackupException("Failed to encrypt " + checksumFile.getName());
                }
                // Replace current backup:
                // There's hardly any chance of getting a false here but checks are done anyway.
                if (backupFile.commit()) {
                    return;
                }
                Log.e(TAG, "Unknown error occurred. This message should never be printed.");
            } catch (Throwable th) {
                backupFile.cleanup();
                crypto.close();
                throw new BackupException(th.getClass().getName(), th);
            } finally {
                cachedApk.delete();
                // delete splits
                for (String splitName : sourceMetadata.splitConfigs) {
                    try {
                        FileUtils.getTempFile(splitName).delete();
                    } catch (IOException ignore) {
                    }
                }
            }
            return;
        }
    }

    private void backupApkFile() throws BackupException {
        Path sourceDir = Objects.requireNonNull(cachedApk.getParentFile());
        // Get certificate checksums
        try {
            String[] checksums = ConvertUtils.getChecksumsFromApk(cachedApk, destMetadata.checksumAlgo);
            for (int i = 0; i < checksums.length; ++i) {
                checksum.add(CERT_PREFIX + i, checksums[i]);
            }
        } catch (Exception ignore) {
        }
        // Backup APK files
        String[] apkFiles = ArrayUtils.appendElement(String.class, destMetadata.splitConfigs, destMetadata.apkName);
        String sourceBackupFilePrefix = SOURCE_PREFIX + getExt(destMetadata.tarType);
        Path[] sourceFiles;
        try {
            // We have to specify APK files because the folder may contain many
            sourceFiles = TarUtils.create(destMetadata.tarType, sourceDir, tmpBackupPath, sourceBackupFilePrefix,
                    apkFiles, null, null, false).toArray(new Path[0]);
        } catch (Throwable th) {
            throw new BackupException("APK files backup is requested but no APK files have been backed up.", th);
        }
        if (!crypto.encrypt(sourceFiles)) {
            throw new BackupException("Failed to encrypt " + Arrays.toString(sourceFiles));
        }
        // Overwrite with the new files
        sourceFiles = crypto.getNewFiles();
        for (Path file : sourceFiles) {
            checksum.add(file.getName(), DigestUtils.getHexDigest(destMetadata.checksumAlgo, file));
        }
    }

    private void backupData() throws BackupException {
        List<Path> dataFiles = new ArrayList<>(3);
        try {
            if (destMetadata.flags.backupInternalData()) {
                dataFiles.add(getIntDataFile());
            }
            if (destMetadata.flags.backupExternalData()) {
                dataFiles.add(getExtDataFile());
            }
            if (destMetadata.flags.backupMediaObb()) {
                dataFiles.add(getObbFile());
            }
        } catch (FileNotFoundException e) {
            throw new BackupException("Could not get data files", e);
        }
        int i = 0;
        for (Path dataFile : dataFiles) {
            String dataBackupFilePrefix = DATA_PREFIX + (i++) + getExt(destMetadata.tarType);
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(dataFile.openInputStream()));
                 SplitOutputStream sos = new SplitOutputStream(tmpBackupPath, dataBackupFilePrefix, DEFAULT_SPLIT_SIZE);
                 BufferedOutputStream bos = new BufferedOutputStream(sos)) {
                // TODO: 31/5/21 Check backup format (each zip file has a comment section which can be parsed as JSON)
                OutputStream os;
                if (TAR_GZIP.equals(destMetadata.tarType)) {
                    os = new GzipCompressorOutputStream(bos);
                } else if (TAR_BZIP2.equals(destMetadata.tarType)) {
                    os = new BZip2CompressorOutputStream(bos);
                } else {
                    throw new BackupException("Invalid compression type: " + destMetadata.tarType);
                }
                try (TarArchiveOutputStream tos = new TarArchiveOutputStream(os)) {
                    tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                    tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
                    ZipEntry zipEntry;
                    while ((zipEntry = zis.getNextEntry()) != null) {
                        File tmpFile = null;
                        if (!zipEntry.isDirectory()) {
                            // We need to use a temporary file
                            tmpFile = FileUtils.getTempFile();
                            try (OutputStream fos = new FileOutputStream(tmpFile)) {
                                FileUtils.copy(zis, fos);
                            } catch (Throwable th) {
                                tmpFile.delete();
                                throw th;
                            }
                        }
                        String fileName = zipEntry.getName().replaceFirst(packageName + "/", "");
                        if (fileName.equals("")) continue;
                        // New tar entry
                        TarArchiveEntry tarArchiveEntry = new TarArchiveEntry(fileName);
                        if (tmpFile != null) {
                            tarArchiveEntry.setSize(tmpFile.length());
                        }
                        tos.putArchiveEntry(tarArchiveEntry);
                        if (tmpFile != null) {
                            // Copy from the temporary file
                            try (FileInputStream fis = new FileInputStream(tmpFile)) {
                                FileUtils.copy(fis, tos);
                            } finally {
                                tmpFile.delete();
                            }
                        }
                        tos.closeArchiveEntry();
                    }
                    tos.finish();
                }
                // Encrypt backups
                Path[] newBackupFiles = sos.getFiles().toArray(new Path[0]);
                if (!crypto.encrypt(newBackupFiles)) {
                    throw new BackupException("Failed to encrypt " + Arrays.toString(newBackupFiles));
                }
                // Overwrite with the new files
                newBackupFiles = crypto.getNewFiles();
                for (Path file : newBackupFiles) {
                    checksum.add(file.getName(), DigestUtils.getHexDigest(destMetadata.checksumAlgo, file));
                }
            } catch (IOException e) {
                throw new BackupException("Backup failed for " + dataFile, e);
            }
        }
    }

    @SuppressLint("WrongConstant")
    private void generateMetadata() throws BackupException {
        try (InputStream pis = getApkFile().openInputStream()) {
            cachedApk = FileUtils.getTempPath(context, "base.apk");
            try (OutputStream fos = cachedApk.openOutputStream()) {
                FileUtils.copy(pis, fos);
            }
        } catch (IOException e) {
            throw new BackupException("Could not cache APK file", e);
        }
        String filePath = Objects.requireNonNull(cachedApk.getFilePath());
        packageInfo = pm.getPackageArchiveInfo(filePath, 0);
        if (packageInfo == null) {
            throw new BackupException("Could not fetch package info");
        }
        packageInfo.applicationInfo.publicSourceDir = filePath;
        packageInfo.applicationInfo.sourceDir = filePath;
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;

        if (!packageInfo.packageName.equals(packageName)) {
            throw new BackupException("Package name mismatch: Expected=" + packageName + ", Actual=" + packageInfo.packageName);
        }

        sourceMetadata.label = applicationInfo.loadLabel(pm).toString();
        sourceMetadata.packageName = packageName;
        sourceMetadata.versionName = packageInfo.versionName;
        sourceMetadata.versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
        sourceMetadata.isSystem = false;
        sourceMetadata.hasRules = false;
        sourceMetadata.backupTime = backupTime;
        sourceMetadata.crypto = CryptoUtils.getMode();
        sourceMetadata.apkName = "base.apk";
        // Backup flags
        BackupFlags flags = new BackupFlags(BackupFlags.BACKUP_APK_FILES);
        try {
            getObbFile();
            flags.addFlag(BackupFlags.BACKUP_EXT_OBB_MEDIA);
        } catch (FileNotFoundException ignore) {
        }
        try {
            getIntDataFile();
            flags.addFlag(BackupFlags.BACKUP_INT_DATA);
            flags.addFlag(BackupFlags.BACKUP_CACHE);
        } catch (FileNotFoundException ignore) {
        }
        try {
            getExtDataFile();
            flags.addFlag(BackupFlags.BACKUP_EXT_DATA);
            flags.addFlag(BackupFlags.BACKUP_CACHE);
        } catch (FileNotFoundException ignore) {
        }
        sourceMetadata.flags = flags;
        sourceMetadata.dataDirs = ConvertUtils.getDataDirs(this.packageName, this.userHandle,
                flags.backupInternalData(), flags.backupExternalData(), flags.backupMediaObb());
        try {
            getSplitFile();
            sourceMetadata.isSplitApk = true;
        } catch (FileNotFoundException e) {
            sourceMetadata.isSplitApk = false;
        }
        try {
            sourceMetadata.splitConfigs = cacheAndGetSplitConfigs();
        } catch (IOException | RemoteException e) {
            throw new BackupException("Could not cache splits", e);
        }
        sourceMetadata.userHandle = userHandle;
        sourceMetadata.tarType = ConvertUtils.getTarTypeFromPref();
        sourceMetadata.keyStore = false;
        sourceMetadata.installer = AppPref.getString(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR);
    }

    @NonNull
    private Path getApkFile() throws FileNotFoundException {
        return backupLocation.findFile(packageName + ".app");
    }

    @NonNull
    private Path getSplitFile() throws FileNotFoundException {
        return backupLocation.findFile(packageName + ".splits");
    }

    @NonNull
    private Path getObbFile() throws FileNotFoundException {
        return backupLocation.findFile(packageName + ".exp");
    }

    @NonNull
    private Path getIntDataFile() throws FileNotFoundException {
        return backupLocation.findFile(packageName + ".dat");
    }

    @NonNull
    private Path getExtDataFile() throws FileNotFoundException {
        return backupLocation.findFile(packageName + ".extdat");
    }

    private String[] cacheAndGetSplitConfigs() throws IOException, RemoteException {
        List<String> splits = new ArrayList<>();
        Path splitFile;
        try {
            splitFile = getSplitFile();
        } catch (FileNotFoundException e) {
            return ArrayUtils.emptyArray(String.class);
        }
        try (BufferedInputStream bis = new BufferedInputStream(splitFile.openInputStream());
             ZipInputStream zis = new ZipInputStream(bis)) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) continue;
                String splitName = new File(zipEntry.getName()).getName();
                splits.add(splitName);
                File file = FileUtils.getTempFile(splitName);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    FileUtils.copy(zis, fos);
                } catch (IOException e) {
                    file.delete();
                    throw e;
                }
            }
        }
        return splits.toArray(new String[0]);
    }

    private void backupIcon() {
        try {
            Path iconFile = tmpBackupPath.findFile(ICON_FILE);
            try (OutputStream outputStream = iconFile.openOutputStream()) {
                Bitmap bitmap = FileUtils.getBitmapFromDrawable(packageInfo.applicationInfo.loadIcon(pm));
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                outputStream.flush();
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not back up icon.");
        }
    }
}
