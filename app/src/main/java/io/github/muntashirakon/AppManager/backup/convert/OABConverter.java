// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import static io.github.muntashirakon.AppManager.backup.BackupManager.CERT_PREFIX;
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
import java.util.Map;
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
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
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

    private BackupItems.Checksum mChecksum;
    private BackupMetadataV2 mSourceMetadata;
    private String mSourceCryptoMode;
    private Crypto mSourceCrypto;
    private BackupMetadataV5 mDestMetadata;
    private BackupItems.BackupItem mBackupItem;

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
        mSourceMetadata = readLogFile();
        // Simulate a backup creation
        try {
            mBackupItem = BackupItems.createBackupItemGracefully(mUserId, "OAndBackup", mPackageName);
        } catch (IOException e) {
            throw new BackupException("Could not get backup files.", e);
        }
        boolean backupSuccess = false;
        try {
            try {
                // Destination metadata
                mDestMetadata = ConvertUtils.getV5Metadata(mSourceMetadata, mBackupItem);
            } catch (CryptoException e) {
                throw new BackupException("Failed to get crypto " + mDestMetadata.info.crypto, e);
            }
            try {
                mChecksum = mBackupItem.getChecksum();
            } catch (IOException e) {
                throw new BackupException("Failed to create checksum file.", e);
            }
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
                throw new BackupException("Failed to write metadata.", e);
            }
            // Store checksum for metadata
            mChecksum.close();
            // Encrypt checksum
            try {
                mBackupItem.encrypt(new Path[]{mChecksum.getFile()});
            } catch (IOException e) {
                throw new BackupException("Failed to encrypt checksums.txt", e);
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
            if (backupSuccess) {
                BackupUtils.putBackupToDbAndBroadcast(ContextUtils.getContext(), mDestMetadata);
            }
        }
    }

    @Override
    public void cleanup() {
        mSourceCrypto.close();
        mBackupLocation.delete();
    }

    @Override
    public String getPackageName() {
        return mPackageName;
    }

    private BackupMetadataV2 readLogFile() throws BackupException {
        try {
            BackupMetadataV2 metadataV2 = new BackupMetadataV2();
            Path logFile = mBackupLocation.findFile(mPackageName + ".log");
            String jsonString = logFile.getContentAsString();
            if (TextUtils.isEmpty(jsonString)) throw new JSONException("Empty JSON string.");
            JSONObject jsonObject = new JSONObject(jsonString);
            metadataV2.label = jsonObject.getString("label");
            metadataV2.packageName = jsonObject.getString("packageName");
            metadataV2.versionName = jsonObject.getString("versionName");
            metadataV2.versionCode = jsonObject.getInt("versionCode");
            metadataV2.isSystem = jsonObject.optBoolean("isSystem");
            metadataV2.isSplitApk = false;
            metadataV2.splitConfigs = ArrayUtils.emptyArray(String.class);
            metadataV2.hasRules = false;
            metadataV2.backupTime = jsonObject.getLong("lastBackupMillis");
            metadataV2.crypto = jsonObject.optBoolean("isEncrypted") ? CryptoUtils.MODE_OPEN_PGP : CryptoUtils.MODE_NO_ENCRYPTION;
            mSourceCryptoMode = metadataV2.crypto;
            mSourceCrypto = CryptoUtils.setupCrypto(metadataV2);
            metadataV2.apkName = new File(jsonObject.getString("sourceDir")).getName();
            // Flags
            metadataV2.flags = new BackupFlags(BackupFlags.BACKUP_MULTIPLE);
            int backupMode = jsonObject.optInt("backupMode", MODE_UNSET);
            if (backupMode == MODE_UNSET) {
                throw new BackupException("Destination doesn't contain any backup.");
            }
            if (backupMode == MODE_APK || backupMode == MODE_BOTH) {
                if (mBackupLocation.hasFile(CryptoUtils.getAppropriateFilename(metadataV2.apkName,
                        mSourceCryptoMode))) {
                    metadataV2.flags.addFlag(BackupFlags.BACKUP_APK_FILES);
                } else {
                    throw new BackupException("Destination doesn't contain any APK files.");
                }
            }
            if (backupMode == MODE_DATA || backupMode == MODE_BOTH) {
                boolean hasBackup = false;
                if (mBackupLocation.hasFile(CryptoUtils.getAppropriateFilename(mPackageName + ".zip",
                        mSourceCryptoMode))) {
                    metadataV2.flags.addFlag(BackupFlags.BACKUP_INT_DATA);
                    hasBackup = true;
                }
                if (mBackupLocation.hasFile(EXTERNAL_FILES) && mBackupLocation.findFile(EXTERNAL_FILES).hasFile(
                        CryptoUtils.getAppropriateFilename(mPackageName + ".zip", mSourceCryptoMode))) {
                    metadataV2.flags.addFlag(BackupFlags.BACKUP_EXT_DATA);
                    hasBackup = true;
                }
                if (!hasBackup) {
                    throw new BackupException("Destination doesn't contain any data files.");
                }
                metadataV2.flags.addFlag(BackupFlags.BACKUP_CACHE);
            }
            metadataV2.userId = UserHandleHidden.myUserId();
            metadataV2.dataDirs = ConvertUtils.getDataDirs(mPackageName, mUserId, metadataV2.flags
                    .backupInternalData(), metadataV2.flags.backupExternalData(), false);
            metadataV2.tarType = Prefs.BackupRestore.getCompressionMethod();
            metadataV2.keyStore = false;
            metadataV2.installer = Prefs.Installer.getInstallerPackageName();
            metadataV2.version = 2;  // Old version is used so that we know that it needs permission fixes
            return metadataV2;
        } catch (JSONException | IOException | CryptoException e) {
            return ExUtils.rethrowAsBackupException("Could not parse JSON file.", e);
        }
    }

    private void backupApkFile() throws BackupException {
        Path[] baseApkFiles;
        try {
            baseApkFiles = new Path[]{mBackupLocation.findFile(CryptoUtils.getAppropriateFilename(
                    mSourceMetadata.apkName, mSourceCryptoMode))};
        } catch (FileNotFoundException e) {
            throw new BackupException("Could not get base.apk file.", e);
        }
        // Decrypt APK file if needed
        try {
            baseApkFiles = ConvertUtils.decryptSourceFiles(baseApkFiles, mSourceCrypto, mSourceCryptoMode, mBackupItem);
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
            String[] checksums = ConvertUtils.getChecksumsFromApk(baseApkFile, mDestMetadata.info.checksumAlgo);
            for (int i = 0; i < checksums.length; ++i) {
                mChecksum.add(CERT_PREFIX + i, checksums[i]);
            }
        } catch (Exception ignore) {
        }
        // Backup APK file
        String sourceBackupFilePrefix = SOURCE_PREFIX + getExt(mDestMetadata.info.tarType);
        Path[] sourceFiles;
        try {
            sourceFiles = TarUtils.create(mDestMetadata.info.tarType, baseApkFile, mBackupItem.getUnencryptedBackupPath(), sourceBackupFilePrefix,
                            /* language=regexp */ new String[]{".*\\.apk"}, null, null, false)
                    .toArray(new Path[0]);
        } catch (Throwable th) {
            throw new BackupException("APK files backup is requested but no APK files have been backed up.", th);
        }
        // Overwrite with the new files
        try {
            sourceFiles = mBackupItem.encrypt(sourceFiles);
        } catch (IOException e) {
            throw new BackupException("Failed to encrypt " + Arrays.toString(sourceFiles), e);
        }
        for (Path file : sourceFiles) {
            mChecksum.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata.info.checksumAlgo, file));
        }
    }

    private void backupData() throws BackupException {
        List<Path> dataFiles = new ArrayList<>(2);
        if (mDestMetadata.info.flags.backupInternalData()) {
            try {
                dataFiles.add(mBackupLocation.findFile(CryptoUtils.getAppropriateFilename(mPackageName + ".zip",
                        mSourceCryptoMode)));
            } catch (FileNotFoundException e) {
                throw new BackupException("Could not get internal data backup.", e);
            }
        }
        if (mDestMetadata.info.flags.backupExternalData()) {
            try {
                dataFiles.add(mBackupLocation.findFile(EXTERNAL_FILES).findFile(CryptoUtils.getAppropriateFilename(
                        mPackageName + ".zip", mSourceCryptoMode)));
            } catch (FileNotFoundException e) {
                throw new BackupException("Could not get external data backup.", e);
            }
        }
        String tarType = mDestMetadata.info.tarType;
        int i = 0;
        Path[] files;
        for (Path dataFile : dataFiles) {
            files = new Path[]{dataFile};
            // Decrypt APK file if needed
            try {
                files = ConvertUtils.decryptSourceFiles(files, mSourceCrypto, mSourceCryptoMode, mBackupItem);
            } catch (IOException e) {
                throw new BackupException("Failed to decrypt " + Arrays.toString(files), e);
            }
            // baseApkFiles should be a singleton array
            if (files.length != 1) {
                throw new BackupException("Incorrect number of APK files: " + files.length);
            }
            String dataBackupFilePrefix = BackupUtils.getDataFilePrefix(i++, getExt(tarType));
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(files[0].openInputStream()));
                 SplitOutputStream sos = new SplitOutputStream(mBackupItem.getUnencryptedBackupPath(), dataBackupFilePrefix, DEFAULT_SPLIT_SIZE);
                 BufferedOutputStream bos = new BufferedOutputStream(sos)) {
                OutputStream os;
                switch (tarType) {
                    case TAR_GZIP:
                        os = new GzipCompressorOutputStream(bos);
                        break;
                    case TAR_BZIP2:
                        os = new BZip2CompressorOutputStream(bos);
                        break;
                    case TAR_ZSTD:
                        os = new ZstdOutputStream(bos);
                        break;
                    default:
                        throw new BackupException("Invalid compression type: " + tarType);
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
}
