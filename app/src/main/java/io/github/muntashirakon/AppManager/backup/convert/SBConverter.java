// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import static io.github.muntashirakon.AppManager.backup.BackupManager.CERT_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.getExt;
import static io.github.muntashirakon.AppManager.utils.TarUtils.DEFAULT_SPLIT_SIZE;

import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
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
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.backup.BackupItems;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.SplitOutputStream;

public class SBConverter extends Converter {
    public static final String TAG = SBConverter.class.getSimpleName();

    private final Path mBackupLocation;
    @UserIdInt
    private final int mUserId;
    private final String mPackageName;
    private final long mBackupTime;
    private final PackageManager mPm;
    private final List<Path> mFilesToBeDeleted = new ArrayList<>();

    private BackupItems.Checksum mChecksum;
    private BackupMetadataV5 mDestMetadata;
    private BackupItems.BackupItem mBackupItem;
    private PackageInfo mPackageInfo;
    private Path mCachedApk;

    public SBConverter(@NonNull Path xmlFile) {
        mBackupLocation = xmlFile.getParent();
        mPackageName = Paths.trimPathExtension(xmlFile.getName());
        mBackupTime = xmlFile.lastModified();
        mUserId = UserHandleHidden.myUserId();
        mPm = ContextUtils.getContext().getPackageManager();
        mFilesToBeDeleted.add(xmlFile);
    }

    @Override
    public String getPackageName() {
        return mPackageName;
    }

    @Override
    public void convert() throws BackupException {
        // Source metadata
        BackupMetadataV2 sourceMetadata = generateMetadata();
        // Simulate a backup creation
        try {
            mBackupItem = BackupItems.createBackupItemGracefully(mUserId, "SB", mPackageName);
        } catch (IOException e) {
            throw new BackupException("Could not get backup files.", e);
        }
        boolean backupSuccess = false;
        try {
            try {
                // Destination metadata
                mDestMetadata = ConvertUtils.getV5Metadata(sourceMetadata, mBackupItem);
            } catch (CryptoException e) {
                throw new BackupException("Failed to get crypto " + mDestMetadata.info.crypto, e);
            }
            try {
                mChecksum = mBackupItem.getChecksum();
            } catch (IOException e) {
                throw new BackupException("Failed to create checksum file.", e);
            }
            // Backup icon
            backupIcon();
            if (mDestMetadata.info.flags.backupApkFiles()) {
                backupApkFile();
            }
            if (mDestMetadata.info.flags.backupData()) {
                backupData();
            }
            // Write modified metadata
            try {
                Map<String, String> filenameChecksumMap = MetadataManager.writeMetadata(mDestMetadata, mBackupItem);
                for (Map.Entry<String, String> filenameChecksumPair : filenameChecksumMap.entrySet()) {
                    mChecksum.add(filenameChecksumPair.getKey(), filenameChecksumPair.getValue());
                }
            } catch (IOException e) {
                throw new BackupException("Failed to write metadata.");
            }
            mChecksum.close();
            // Encrypt checksum
            try {
                mBackupItem.encrypt(new Path[]{mChecksum.getFile()});
            } catch (IOException e) {
                throw new BackupException("Failed to encrypt checksums.txt");
            }
            // Replace current backup
            try {
                mBackupItem.commit();
            } catch (IOException e) {
                throw new BackupException("Could not finalise backup.", e);
            }
            backupSuccess = true;
        } catch (BackupException e) {
            throw e;
        } catch (Throwable th) {
            throw new BackupException("Unknown error occurred.", th);
        } finally {
            mBackupItem.cleanup();
            mCachedApk.requireParent().delete();
            if (backupSuccess) {
                BackupUtils.putBackupToDbAndBroadcast(ContextUtils.getContext(), mDestMetadata);
            }
        }
    }

    @Override
    public void cleanup() {
        for (Path file : mFilesToBeDeleted) {
            file.delete();
        }
    }

