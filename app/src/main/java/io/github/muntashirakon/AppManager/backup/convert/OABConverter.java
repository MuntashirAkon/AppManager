// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import static io.github.muntashirakon.AppManager.backup.BackupManager.CERT_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.DATA_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.SOURCE_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.getExt;
import static io.github.muntashirakon.AppManager.utils.TarUtils.DEFAULT_SPLIT_SIZE;
import static io.github.muntashirakon.AppManager.utils.TarUtils.TAR_BZIP2;
import static io.github.muntashirakon.AppManager.utils.TarUtils.TAR_GZIP;
import static io.github.muntashirakon.AppManager.utils.TarUtils.TAR_ZSTD;

import android.annotation.UserIdInt;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.github.luben.zstd.ZstdOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.SplitOutputStream;

/**
 * A documentation about OAndBackup is located at
 * <a href=https://github.com/MuntashirAkon/AppManager/issues/371#issuecomment-818429082>GH#371</a>.
 */
public class OABConverter extends Converter {
    public static final String TAG = OABConverter.class.getSimpleName();

    public static final String PATH_SUFFIX = "oandbackups";

    private static final List<String> SPECIAL_BACKUPS = new ArrayList<String>() {
        {
            add("accounts");
            add("appwidgets");
            add("bluetooth");
            add("data.usage.policy");
            add("wallpaper");
            add("wifi.access.points");
        }
    };

    private static final int MODE_UNSET = 0;
    private static final int MODE_APK = 1;
    private static final int MODE_DATA = 2;
    private static final int MODE_BOTH = 3;

    private static final String EXTERNAL_FILES = "external_files";

    private final Path mBackupLocation;
    private final String mPackageName;
    @UserIdInt
    private final int mUserId;
    private final List<Path> mDecryptedFiles = new ArrayList<>();

    private Crypto mCrypto;
    private BackupFiles.Checksum mChecksum;
    private MetadataManager.Metadata mSourceMetadata;
    private MetadataManager.Metadata mDestMetadata;
    private Path mTempBackupPath;

    /**
     * @param backupLocation E.g. {@code /sdcard/oandbackups/package.name}
     */
    public OABConverter(@NonNull Path backupLocation) {
        mBackupLocation = backupLocation;
        // Last path component is the package name
        mPackageName = backupLocation.getName();
        mUserId = UserHandleHidden.myUserId();
    }

