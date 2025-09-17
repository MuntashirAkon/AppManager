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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.UserHandleHidden;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.luben.zstd.ZstdOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

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
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.SplitOutputStream;

public class TBConverter extends Converter {
    public static final String TAG = TBConverter.class.getSimpleName();

    public static final String PATH_SUFFIX = "TitaniumBackup";

    private static final String INTERNAL_PREFIX = "data/data/";
    private static final String EXTERNAL_PREFIX = "data/data/.external.";

    private final Path mBackupLocation;
    @UserIdInt
    private final int mUserId;
    private final Path mPropFile;
    private final String mPackageName;
    private final long mBackupTime;
    private final List<Path> mFilesToBeDeleted = new ArrayList<>();

    private BackupItems.Checksum mChecksum;
    private BackupMetadataV2 mSourceMetadata;
    private BackupMetadataV5 mDestMetadata;
    private BackupItems.BackupItem mBackupItem;
    @Nullable
    private Bitmap mIcon;

    /**
     * A documentation about Titanium Backup is located at
     * <a href=https://github.com/MuntashirAkon/AppManager/issues/371#issuecomment-818491126>GH#371</a>.
     *
     * @param propFile Location to the properties file e.g. {@code /sdcard/TitaniumBackup/package.name-YYYYMMDD-HHMMSS.properties}
     */
    public TBConverter(@NonNull Path propFile) {
        mPropFile = propFile;
        mBackupLocation = propFile.getParent();
        mUserId = UserHandleHidden.myUserId();
        String dirtyName = propFile.getName();
        int idx = dirtyName.indexOf('-');
        if (idx == -1) mPackageName = null;
        else mPackageName = dirtyName.substring(0, idx);
        mBackupTime = propFile.lastModified();  // TODO: Grab from the file name
        mFilesToBeDeleted.add(propFile);
    }

