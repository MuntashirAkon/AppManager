// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import androidx.annotation.NonNull;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorOutputStream;
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
import java.util.Arrays;
import java.util.Properties;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.backup.BackupFiles;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
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

public class TBConvert extends Convert {
    public static final String TAG = TBConvert.class.getSimpleName();

    public static final String PATH_SUFFIX = "TitaniumBackup";

    private static final String INTERNAL_PREFIX = "data/data/";
    private static final String EXTERNAL_PREFIX = "data/data/.external.";

    @NonNull
    private final Context context = AppManager.getContext();
    private final Path backupLocation;
    private final int userHandle;
    private final Path propFile;
    private final String packageName;
    private final long backupTime;

    private Crypto crypto;
    private BackupFiles.Checksum checksum;
    private MetadataManager.Metadata sourceMetadata;
    private MetadataManager.Metadata destMetadata;
    private Path tmpBackupPath;
    private Bitmap icon;

    /**
     * A documentation about Titanium Backup is located at
     * <a href=https://github.com/MuntashirAkon/AppManager/issues/371#issuecomment-818491126>GH#371</a>.
     *
     * @param propFile Location to the properties file e.g. {@code /sdcard/TitaniumBackup/package.name-YYYYMMDD-HHMMSS.properties}
     */
    public TBConvert(@NonNull Path propFile) {
        this.propFile = propFile;
        this.backupLocation = propFile.getParentFile();
        this.userHandle = Users.myUserId();
        String dirtyName = propFile.getName();
        int idx = dirtyName.indexOf('-');
        if (idx == -1) this.packageName = null;
        else this.packageName = dirtyName.substring(0, idx);
        this.backupTime = propFile.lastModified();  // TODO: Grab from the file name
    }