    @Override
    public void convert() throws BackupException {
        if (SPECIAL_BACKUPS.contains(mPackageName)) {
            throw new BackupException("Cannot convert special backup " + mPackageName);
        }
        // Source metadata
        mSourceMetadata = new MetadataManager.Metadata();
        readLogFile();
        // Destination metadata
        mDestMetadata = new MetadataManager.Metadata(mSourceMetadata);
        // Destination files will be encrypted by the default encryption method
        mDestMetadata.crypto = CryptoUtils.getMode();
        MetadataManager metadataManager = MetadataManager.getNewInstance();
        metadataManager.setMetadata(mDestMetadata);
        // Simulate a backup creation
        BackupFiles backupFiles;
        BackupFiles.BackupFile[] backupFileList;
        try {
            backupFiles = new BackupFiles(mPackageName, mUserId, new String[]{"OAndBackup"});
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
                    throw new BackupException("Failed to write metadata.", e);
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
                    throw new BackupException("Failed to encrypt checksums.txt", e);
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
                for (Path file : mDecryptedFiles) {
                    Log.d(TAG, "Deleting %s", file);
                    file.delete();
                }
                if (backupSuccess) {
                    BackupUtils.putBackupToDbAndBroadcast(ContextUtils.getContext(), mDestMetadata);
                }
            }
            return;
        }
    }

    @Override
    public void cleanup() {
        mBackupLocation.delete();
    }

    @Override
    public String getPackageName() {
        return mPackageName;
    }

    private void readLogFile() throws BackupException {
        try {
            Path logFile = mBackupLocation.findFile(mPackageName + ".log");
            String jsonString = logFile.getContentAsString();
            if (TextUtils.isEmpty(jsonString)) throw new JSONException("Empty JSON string.");
            JSONObject jsonObject = new JSONObject(jsonString);
            mSourceMetadata.label = jsonObject.getString("label");
            mSourceMetadata.packageName = jsonObject.getString("packageName");
            mSourceMetadata.versionName = jsonObject.getString("versionName");
            mSourceMetadata.versionCode = jsonObject.getInt("versionCode");
            mSourceMetadata.isSystem = jsonObject.optBoolean("isSystem");
            mSourceMetadata.isSplitApk = false;
            mSourceMetadata.splitConfigs = ArrayUtils.emptyArray(String.class);
            mSourceMetadata.hasRules = false;
            mSourceMetadata.backupTime = jsonObject.getLong("lastBackupMillis");
            mSourceMetadata.crypto = jsonObject.optBoolean("isEncrypted") ? CryptoUtils.MODE_OPEN_PGP : CryptoUtils.MODE_NO_ENCRYPTION;
            mSourceMetadata.apkName = new File(jsonObject.getString("sourceDir")).getName();
            // Flags
            mSourceMetadata.flags = new BackupFlags(BackupFlags.BACKUP_MULTIPLE);
            int backupMode = jsonObject.optInt("backupMode", MODE_UNSET);
            if (backupMode == MODE_UNSET) {
                throw new BackupException("Destination doesn't contain any backup.");
            }
            if (backupMode == MODE_APK || backupMode == MODE_BOTH) {
                if (mBackupLocation.hasFile(CryptoUtils.getAppropriateFilename(mSourceMetadata.apkName,
                        mSourceMetadata.crypto))) {
                    mSourceMetadata.flags.addFlag(BackupFlags.BACKUP_APK_FILES);
                } else {
                    throw new BackupException("Destination doesn't contain any APK files.");
                }
            }
            if (backupMode == MODE_DATA || backupMode == MODE_BOTH) {
                boolean hasBackup = false;
                if (mBackupLocation.hasFile(CryptoUtils.getAppropriateFilename(mPackageName + ".zip",
                        mSourceMetadata.crypto))) {
                    mSourceMetadata.flags.addFlag(BackupFlags.BACKUP_INT_DATA);
                    hasBackup = true;
                }
                if (mBackupLocation.hasFile(EXTERNAL_FILES) && mBackupLocation.findFile(EXTERNAL_FILES).hasFile(
                        CryptoUtils.getAppropriateFilename(mPackageName + ".zip", mSourceMetadata.crypto))) {
                    mSourceMetadata.flags.addFlag(BackupFlags.BACKUP_EXT_DATA);
                    hasBackup = true;
                }
                if (!hasBackup) {
                    throw new BackupException("Destination doesn't contain any data files.");
                }
                mSourceMetadata.flags.addFlag(BackupFlags.BACKUP_CACHE);
            }
            mSourceMetadata.userHandle = UserHandleHidden.myUserId();
            mSourceMetadata.dataDirs = ConvertUtils.getDataDirs(mPackageName, mUserId, mSourceMetadata.flags
                    .backupInternalData(), mSourceMetadata.flags.backupExternalData(), false);
            mSourceMetadata.tarType = Prefs.BackupRestore.getCompressionMethod();
            mSourceMetadata.keyStore = false;
            mSourceMetadata.installer = Prefs.Installer.getInstallerPackageName();
            mSourceMetadata.version = 2;  // Old version is used so that we know that it needs permission fixes
        } catch (JSONException | IOException e) {
            ExUtils.rethrowAsBackupException("Could not parse JSON file.", e);
        }
    }

    private void backupApkFile() throws BackupException {
        Path[] baseApkFiles;
        try {
            baseApkFiles = new Path[]{mBackupLocation.findFile(CryptoUtils.getAppropriateFilename(
                    mSourceMetadata.apkName, mSourceMetadata.crypto))};
        } catch (FileNotFoundException e) {
            throw new BackupException("Could not get base.apk file.", e);
        }
        // Decrypt APK file if needed
        try {
            baseApkFiles = decrypt(baseApkFiles);
        } catch (IOException e) {
            throw new BackupException("Failed to decrypt " + Arrays.toString(baseApkFiles), e);
        }
        // baseApkFiles should be a singleton array
        if (baseApkFiles.length != 1) {
            throw new BackupException("Incorrect number of APK files: " + baseApkFiles.length);
        }
        Path baseApkFile = baseApkFiles[0];
        // Get certificate checksums
        try {
            String[] checksums = ConvertUtils.getChecksumsFromApk(baseApkFile, mDestMetadata.checksumAlgo);
            for (int i = 0; i < checksums.length; ++i) {
                mChecksum.add(CERT_PREFIX + i, checksums[i]);
            }
        } catch (Exception ignore) {
        }
        // Backup APK file
        String sourceBackupFilePrefix = SOURCE_PREFIX + getExt(mDestMetadata.tarType);
        Path[] sourceFiles;
        try {
            sourceFiles = TarUtils.create(mDestMetadata.tarType, baseApkFile, mTempBackupPath, sourceBackupFilePrefix,
                            /* language=regexp */ new String[]{".*\\.apk"}, null, null, false)
                    .toArray(new Path[0]);
        } catch (Throwable th) {
            throw new BackupException("APK files backup is requested but no APK files have been backed up.", th);
        }
        // Overwrite with the new files
        try {
            sourceFiles = encrypt(sourceFiles);
        } catch (IOException e) {
            throw new BackupException("Failed to encrypt " + Arrays.toString(sourceFiles), e);
        }
        for (Path file : sourceFiles) {
            mChecksum.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata.checksumAlgo, file));
        }
    }

    private void backupData() throws BackupException {
        List<Path> dataFiles = new ArrayList<>(2);
        if (mDestMetadata.flags.backupInternalData()) {
            try {
                dataFiles.add(mBackupLocation.findFile(CryptoUtils.getAppropriateFilename(mPackageName + ".zip",
                        mSourceMetadata.crypto)));
            } catch (FileNotFoundException e) {
                throw new BackupException("Could not get internal data backup.", e);
            }
        }
        if (mDestMetadata.flags.backupExternalData()) {
            try {
                dataFiles.add(mBackupLocation.findFile(EXTERNAL_FILES).findFile(CryptoUtils.getAppropriateFilename(
                        mPackageName + ".zip", mSourceMetadata.crypto)));
            } catch (FileNotFoundException e) {
                throw new BackupException("Could not get external data backup.", e);
            }
        }
        int i = 0;
        Path[] files;
        for (Path dataFile : dataFiles) {
            files = new Path[]{dataFile};
            // Decrypt APK file if needed
            try {
                files = decrypt(files);
            } catch (IOException e) {
                throw new BackupException("Failed to decrypt " + Arrays.toString(files), e);
            }
            // baseApkFiles should be a singleton array
            if (files.length != 1) {
                throw new BackupException("Incorrect number of APK files: " + files.length);
            }
            String dataBackupFilePrefix = DATA_PREFIX + (i++) + getExt(mDestMetadata.tarType);
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(files[0].openInputStream()));
                 SplitOutputStream sos = new SplitOutputStream(mTempBackupPath, dataBackupFilePrefix, DEFAULT_SPLIT_SIZE);
                 BufferedOutputStream bos = new BufferedOutputStream(sos)) {
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
                            tmpFile = FileCache.getGlobalFileCache().createCachedFile(files[0].getExtension());
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

    @NonNull
    private Path[] encrypt(@NonNull Path[] files) throws IOException {
        synchronized (Crypto.class) {
            mCrypto.encrypt(files);
            return mCrypto.getNewFiles();
        }
    }

    @NonNull
    private Path[] decrypt(@NonNull Path[] files) throws IOException {
        Path[] newFiles;
        synchronized (Crypto.class) {
            mCrypto.decrypt(files);
            newFiles = mCrypto.getNewFiles();
        }
        mDecryptedFiles.addAll(Arrays.asList(newFiles));
        return newFiles.length > 0 ? newFiles : files;
    }
}