    @Override
    public void convert() throws BackupException {
        if (mPackageName == null) {
            throw new BackupException("Could not read package name.");
        }
        // Source metadata
        mSourceMetadata = readPropFile();
        // Simulate a backup creation
        try {
            mBackupItem = BackupItems.createBackupItemGracefully(mUserId, "TB", mPackageName);
        } catch (IOException e) {
            throw new BackupException("Could not get backup files", e);
        }
        boolean backupSuccess = false;
        try {
            try {
                // Destination metadata
                mDestMetadata = ConvertUtils.getV5Metadata(mSourceMetadata, mBackupItem);
                // Destination APK will be renamed
                mDestMetadata.metadata.apkName = "base.apk";
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
                throw new BackupException("Failed to write metadata.", e);
            }
            mChecksum.close();
            // Encrypt checksum
            try {
                mBackupItem.encrypt(new Path[]{mChecksum.getFile()});
            } catch (IOException e) {
                throw new BackupException("Failed to encrypt checksums.txt");
            }
            // Replace current backup:
            // There's hardly any chance of getting a false here but checks are done anyway.
            try {
                mBackupItem.commit();
            } catch (Exception e) {
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
    public String getPackageName() {
        return mPackageName;
    }

    @Override
    public void cleanup() {
        for (Path file : mFilesToBeDeleted) {
            file.delete();
        }
    }

    private void backupApkFile() throws BackupException {
        // Decompress APK file
        Path baseApkFile = FileUtils.getTempPath(mPackageName, mDestMetadata.metadata.apkName);
        try (InputStream pis = getApkFile(mSourceMetadata.apkName, mSourceMetadata.tarType).openInputStream();
             BufferedInputStream bis = new BufferedInputStream(pis)) {
            CompressorInputStream is;
            if (TAR_GZIP.equals(mSourceMetadata.tarType)) {
                is = new GzipCompressorInputStream(bis, true);
            } else if (TAR_BZIP2.equals(mSourceMetadata.tarType)) {
                is = new BZip2CompressorInputStream(bis, true);
            } else {
                baseApkFile.requireParent().delete();
                throw new BackupException("Invalid source compression type: " + mSourceMetadata.tarType);
            }
            try (OutputStream fos = baseApkFile.openOutputStream()) {
                // The whole file is the APK
                IoUtils.copy(is, fos);
            } finally {
                is.close();
            }
        } catch (IOException e) {
            baseApkFile.requireParent().delete();
            throw new BackupException("Couldn't decompress " + mSourceMetadata.apkName, e);
        }
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
                            /* language=regexp */new String[]{".*\\.apk"}, null, null, false)
                    .toArray(new Path[0]);
        } catch (Throwable th) {
            throw new BackupException("APK files backup is requested but no APK files have been backed up.", th);
        } finally {
            baseApkFile.requireParent().delete();
        }
        // Overwrite with the new files
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
        Path dataFile;
        try {
            dataFile = getDataFile(Paths.trimPathExtension(mPropFile.getName()), mSourceMetadata.tarType);
        } catch (FileNotFoundException e) {
            throw new BackupException("Could not get data file", e);
        }
        String tarType = mDestMetadata.info.tarType;
        int i = 0;
        String intBackupFilePrefix = null;
        String extBackupFilePrefix = null;
        if (mDestMetadata.info.flags.backupInternalData()) {
            intBackupFilePrefix = BackupUtils.getDataFilePrefix(i++, getExt(tarType));
        }
        if (mDestMetadata.info.flags.backupExternalData()) {
            extBackupFilePrefix = BackupUtils.getDataFilePrefix(i, getExt(tarType));
        }
        try (BufferedInputStream bis = new BufferedInputStream(dataFile.openInputStream())) {
            CompressorInputStream cis;
            if (TAR_GZIP.equals(mSourceMetadata.tarType)) {
                cis = new GzipCompressorInputStream(bis);
            } else if (TAR_BZIP2.equals(mSourceMetadata.tarType)) {
                cis = new BZip2CompressorInputStream(bis);
            } else {
                throw new BackupException("Invalid compression type: " + tarType);
            }
            TarArchiveInputStream tis = new TarArchiveInputStream(cis);
            SplitOutputStream intSos = null, extSos = null;
            TarArchiveOutputStream intTos = null, extTos = null;
            if (intBackupFilePrefix != null) {
                intSos = new SplitOutputStream(mBackupItem.getUnencryptedBackupPath(), intBackupFilePrefix, DEFAULT_SPLIT_SIZE);
                BufferedOutputStream bos = new BufferedOutputStream(intSos);
                OutputStream cos;
                switch (tarType) {
                    case TAR_GZIP:
                        cos = new GzipCompressorOutputStream(bos);
                        break;
                    case TAR_BZIP2:
                        cos = new BZip2CompressorOutputStream(bos);
                        break;
                    case TAR_ZSTD:
                        cos = new ZstdOutputStream(bos);
                        break;
                    default:
                        throw new BackupException("Invalid compression type: " + tarType);
                }
                intTos = new TarArchiveOutputStream(cos);
                intTos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                intTos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            }
            if (extBackupFilePrefix != null) {
                extSos = new SplitOutputStream(mBackupItem.getUnencryptedBackupPath(), extBackupFilePrefix, DEFAULT_SPLIT_SIZE);
                BufferedOutputStream bos = new BufferedOutputStream(extSos);
                OutputStream cos;
                switch (tarType) {
                    case TAR_GZIP:
                        cos = new GzipCompressorOutputStream(bos);
                        break;
                    case TAR_BZIP2:
                        cos = new BZip2CompressorOutputStream(bos);
                        break;
                    case TAR_ZSTD:
                        cos = new ZstdOutputStream(bos);
                        break;
                    default:
                        throw new BackupException("Invalid compression type: " + tarType);
                }
                extTos = new TarArchiveOutputStream(cos);
                extTos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                extTos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            }

            // Add files
            TarArchiveEntry inTarEntry;
            while ((inTarEntry = tis.getNextEntry()) != null) {
                String fileName = inTarEntry.getName();
                boolean isExternal = fileName.startsWith(EXTERNAL_PREFIX);
                // Get new file name
                fileName = fileName.replaceFirst((isExternal ? EXTERNAL_PREFIX : INTERNAL_PREFIX) + Pattern.quote(mPackageName + "/") + "\\./", "");
                if (fileName.isEmpty()) continue;
                // New tar entry
                TarArchiveEntry outTarEntry = new TarArchiveEntry(fileName);
                outTarEntry.setMode(inTarEntry.getMode());
                outTarEntry.setUserId(inTarEntry.getUserId());
                outTarEntry.setGroupId(inTarEntry.getGroupId());
                outTarEntry.setSize(inTarEntry.getSize());
                if (isExternal) {
                    if (extTos != null) {
                        extTos.putArchiveEntry(outTarEntry);
                    }
                } else {
                    if (intTos != null) {
                        intTos.putArchiveEntry(outTarEntry);
                    }
                }
                if (!inTarEntry.isDirectory() && !inTarEntry.isSymbolicLink()) {
                    if (isExternal) {
                        if (extTos != null) {
                            IoUtils.copy(tis, extTos);
                        }
                    } else {
                        if (intTos != null) {
                            IoUtils.copy(tis, intTos);
                        }
                    }
                }
                if (isExternal) {
                    if (extTos != null) {
                        extTos.closeArchiveEntry();
                    }
                } else {
                    if (intTos != null) {
                        intTos.closeArchiveEntry();
                    }
                }
            }
            // Archiving finished
            try {
                tis.close();
            } catch (Exception ignore) {
            }
            if (intTos != null) {
                intTos.finish();
                try {
                    intTos.close();
                } catch (Exception ignore) {
                }
            }
            if (extTos != null) {
                extTos.finish();
                try {
                    extTos.close();
                } catch (Exception ignore) {
                }
            }

            // Encrypt created backups and generate checksum
            if (intSos != null) {
                // Encrypt backups
                Path[] newBackupFiles = mBackupItem.encrypt(intSos.getFiles().toArray(new Path[0]));
                for (Path file : newBackupFiles) {
                    mChecksum.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata.info.checksumAlgo, file));
                }
            }
            if (extSos != null) {
                // Encrypt backups
                Path[] newBackupFiles = mBackupItem.encrypt(extSos.getFiles().toArray(new Path[0]));
                for (Path file : newBackupFiles) {
                    mChecksum.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata.info.checksumAlgo, file));
                }
            }
        } catch (IOException e) {
            throw new BackupException("Could not backup data", e);
        }
    }

    private BackupMetadataV2 readPropFile() throws BackupException {
        try (InputStream is = mPropFile.openInputStream()) {
            BackupMetadataV2 metadataV2 = new BackupMetadataV2();
            Properties prop = new Properties();
            prop.load(is);
            metadataV2.label = prop.getProperty("app_label");
            metadataV2.packageName = mPackageName;
            metadataV2.versionName = prop.getProperty("app_version_name");
            metadataV2.versionCode = Integer.parseInt(prop.getProperty("app_version_code"));
            metadataV2.isSystem = "1".equals(prop.getProperty("app_is_system"));
            metadataV2.isSplitApk = false;
            metadataV2.splitConfigs = ArrayUtils.emptyArray(String.class);
            metadataV2.hasRules = false;
            metadataV2.backupTime = mBackupTime;
            metadataV2.crypto = CryptoUtils.MODE_NO_ENCRYPTION;  // We only support no encryption mode for TB backups
            metadataV2.apkName = mPackageName + "-" + prop.getProperty("app_apk_md5") + ".apk";
            metadataV2.userId = UserHandleHidden.myUserId();
            // Compression type
            String compressionType = prop.getProperty("app_apk_codec");
            if ("GZIP".equals(compressionType)) {
                metadataV2.tarType = TAR_GZIP;
            } else if ("BZIP2".equals(compressionType)) {
                metadataV2.tarType = TAR_BZIP2;
            } else throw new BackupException("Unsupported compression type: " + compressionType);
            // Flags
            metadataV2.flags = new BackupFlags(BackupFlags.BACKUP_MULTIPLE);
            try {
                mFilesToBeDeleted.add(getDataFile(Paths.trimPathExtension(mPropFile.getName()), metadataV2.tarType));
                // No error = data file exists
                metadataV2.flags.addFlag(BackupFlags.BACKUP_INT_DATA);
                if ("1".equals(prop.getProperty("has_external_data"))) {
                    metadataV2.flags.addFlag(BackupFlags.BACKUP_EXT_DATA);
                }
                metadataV2.flags.addFlag(BackupFlags.BACKUP_CACHE);
            } catch (FileNotFoundException ignore) {
            }
            try {
                mFilesToBeDeleted.add(getApkFile(metadataV2.apkName, metadataV2.tarType));
                // No error = APK file exists
                metadataV2.flags.addFlag(BackupFlags.BACKUP_APK_FILES);
            } catch (FileNotFoundException ignore) {
            }
            metadataV2.dataDirs = ConvertUtils.getDataDirs(mPackageName, mUserId, metadataV2.flags
                    .backupInternalData(), metadataV2.flags.backupExternalData(), false);
            metadataV2.keyStore = false;
            metadataV2.installer = Prefs.Installer.getInstallerPackageName();
            String base64Icon = prop.getProperty("app_gui_icon");
            if (base64Icon != null) {
                byte[] decodedBytes = Base64.decode(base64Icon, 0);
                mIcon = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            }
            return metadataV2;
        } catch (IOException e) {
            throw new BackupException("Could not read the prop file", e);
        }
    }

    @NonNull
    private Path getDataFile(String filePrefix, @TarUtils.TarType String tarType) throws FileNotFoundException {
        String filename = filePrefix + ".tar";
        if (TAR_BZIP2.equals(tarType)) filename += ".bz2";
        else if (TAR_ZSTD.equals(tarType)) filename += ".zst";
        else filename += ".gz";
        return mBackupLocation.findFile(filename);
    }

    @NonNull
    private Path getApkFile(String apkName, @TarUtils.TarType String tarType) throws FileNotFoundException {
        if (TAR_BZIP2.equals(tarType)) apkName += ".bz2";
        else if (TAR_ZSTD.equals(tarType)) apkName += ".zst";
        else apkName += ".gz";
        return mBackupLocation.findFile(apkName);
    }

    private void backupIcon() {
        if (mIcon == null) return;
        try {
            Path iconFile = mBackupItem.getIconFile();
            try (OutputStream outputStream = iconFile.openOutputStream()) {
                mIcon.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                outputStream.flush();
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not back up icon.");
        }
    }
}