    @Override
    public void convert() throws BackupException {
        if (this.packageName == null) {
            throw new BackupException("Could not read package name.");
        }
        // Source metadata
        sourceMetadata = new MetadataManager.Metadata();
        readPropFile();
        // Destination metadata
        destMetadata = new MetadataManager.Metadata(sourceMetadata);
        // Destination files will be encrypted by the default encryption method
        destMetadata.crypto = CryptoUtils.getMode();
        // Destination APK will be renamed
        destMetadata.apkName = "base.apk";
        // Destination compression type will be the default compression method
        destMetadata.tarType = ConvertUtils.getTarTypeFromPref();
        MetadataManager metadataManager = MetadataManager.getNewInstance();
        metadataManager.setMetadata(destMetadata);
        // Simulate a backup creation
        BackupFiles backupFiles;
        BackupFiles.BackupFile[] backupFileList;
        try {
            backupFiles = new BackupFiles(packageName, userHandle, new String[]{"TB"});
            backupFileList = backupFiles.getBackupPaths(true);
        } catch (IOException e) {
            throw new BackupException("Could not get backup files", e);
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
                metadataManager.writeMetadata(backupFile);
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
                if (!backupFile.commit()) {
                    throw new Exception("Unknown error occurred. This message should never be printed.");
                }
            } catch (Throwable th) {
                backupFile.cleanup();
                crypto.close();
                throw new BackupException(th.getClass().getName(), th);
            }
            return;
        }
    }

    @Override
    public String getPackageName() {
        return packageName;
    }

    private void backupApkFile() throws BackupException {
        // Decompress APK file
        Path baseApkFile;
        try {
            baseApkFile = new Path(context, IOUtils.getTempFile(destMetadata.apkName));
        } catch (IOException e) {
            throw new BackupException("Couldn't create temporary file to decompress APK file", e);
        }
        try (InputStream pis = getApkFile(sourceMetadata.apkName, sourceMetadata.tarType).openInputStream();
             BufferedInputStream bis = new BufferedInputStream(pis)) {
            CompressorInputStream is;
            if (TAR_GZIP.equals(sourceMetadata.tarType)) {
                is = new GzipCompressorInputStream(bis, true);
            } else if (TAR_BZIP2.equals(sourceMetadata.tarType)) {
                is = new BZip2CompressorInputStream(bis, true);
            } else {
                baseApkFile.delete();
                throw new BackupException("Invalid source compression type: " + sourceMetadata.tarType);
            }
            try (OutputStream fos = baseApkFile.openOutputStream()) {
                // The whole file is the APK
                IOUtils.copy(is, fos);
            } finally {
                is.close();
            }
        } catch (IOException e) {
            baseApkFile.delete();
            throw new BackupException("Couldn't decompress " + sourceMetadata.apkName, e);
        }
        // Get certificate checksums
        try {
            String[] checksums = ConvertUtils.getChecksumsFromApk(baseApkFile, destMetadata.checksumAlgo);
            for (int i = 0; i < checksums.length; ++i) {
                checksum.add(CERT_PREFIX + i, checksums[i]);
            }
        } catch (Exception ignore) {
        }
        // Backup APK file
        String sourceBackupFilePrefix = SOURCE_PREFIX + getExt(destMetadata.tarType);
        Path[] sourceFiles;
        try {
            sourceFiles = TarUtils.create(destMetadata.tarType, baseApkFile, tmpBackupPath, sourceBackupFilePrefix,
                    /* language=regexp */new String[]{".*\\.apk"}, null, null, false)
                    .toArray(new Path[0]);
        } catch (Throwable th) {
            throw new BackupException("APK files backup is requested but no APK files have been backed up.", th);
        } finally {
            baseApkFile.delete();
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
        Path dataFile;
        try {
            dataFile = getDataFile(IOUtils.trimExtension(this.propFile.getName()), sourceMetadata.tarType);
        } catch (FileNotFoundException e) {
            throw new BackupException("Could not get data file", e);
        }
        int i = 0;
        String intBackupFilePrefix = null;
        String extBackupFilePrefix = null;
        if (destMetadata.flags.backupInternalData()) {
            intBackupFilePrefix = DATA_PREFIX + (i++) + getExt(destMetadata.tarType);
        }
        if (destMetadata.flags.backupExternalData()) {
            extBackupFilePrefix = DATA_PREFIX + i + getExt(destMetadata.tarType);
        }
        try (BufferedInputStream bis = new BufferedInputStream(dataFile.openInputStream())) {
            CompressorInputStream cis;
            if (TAR_GZIP.equals(sourceMetadata.tarType)) {
                cis = new GzipCompressorInputStream(bis);
            } else if (TAR_BZIP2.equals(sourceMetadata.tarType)) {
                cis = new BZip2CompressorInputStream(bis);
            } else {
                throw new BackupException("Invalid compression type: " + destMetadata.tarType);
            }
            TarArchiveInputStream tis = new TarArchiveInputStream(cis);
            SplitOutputStream intSos = null, extSos = null;
            TarArchiveOutputStream intTos = null, extTos = null;
            if (intBackupFilePrefix != null) {
                intSos = new SplitOutputStream(tmpBackupPath, intBackupFilePrefix, DEFAULT_SPLIT_SIZE);
                BufferedOutputStream bos = new BufferedOutputStream(intSos);
                CompressorOutputStream cos;
                if (TAR_GZIP.equals(destMetadata.tarType)) {
                    cos = new GzipCompressorOutputStream(bos);
                } else if (TAR_BZIP2.equals(destMetadata.tarType)) {
                    cos = new BZip2CompressorOutputStream(bos);
                } else {
                    throw new BackupException("Invalid compression type: " + destMetadata.tarType);
                }
                intTos = new TarArchiveOutputStream(cos);
                intTos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                intTos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            }
            if (extBackupFilePrefix != null) {
                extSos = new SplitOutputStream(tmpBackupPath, extBackupFilePrefix, DEFAULT_SPLIT_SIZE);
                BufferedOutputStream bos = new BufferedOutputStream(extSos);
                CompressorOutputStream cos;
                if (TAR_GZIP.equals(destMetadata.tarType)) {
                    cos = new GzipCompressorOutputStream(bos);
                } else if (TAR_BZIP2.equals(destMetadata.tarType)) {
                    cos = new BZip2CompressorOutputStream(bos);
                } else {
                    throw new BackupException("Invalid compression type: " + destMetadata.tarType);
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
                fileName = fileName.replaceFirst((isExternal ? EXTERNAL_PREFIX : INTERNAL_PREFIX) + packageName + "/\\./", "");
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
                            IOUtils.copy(tis, extTos);
                        }
                    } else {
                        if (intTos != null) {
                            IOUtils.copy(tis, intTos);
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
                Path[] newBackupFiles = intSos.getFiles().toArray(new Path[0]);
                if (!crypto.encrypt(newBackupFiles)) {
                    throw new BackupException("Failed to encrypt " + Arrays.toString(newBackupFiles));
                }
                // Overwrite with the new files
                newBackupFiles = crypto.getNewFiles();
                for (Path file : newBackupFiles) {
                    checksum.add(file.getName(), DigestUtils.getHexDigest(destMetadata.checksumAlgo, file));
                }
            }
            if (extSos != null) {
                // Encrypt backups
                Path[] newBackupFiles = extSos.getFiles().toArray(new Path[0]);
                if (!crypto.encrypt(newBackupFiles)) {
                    throw new BackupException("Failed to encrypt " + Arrays.toString(newBackupFiles));
                }
                // Overwrite with the new files
                newBackupFiles = crypto.getNewFiles();
                for (Path file : newBackupFiles) {
                    checksum.add(file.getName(), DigestUtils.getHexDigest(destMetadata.checksumAlgo, file));
                }
            }
        } catch (IOException e) {
            throw new BackupException("Could not backup data", e);
        }
    }

    private void readPropFile() throws BackupException {
        try (InputStream is = this.propFile.openInputStream()) {
            Properties prop = new Properties();
            prop.load(is);
            sourceMetadata.label = prop.getProperty("app_label");
            sourceMetadata.packageName = this.packageName;
            sourceMetadata.versionName = prop.getProperty("app_version_name");
            sourceMetadata.versionCode = Integer.parseInt(prop.getProperty("app_version_code"));
            sourceMetadata.isSystem = "1".equals(prop.getProperty("app_is_system"));
            sourceMetadata.isSplitApk = false;
            sourceMetadata.splitConfigs = ArrayUtils.emptyArray(String.class);
            sourceMetadata.hasRules = false;
            sourceMetadata.backupTime = this.backupTime;
            sourceMetadata.crypto = CryptoUtils.MODE_NO_ENCRYPTION;  // We only support no encryption mode for TB backups
            sourceMetadata.apkName = this.packageName + "-" + prop.getProperty("app_apk_md5") + ".apk";
            sourceMetadata.userHandle = Users.myUserId();
            // Compression type
            String compressionType = prop.getProperty("app_apk_codec");
            if ("GZIP".equals(compressionType)) {
                sourceMetadata.tarType = TAR_GZIP;
            } else if ("BZIP2".equals(compressionType)) {
                sourceMetadata.tarType = TAR_BZIP2;
            } else throw new BackupException("Unsupported compression type: " + compressionType);
            // Flags
            sourceMetadata.flags = new BackupFlags(BackupFlags.BACKUP_MULTIPLE);
            try {
                getDataFile(IOUtils.trimExtension(this.propFile.getName()), sourceMetadata.tarType);
                // No error = data file exists
                sourceMetadata.flags.addFlag(BackupFlags.BACKUP_INT_DATA);
                if ("1".equals(prop.getProperty("has_external_data"))) {
                    sourceMetadata.flags.addFlag(BackupFlags.BACKUP_EXT_DATA);
                }
                sourceMetadata.flags.addFlag(BackupFlags.BACKUP_CACHE);
            } catch (FileNotFoundException ignore) {
            }
            try {
                getApkFile(sourceMetadata.apkName, sourceMetadata.tarType);
                // No error = APK file exists
                sourceMetadata.flags.addFlag(BackupFlags.BACKUP_APK_FILES);
            } catch (FileNotFoundException ignore) {
            }
            sourceMetadata.dataDirs = ConvertUtils.getDataDirs(this.packageName, this.userHandle, sourceMetadata.flags
                    .backupInternalData(), sourceMetadata.flags.backupExternalData(), false);
            sourceMetadata.keyStore = false;
            sourceMetadata.installer = AppPref.getString(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR);
            String base64Icon = prop.getProperty("app_gui_icon");
            if (base64Icon != null) {
                byte[] decodedBytes = Base64.decode(base64Icon, 0);
                icon = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            }
        } catch (IOException e) {
            throw new BackupException("Could not read the prop file", e);
        }
    }

    @NonNull
    private Path getDataFile(String filePrefix, @TarUtils.TarType String tarType) throws FileNotFoundException {
        String filename = filePrefix + ".tar";
        if (TAR_BZIP2.equals(tarType)) filename += ".bz2";
        else filename += ".gz";
        return this.backupLocation.findFile(filename);
    }

    @NonNull
    private Path getApkFile(String apkName, @TarUtils.TarType String tarType) throws FileNotFoundException {
        if (TAR_BZIP2.equals(tarType)) apkName += ".bz2";
        else apkName += ".gz";
        return this.backupLocation.findFile(apkName);
    }

    private void backupIcon() {
        if (icon == null) return;
        if (!tmpBackupPath.hasFile(ICON_FILE)) return;
        try {
            Path iconFile = tmpBackupPath.findFile(ICON_FILE);
            try (OutputStream outputStream = iconFile.openOutputStream()) {
                icon.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                outputStream.flush();
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not back up icon.");
        }
    }

}