    private void backupApkFile() throws BackupException {
        Path sourceDir = mCachedApk.requireParent();
        // Get certificate checksums
        try {
            String[] checksums = ConvertUtils.getChecksumsFromApk(mCachedApk, mDestMetadata.info.checksumAlgo);
            for (int i = 0; i < checksums.length; ++i) {
                mChecksum.add(CERT_PREFIX + i, checksums[i]);
            }
        } catch (Exception ignore) {
        }
        // Backup APK files
        String[] apkFiles = ArrayUtils.appendElement(String.class, mDestMetadata.metadata.splitConfigs, mDestMetadata.metadata.apkName);
        String sourceBackupFilePrefix = BackupUtils.getSourceFilePrefix(getExt(mDestMetadata.info.tarType));
        Path[] sourceFiles;
        try {
            // We have to specify APK files because the folder may contain many
            sourceFiles = TarUtils.create(mDestMetadata.info.tarType, sourceDir, mBackupItem.getUnencryptedBackupPath(), sourceBackupFilePrefix,
                    apkFiles, null, null, false).toArray(new Path[0]);
        } catch (Throwable th) {
            throw new BackupException("APK files backup is requested but no APK files have been backed up.", th);
        }
        try {
            sourceFiles = mBackupItem.encrypt(sourceFiles);
        } catch (IOException e) {
            throw new BackupException("Failed to encrypt " + Arrays.toString(sourceFiles));
        }
        for (Path file : sourceFiles) {
            mChecksum.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata.info.checksumAlgo, file));
        }
    }

    private void backupData() throws BackupException {
        List<Path> dataFiles = new ArrayList<>(3);
        try {
            if (mDestMetadata.info.flags.backupInternalData()) {
                dataFiles.add(getIntDataFile());
            }
            if (mDestMetadata.info.flags.backupExternalData()) {
                dataFiles.add(getExtDataFile());
            }
            if (mDestMetadata.info.flags.backupMediaObb()) {
                dataFiles.add(getObbFile());
            }
        } catch (FileNotFoundException e) {
            throw new BackupException("Could not get data files", e);
        }
        String tarType = mDestMetadata.info.tarType;
        int i = 0;
        for (Path dataFile : dataFiles) {
            String dataBackupFilePrefix = BackupUtils.getDataFilePrefix(i++, getExt(tarType));
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(dataFile.openInputStream()));
                 SplitOutputStream sos = new SplitOutputStream(mBackupItem.getUnencryptedBackupPath(), dataBackupFilePrefix, DEFAULT_SPLIT_SIZE);
                 BufferedOutputStream bos = new BufferedOutputStream(sos);
                 OutputStream os = TarUtils.createCompressedStream(bos, tarType)) {
                // TODO: 31/5/21 Check backup format (each zip file has a comment section which can be parsed as JSON)
                try (TarArchiveOutputStream tos = new TarArchiveOutputStream(os)) {
                    tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                    tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
                    ZipEntry zipEntry;
                    while ((zipEntry = zis.getNextEntry()) != null) {
                        File tmpFile = null;
                        if (!zipEntry.isDirectory()) {
                            // We need to use a temporary file
                            tmpFile = FileCache.getGlobalFileCache().createCachedFile(dataFile.getExtension());
                            try (OutputStream fos = new FileOutputStream(tmpFile)) {
                                IoUtils.copy(zis, fos);
                            }
                        }
                        String fileName = zipEntry.getName().replaceFirst(Pattern.quote(mPackageName + "/"), "");
                        if (fileName.isEmpty()) continue;
                        // New tar entry
                        TarArchiveEntry tarArchiveEntry = new TarArchiveEntry(fileName);
                        if (tmpFile != null) {
                            tarArchiveEntry.setSize(tmpFile.length());
                        }
                        tos.putArchiveEntry(tarArchiveEntry);
                        if (tmpFile != null) {
                            // Copy from the temporary file
                            try (FileInputStream fis = new FileInputStream(tmpFile)) {
                                IoUtils.copy(fis, tos);
                            } finally {
                                FileCache.getGlobalFileCache().delete(tmpFile);
                            }
                        }
                        tos.closeArchiveEntry();
                    }
                    tos.finish();
                }
                // Encrypt backups
                Path[] newBackupFiles = mBackupItem.encrypt(sos.getFiles().toArray(new Path[0]));
                for (Path file : newBackupFiles) {
                    mChecksum.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata.info.checksumAlgo, file));
                }
            } catch (IOException e) {
                throw new BackupException("Backup failed for " + dataFile, e);
            }
        }
    }

    @SuppressLint("WrongConstant")
    @NonNull
    private BackupMetadataV2 generateMetadata() throws BackupException {
        BackupMetadataV2 metadataV2 = new BackupMetadataV2();
        mCachedApk = FileUtils.getTempPath(mPackageName, "base.apk");
        try (InputStream pis = getApkFile().openInputStream()) {
            try (OutputStream fos = mCachedApk.openOutputStream()) {
                IoUtils.copy(pis, fos);
            }
            mFilesToBeDeleted.add(getApkFile());
        } catch (IOException e) {
            throw new BackupException("Could not cache APK file", e);
        }
        String filePath = Objects.requireNonNull(mCachedApk.getFilePath());
        PackageInfo packageInfo = mPm.getPackageArchiveInfo(filePath, 0);
        if (packageInfo == null) {
            throw new BackupException("Could not fetch package info");
        }
        mPackageInfo = packageInfo;
        Objects.requireNonNull(mPackageInfo.applicationInfo);
        mPackageInfo.applicationInfo.publicSourceDir = filePath;
        mPackageInfo.applicationInfo.sourceDir = filePath;
        ApplicationInfo applicationInfo = mPackageInfo.applicationInfo;

        if (!mPackageInfo.packageName.equals(mPackageName)) {
            throw new BackupException("Package name mismatch: Expected=" + mPackageName + ", Actual=" + mPackageInfo.packageName);
        }

        metadataV2.label = applicationInfo.loadLabel(mPm).toString();
        metadataV2.packageName = mPackageName;
        metadataV2.versionName = mPackageInfo.versionName;
        metadataV2.versionCode = PackageInfoCompat.getLongVersionCode(mPackageInfo);
        metadataV2.isSystem = false;
        metadataV2.hasRules = false;
        metadataV2.backupTime = mBackupTime;
        metadataV2.crypto = CryptoUtils.MODE_NO_ENCRYPTION;
        metadataV2.apkName = "base.apk";
        // Backup flags
        BackupFlags flags = new BackupFlags(BackupFlags.BACKUP_APK_FILES);
        try {
            mFilesToBeDeleted.add(getObbFile());
            flags.addFlag(BackupFlags.BACKUP_EXT_OBB_MEDIA);
        } catch (FileNotFoundException ignore) {
        }
        try {
            mFilesToBeDeleted.add(getIntDataFile());
            flags.addFlag(BackupFlags.BACKUP_INT_DATA);
            flags.addFlag(BackupFlags.BACKUP_CACHE);
        } catch (FileNotFoundException ignore) {
        }
        try {
            mFilesToBeDeleted.add(getExtDataFile());
            flags.addFlag(BackupFlags.BACKUP_EXT_DATA);
            flags.addFlag(BackupFlags.BACKUP_CACHE);
        } catch (FileNotFoundException ignore) {
        }
        metadataV2.flags = flags;
        metadataV2.dataDirs = ConvertUtils.getDataDirs(mPackageName, mUserId, flags.backupInternalData(),
                flags.backupExternalData(), flags.backupMediaObb());
        try {
            mFilesToBeDeleted.add(getSplitFile());
            metadataV2.isSplitApk = true;
        } catch (FileNotFoundException e) {
            metadataV2.isSplitApk = false;
        }
        try {
            metadataV2.splitConfigs = cacheAndGetSplitConfigs();
        } catch (IOException | RemoteException e) {
            throw new BackupException("Could not cache splits", e);
        }
        metadataV2.userId = mUserId;
        metadataV2.tarType = Prefs.BackupRestore.getCompressionMethod();
        metadataV2.keyStore = false;
        metadataV2.installer = Prefs.Installer.getInstallerPackageName();
        return metadataV2;
    }

    @NonNull
    private Path getApkFile() throws FileNotFoundException {
        return mBackupLocation.findFile(mPackageName + ".app");
    }

    @NonNull
    private Path getSplitFile() throws FileNotFoundException {
        return mBackupLocation.findFile(mPackageName + ".splits");
    }

    @NonNull
    private Path getObbFile() throws FileNotFoundException {
        return mBackupLocation.findFile(mPackageName + ".exp");
    }

    @NonNull
    private Path getIntDataFile() throws FileNotFoundException {
        return mBackupLocation.findFile(mPackageName + ".dat");
    }

    @NonNull
    private Path getExtDataFile() throws FileNotFoundException {
        return mBackupLocation.findFile(mPackageName + ".extdat");
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
                String splitName = FileUtils.getFilenameFromZipEntry(zipEntry);
                splits.add(splitName);
                Path file = mCachedApk.requireParent().findOrCreateFile(splitName, null);
                try (OutputStream fos = file.openOutputStream()) {
                    IoUtils.copy(zis, fos);
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
            Path iconFile = mBackupItem.getIconFile();
            try (OutputStream outputStream = iconFile.openOutputStream()) {
                Bitmap bitmap = UIUtils.getBitmapFromDrawable(mPackageInfo.applicationInfo.loadIcon(mPm));
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                outputStream.flush();
            }
        } catch (Throwable th) {
            Log.w(TAG, "Could not back up icon.", th);
        }
    }
}
