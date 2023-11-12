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
import java.util.Properties;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.backup.BackupFiles;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.crypto.Crypto;
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

    private Crypto mCrypto;
    private BackupFiles.Checksum mChecksum;
    private MetadataManager.Metadata mSourceMetadata;
    private MetadataManager.Metadata mDestMetadata;
    private Path mTempBackupPath;
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
        mSourceMetadata = new MetadataManager.Metadata();
        readPropFile();
        // Destination metadata
        mDestMetadata = new MetadataManager.Metadata(mSourceMetadata);
        // Destination files will be encrypted by the default encryption method
        mDestMetadata.crypto = CryptoUtils.getMode();
        // Destination APK will be renamed
        mDestMetadata.apkName = "base.apk";
        // Destination compression type will be the default compression method
        mDestMetadata.tarType = Prefs.BackupRestore.getCompressionMethod();
        MetadataManager metadataManager = MetadataManager.getNewInstance();
        metadataManager.setMetadata(mDestMetadata);
        // Simulate a backup creation
        BackupFiles backupFiles;
        BackupFiles.BackupFile[] backupFileList;
        try {
            backupFiles = new BackupFiles(mPackageName, mUserId, new String[]{"TB"});
            backupFileList = backupFiles.getBackupPaths(true);
        } catch (IOException e) {
            throw new BackupException("Could not get backup files", e);
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
                    throw new BackupException("Failed to encrypt checksums.txt");
                }
                // Replace current backup:
                // There's hardly any chance of getting a false here but checks are done anyway.
                try {
                    backupFile.commit();
                } catch (Exception e) {
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
                if (backupSuccess) {
                    BackupUtils.putBackupToDbAndBroadcast(ContextUtils.getContext(), mDestMetadata);
                }
            }
            return;
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
        Path baseApkFile = FileUtils.getTempPath(mPackageName, mDestMetadata.apkName);
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
                            /* language=regexp */new String[]{".*\\.apk"}, null, null, false)
                    .toArray(new Path[0]);
        } catch (Throwable th) {
            throw new BackupException("APK files backup is requested but no APK files have been backed up.", th);
        } finally {
            baseApkFile.requireParent().delete();
        }
        // Overwrite with the new files
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
        Path dataFile;
        try {
            dataFile = getDataFile(Paths.trimPathExtension(mPropFile.getName()), mSourceMetadata.tarType);
        } catch (FileNotFoundException e) {
            throw new BackupException("Could not get data file", e);
        }
        int i = 0;
        String intBackupFilePrefix = null;
        String extBackupFilePrefix = null;
        if (mDestMetadata.flags.backupInternalData()) {
            intBackupFilePrefix = DATA_PREFIX + (i++) + getExt(mDestMetadata.tarType);
        }
        if (mDestMetadata.flags.backupExternalData()) {
            extBackupFilePrefix = DATA_PREFIX + i + getExt(mDestMetadata.tarType);
        }
        try (BufferedInputStream bis = new BufferedInputStream(dataFile.openInputStream())) {
            CompressorInputStream cis;
            if (TAR_GZIP.equals(mSourceMetadata.tarType)) {
                cis = new GzipCompressorInputStream(bis);
            } else if (TAR_BZIP2.equals(mSourceMetadata.tarType)) {
                cis = new BZip2CompressorInputStream(bis);
            } else {
                throw new BackupException("Invalid compression type: " + mDestMetadata.tarType);
            }
            TarArchiveInputStream tis = new TarArchiveInputStream(cis);
            SplitOutputStream intSos = null, extSos = null;
            TarArchiveOutputStream intTos = null, extTos = null;
            if (intBackupFilePrefix != null) {
                intSos = new SplitOutputStream(mTempBackupPath, intBackupFilePrefix, DEFAULT_SPLIT_SIZE);
                BufferedOutputStream bos = new BufferedOutputStream(intSos);
                OutputStream cos;
                if (TAR_GZIP.equals(mDestMetadata.tarType)) {
                    cos = new GzipCompressorOutputStream(bos);
                } else if (TAR_BZIP2.equals(mDestMetadata.tarType)) {
                    cos = new BZip2CompressorOutputStream(bos);
                } else if (TAR_ZSTD.equals(mDestMetadata.tarType)) {
                    cos = new ZstdOutputStream(bos);
                } else {
                    throw new BackupException("Invalid compression type: " + mDestMetadata.tarType);
                }
                intTos = new TarArchiveOutputStream(cos);
                intTos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                intTos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            }
            if (extBackupFilePrefix != null) {
                extSos = new SplitOutputStream(mTempBackupPath, extBackupFilePrefix, DEFAULT_SPLIT_SIZE);
                BufferedOutputStream bos = new BufferedOutputStream(extSos);
                OutputStream cos;
                if (TAR_GZIP.equals(mDestMetadata.tarType)) {
                    cos = new GzipCompressorOutputStream(bos);
                } else if (TAR_BZIP2.equals(mDestMetadata.tarType)) {
                    cos = new BZip2CompressorOutputStream(bos);
                } else if (TAR_ZSTD.equals(mDestMetadata.tarType)) {
                    cos = new ZstdOutputStream(bos);
                } else {
                    throw new BackupException("Invalid compression type: " + mDestMetadata.tarType);
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
                if (fileName.equals("")) continue;
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
                Path[] newBackupFiles = encrypt(intSos.getFiles().toArray(new Path[0]));
                for (Path file : newBackupFiles) {
                    mChecksum.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata.checksumAlgo, file));
                }
            }
            if (extSos != null) {
                // Encrypt backups
                Path[] newBackupFiles = encrypt(extSos.getFiles().toArray(new Path[0]));
                for (Path file : newBackupFiles) {
                    mChecksum.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata.checksumAlgo, file));
                }
            }
        } catch (IOException e) {
            throw new BackupException("Could not backup data", e);
        }
    }

    private void readPropFile() throws BackupException {
        try (InputStream is = mPropFile.openInputStream()) {
            Properties prop = new Properties();
            prop.load(is);
            mSourceMetadata.label = prop.getProperty("app_label");
            mSourceMetadata.packageName = mPackageName;
            mSourceMetadata.versionName = prop.getProperty("app_version_name");
            mSourceMetadata.versionCode = Integer.parseInt(prop.getProperty("app_version_code"));
            mSourceMetadata.isSystem = "1".equals(prop.getProperty("app_is_system"));
            mSourceMetadata.isSplitApk = false;
            mSourceMetadata.splitConfigs = ArrayUtils.emptyArray(String.class);
            mSourceMetadata.hasRules = false;
            mSourceMetadata.backupTime = mBackupTime;
            mSourceMetadata.crypto = CryptoUtils.MODE_NO_ENCRYPTION;  // We only support no encryption mode for TB backups
            mSourceMetadata.apkName = mPackageName + "-" + prop.getProperty("app_apk_md5") + ".apk";
            mSourceMetadata.userHandle = UserHandleHidden.myUserId();
            // Compression type
            String compressionType = prop.getProperty("app_apk_codec");
            if ("GZIP".equals(compressionType)) {
                mSourceMetadata.tarType = TAR_GZIP;
            } else if ("BZIP2".equals(compressionType)) {
                mSourceMetadata.tarType = TAR_BZIP2;
            } else throw new BackupException("Unsupported compression type: " + compressionType);
            // Flags
            mSourceMetadata.flags = new BackupFlags(BackupFlags.BACKUP_MULTIPLE);
            try {
                mFilesToBeDeleted.add(getDataFile(Paths.trimPathExtension(mPropFile.getName()), mSourceMetadata.tarType));
                // No error = data file exists
                mSourceMetadata.flags.addFlag(BackupFlags.BACKUP_INT_DATA);
                if ("1".equals(prop.getProperty("has_external_data"))) {
                    mSourceMetadata.flags.addFlag(BackupFlags.BACKUP_EXT_DATA);
                }
                mSourceMetadata.flags.addFlag(BackupFlags.BACKUP_CACHE);
            } catch (FileNotFoundException ignore) {
            }
            try {
                mFilesToBeDeleted.add(getApkFile(mSourceMetadata.apkName, mSourceMetadata.tarType));
                // No error = APK file exists
                mSourceMetadata.flags.addFlag(BackupFlags.BACKUP_APK_FILES);
            } catch (FileNotFoundException ignore) {
            }
            mSourceMetadata.dataDirs = ConvertUtils.getDataDirs(mPackageName, mUserId, mSourceMetadata.flags
                    .backupInternalData(), mSourceMetadata.flags.backupExternalData(), false);
            mSourceMetadata.keyStore = false;
            mSourceMetadata.installer = Prefs.Installer.getInstallerPackageName();
            String base64Icon = prop.getProperty("app_gui_icon");
            if (base64Icon != null) {
                byte[] decodedBytes = Base64.decode(base64Icon, 0);
                mIcon = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            }
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
            Path iconFile = mTempBackupPath.findOrCreateFile(ICON_FILE, null);
            try (OutputStream outputStream = iconFile.openOutputStream()) {
                mIcon.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                outputStream.flush();
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not back up icon.");
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
