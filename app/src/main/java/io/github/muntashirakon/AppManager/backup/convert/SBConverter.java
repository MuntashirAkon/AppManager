// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import static io.github.muntashirakon.AppManager.backup.BackupManager.CERT_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.DATA_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.ICON_FILE;
import static io.github.muntashirakon.AppManager.backup.BackupManager.SOURCE_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.getExt;
import static io.github.muntashirakon.AppManager.utils.TarUtils.DEFAULT_SPLIT_SIZE;
import static io.github.muntashirakon.AppManager.utils.TarUtils.TAR_BZIP2;
import static io.github.muntashirakon.AppManager.utils.TarUtils.TAR_GZIP;
import static io.github.muntashirakon.AppManager.utils.TarUtils.TAR_ZSTD;

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

import com.github.luben.zstd.ZstdOutputStream;

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
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.backup.BackupFiles;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.crypto.Crypto;
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

    private Crypto mCrypto;
    private BackupFiles.Checksum mChecksum;
    private MetadataManager.Metadata mSourceMetadata;
    private MetadataManager.Metadata mDestMetadata;
    private Path mTempBackupPath;
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
        mSourceMetadata = new MetadataManager.Metadata();
        generateMetadata();
        // Destination metadata
        mDestMetadata = new MetadataManager.Metadata(mSourceMetadata);
        MetadataManager metadataManager = MetadataManager.getNewInstance();
        metadataManager.setMetadata(mDestMetadata);
        // Simulate a backup creation
        // If the package has another backup named SB, another backup will be created
        BackupFiles backupFiles;
        BackupFiles.BackupFile[] backupFileList;
        try {
            backupFiles = new BackupFiles(mPackageName, mUserId, new String[]{"SB"});
            backupFileList = backupFiles.getBackupPaths(true);
        } catch (IOException e) {
            throw new BackupException("Could not get backup files.", e);
        }
        for (BackupFiles.BackupFile backupFile : backupFileList) {
            // We're iterating over a singleton list
            boolean backupSuccess = false;
            try {
                mTempBackupPath = backupFile.getBackupPath();
                mCrypto = ConvertUtils.setupCrypto(mDestMetadata);
                try {
                    mChecksum = backupFile.getChecksum(CryptoUtils.MODE_NO_ENCRYPTION);
                } catch (IOException e) {
                    throw new BackupException("Failed to create checksum file.", e);
                }
                // Backup icon
                backupIcon();
                if (mDestMetadata.flags.backupApkFiles()) {
                    backupApkFile();
                }
                if (mDestMetadata.flags.backupData()) {
                    backupData();
                }
                // Write modified metadata
                metadataManager.setMetadata(mDestMetadata);
                try {
                    metadataManager.writeMetadata(backupFile);
                } catch (IOException e) {
                    throw new BackupException("Failed to write metadata.");
                }
                // Store checksum for metadata
                try {
                    mChecksum.add(MetadataManager.META_FILE, DigestUtils.getHexDigest(mDestMetadata.checksumAlgo,
                            backupFile.getMetadataFile()));
                } catch (IOException e) {
                    throw new BackupException("Failed to generate checksum for meta.json", e);
                }
                mChecksum.close();
                // Encrypt checksum
                try {
                    Path checksumFile = backupFile.getChecksumFile(CryptoUtils.MODE_NO_ENCRYPTION);
                    encrypt(new Path[]{checksumFile});
                } catch (IOException e) {
                    throw new BackupException("Failed to encrypt checksums.txt");
                }
                // Replace current backup
                try {
                    backupFile.commit();
                } catch (IOException e) {
                    throw new BackupException("Could not finalise backup.", e);
                }
                backupSuccess = true;
            } catch (BackupException e) {
                throw e;
            } catch (Throwable th) {
                throw new BackupException("Unknown error occurred.", th);
            } finally {
                if (!backupSuccess) {
                    backupFile.cleanup();
                }
                if (mCrypto != null) {
                    mCrypto.close();
                }
                mCachedApk.requireParent().delete();
                if (backupSuccess) {
                    BackupUtils.putBackupToDbAndBroadcast(ContextUtils.getContext(), mDestMetadata);
                }
            }
            return;
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
            String[] checksums = ConvertUtils.getChecksumsFromApk(mCachedApk, mDestMetadata.checksumAlgo);
            for (int i = 0; i < checksums.length; ++i) {
                mChecksum.add(CERT_PREFIX + i, checksums[i]);
            }
        } catch (Exception ignore) {
        }
        // Backup APK files
        String[] apkFiles = ArrayUtils.appendElement(String.class, mDestMetadata.splitConfigs, mDestMetadata.apkName);
        String sourceBackupFilePrefix = SOURCE_PREFIX + getExt(mDestMetadata.tarType);
        Path[] sourceFiles;
        try {
            // We have to specify APK files because the folder may contain many
            sourceFiles = TarUtils.create(mDestMetadata.tarType, sourceDir, mTempBackupPath, sourceBackupFilePrefix,
                    apkFiles, null, null, false).toArray(new Path[0]);
        } catch (Throwable th) {
            throw new BackupException("APK files backup is requested but no APK files have been backed up.", th);
        }
        try {
            sourceFiles = encrypt(sourceFiles);
        } catch (IOException e) {
            throw new BackupException("Failed to encrypt " + Arrays.toString(sourceFiles));
        }
        for (Path file : sourceFiles) {
            mChecksum.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata.checksumAlgo, file));
        }
    }

    private void backupData() throws BackupException {
        List<Path> dataFiles = new ArrayList<>(3);
        try {
            if (mDestMetadata.flags.backupInternalData()) {
                dataFiles.add(getIntDataFile());
            }
            if (mDestMetadata.flags.backupExternalData()) {
                dataFiles.add(getExtDataFile());
            }
            if (mDestMetadata.flags.backupMediaObb()) {
                dataFiles.add(getObbFile());
            }
        } catch (FileNotFoundException e) {
            throw new BackupException("Could not get data files", e);
        }
        int i = 0;
        for (Path dataFile : dataFiles) {
            String dataBackupFilePrefix = DATA_PREFIX + (i++) + getExt(mDestMetadata.tarType);
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(dataFile.openInputStream()));
                 SplitOutputStream sos = new SplitOutputStream(mTempBackupPath, dataBackupFilePrefix, DEFAULT_SPLIT_SIZE);
                 BufferedOutputStream bos = new BufferedOutputStream(sos)) {
                // TODO: 31/5/21 Check backup format (each zip file has a comment section which can be parsed as JSON)
                OutputStream os;
                if (TAR_GZIP.equals(mDestMetadata.tarType)) {
                    os = new GzipCompressorOutputStream(bos);
                } else if (TAR_BZIP2.equals(mDestMetadata.tarType)) {
                    os = new BZip2CompressorOutputStream(bos);
                } else if (TAR_ZSTD.equals(mDestMetadata.tarType)) {
                    os = new ZstdOutputStream(bos);
                } else {
                    throw new BackupException("Invalid compression type: " + mDestMetadata.tarType);
                }
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
                Path[] newBackupFiles = encrypt(sos.getFiles().toArray(new Path[0]));
                for (Path file : newBackupFiles) {
                    mChecksum.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata.checksumAlgo, file));
                }
            } catch (IOException e) {
                throw new BackupException("Backup failed for " + dataFile, e);
            }
        }
    }

    @SuppressLint("WrongConstant")
    private void generateMetadata() throws BackupException {
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
        mPackageInfo = mPm.getPackageArchiveInfo(filePath, 0);
        if (mPackageInfo == null) {
            throw new BackupException("Could not fetch package info");
        }
        mPackageInfo.applicationInfo.publicSourceDir = filePath;
        mPackageInfo.applicationInfo.sourceDir = filePath;
        ApplicationInfo applicationInfo = mPackageInfo.applicationInfo;

        if (!mPackageInfo.packageName.equals(mPackageName)) {
            throw new BackupException("Package name mismatch: Expected=" + mPackageName + ", Actual=" + mPackageInfo.packageName);
        }

        mSourceMetadata.label = applicationInfo.loadLabel(mPm).toString();
        mSourceMetadata.packageName = mPackageName;
        mSourceMetadata.versionName = mPackageInfo.versionName;
        mSourceMetadata.versionCode = PackageInfoCompat.getLongVersionCode(mPackageInfo);
        mSourceMetadata.isSystem = false;
        mSourceMetadata.hasRules = false;
        mSourceMetadata.backupTime = mBackupTime;
        mSourceMetadata.crypto = CryptoUtils.getMode();
        mSourceMetadata.apkName = "base.apk";
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
        mSourceMetadata.flags = flags;
        mSourceMetadata.dataDirs = ConvertUtils.getDataDirs(mPackageName, mUserId, flags.backupInternalData(),
                flags.backupExternalData(), flags.backupMediaObb());
        try {
            mFilesToBeDeleted.add(getSplitFile());
            mSourceMetadata.isSplitApk = true;
        } catch (FileNotFoundException e) {
            mSourceMetadata.isSplitApk = false;
        }
        try {
            mSourceMetadata.splitConfigs = cacheAndGetSplitConfigs();
        } catch (IOException | RemoteException e) {
            throw new BackupException("Could not cache splits", e);
        }
        mSourceMetadata.userHandle = mUserId;
        mSourceMetadata.tarType = Prefs.BackupRestore.getCompressionMethod();
        mSourceMetadata.keyStore = false;
        mSourceMetadata.installer = Prefs.Installer.getInstallerPackageName();
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
            Path iconFile = mTempBackupPath.findOrCreateFile(ICON_FILE, null);
            try (OutputStream outputStream = iconFile.openOutputStream()) {
                Bitmap bitmap = UIUtils.getBitmapFromDrawable(mPackageInfo.applicationInfo.loadIcon(mPm));
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                outputStream.flush();
            }
        } catch (Throwable th) {
            Log.w(TAG, "Could not back up icon.", th);
        }
    }

    @NonNull
    private Path[] encrypt(@NonNull Path[] files) throws IOException {
        synchronized (Class.class) {
            mCrypto.encrypt(files);
            return mCrypto.getNewFiles();
        }
    }
}
