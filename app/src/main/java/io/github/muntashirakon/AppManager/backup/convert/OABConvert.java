// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import android.os.RemoteException;
import android.text.TextUtils;

import androidx.annotation.NonNull;

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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyInputStream;
import io.github.muntashirakon.io.SplitOutputStream;

import static io.github.muntashirakon.AppManager.backup.BackupManager.CERT_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.DATA_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.SOURCE_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.getExt;
import static io.github.muntashirakon.AppManager.utils.TarUtils.DEFAULT_SPLIT_SIZE;
import static io.github.muntashirakon.AppManager.utils.TarUtils.TAR_BZIP2;
import static io.github.muntashirakon.AppManager.utils.TarUtils.TAR_GZIP;

/**
 * A documentation about OAndBackup is located at
 * <a href=https://github.com/MuntashirAkon/AppManager/issues/371#issuecomment-818429082>GH#371</a>.
 */
class OABConvert implements Convert {
    public static final String TAG = OABConvert.class.getSimpleName();

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

    private final File backupLocation;
    private final String packageName;
    private final int userHandle;
    private final List<File> decryptedFiles = new ArrayList<>();

    private Crypto crypto;
    private BackupFiles.Checksum checksum;
    private MetadataManager.Metadata sourceMetadata;
    private MetadataManager.Metadata destMetadata;
    private File tmpBackupPath;

    /**
     * @param backupLocation E.g. {@code /sdcard/oandbackups/package.name}
     */
    public OABConvert(@NonNull File backupLocation) {
        this.backupLocation = backupLocation;
        // Last path component is the package name
        this.packageName = backupLocation.getName();
        this.userHandle = Users.getCurrentUserHandle();
    }

    @Override
    public void convert() throws BackupException {
        if (SPECIAL_BACKUPS.contains(packageName)) {
            throw new BackupException("Cannot convert special backup " + packageName);
        }
        // Source metadata
        sourceMetadata = new MetadataManager.Metadata();
        try {
            readLogFile();
        } catch (JSONException e) {
            throw new BackupException(e.getClass().getName(), e);
        }
        // Destination metadata
        destMetadata = new MetadataManager.Metadata(sourceMetadata);
        // Destination files will be encrypted by the default encryption method
        destMetadata.crypto = CryptoUtils.getMode();
        MetadataManager metadataManager = MetadataManager.getNewInstance();
        metadataManager.setMetadata(destMetadata);
        // Simulate a backup creation
        BackupFiles backupFiles = new BackupFiles(packageName, userHandle, new String[]{"OAndBackup"});
        BackupFiles.BackupFile[] backupFileList = backupFiles.getBackupPaths(true);
        for (BackupFiles.BackupFile backupFile : backupFileList) {
            // We're iterating over a singleton list
            try {
                tmpBackupPath = backupFile.getBackupPath();
                crypto = ConvertUtils.setupCrypto(destMetadata);
                checksum = new BackupFiles.Checksum(backupFile.getChecksumFile(CryptoUtils.MODE_NO_ENCRYPTION), "w");
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
                } catch (IOException | JSONException | RemoteException e) {
                    throw new BackupException("Failed to write metadata.");
                }
                // Store checksum for metadata
                checksum.add(MetadataManager.META_FILE, DigestUtils.getHexDigest(destMetadata.checksumAlgo, backupFile.getMetadataFile()));
                checksum.close();
                // Encrypt checksum
                ProxyFile checksumFile = backupFile.getChecksumFile(CryptoUtils.MODE_NO_ENCRYPTION);
                if (!crypto.encrypt(new ProxyFile[]{checksumFile})) {
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
                for (File file : decryptedFiles) {
                    Log.d(TAG, "Deleting " + file);
                    IOUtils.deleteSilently(new ProxyFile(file));
                }
                throw new BackupException(th.getClass().getName(), th);
            }
            return;
        }
    }

    private void readLogFile() throws JSONException, BackupException {
        ProxyFile logFile = new ProxyFile(backupLocation, packageName + ".log");
        String jsonString = IOUtils.getFileContent(logFile);
        if (TextUtils.isEmpty(jsonString)) throw new JSONException("Empty JSON string.");
        JSONObject jsonObject = new JSONObject(jsonString);
        sourceMetadata.label = jsonObject.getString("label");
        sourceMetadata.packageName = jsonObject.getString("packageName");
        sourceMetadata.versionName = jsonObject.getString("versionName");
        sourceMetadata.versionCode = jsonObject.getInt("versionCode");
        sourceMetadata.isSystem = jsonObject.optBoolean("isSystem");
        sourceMetadata.isSplitApk = false;
        sourceMetadata.splitConfigs = ArrayUtils.emptyArray(String.class);
        sourceMetadata.hasRules = false;
        sourceMetadata.backupTime = jsonObject.getLong("lastBackupMillis");
        sourceMetadata.crypto = jsonObject.optBoolean("isEncrypted") ? CryptoUtils.MODE_OPEN_PGP : CryptoUtils.MODE_NO_ENCRYPTION;
        sourceMetadata.apkName = new File(jsonObject.getString("sourceDir")).getName();
        // Flags
        sourceMetadata.flags = new BackupFlags(BackupFlags.BACKUP_MULTIPLE);
        int backupMode = jsonObject.optInt("backupMode", MODE_UNSET);
        if (backupMode == MODE_UNSET) {
            throw new BackupException("Destination doesn't contain any backup.");
        }
        if (backupMode == MODE_APK || backupMode == MODE_BOTH) {
            if (new ProxyFile(backupLocation, CryptoUtils.getAppropriateFilename(sourceMetadata.apkName,
                    sourceMetadata.crypto)).exists()) {
                sourceMetadata.flags.addFlag(BackupFlags.BACKUP_APK_FILES);
            } else {
                throw new BackupException("Destination doesn't contain any APK files.");
            }
        }
        if (backupMode == MODE_DATA || backupMode == MODE_BOTH) {
            boolean hasBackup = false;
            if (new ProxyFile(backupLocation, CryptoUtils.getAppropriateFilename(packageName + ".zip",
                    sourceMetadata.crypto)).exists()) {
                sourceMetadata.flags.addFlag(BackupFlags.BACKUP_INT_DATA);
                hasBackup = true;
            }
            if (new ProxyFile(new File(backupLocation, EXTERNAL_FILES), CryptoUtils.getAppropriateFilename(
                    packageName + ".zip", sourceMetadata.crypto)).exists()) {
                sourceMetadata.flags.addFlag(BackupFlags.BACKUP_EXT_DATA);
                hasBackup = true;
            }
            if (!hasBackup) {
                throw new BackupException("Destination doesn't contain any data files.");
            }
            sourceMetadata.flags.addFlag(BackupFlags.BACKUP_CACHE);
        }
        sourceMetadata.userHandle = Users.getCurrentUserHandle();
        sourceMetadata.dataDirs = ConvertUtils.getDataDirs(this.packageName, this.userHandle, sourceMetadata.flags
                .backupInternalData(), sourceMetadata.flags.backupExternalData(), false);
        sourceMetadata.tarType = ConvertUtils.getTarTypeFromPref();
        sourceMetadata.keyStore = false;
        sourceMetadata.installer = (String) AppPref.get(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR);
        sourceMetadata.version = 2;  // Old version is used so that we know that it needs permission fixes
    }

    private void backupApkFile() throws BackupException {
        File[] baseApkFiles = new File[]{new ProxyFile(backupLocation, CryptoUtils
                .getAppropriateFilename(sourceMetadata.apkName, sourceMetadata.crypto))};
        // Decrypt APK file if needed
        if (!crypto.decrypt(baseApkFiles)) {
            throw new BackupException("Failed to decrypt " + Arrays.toString(baseApkFiles));
        }
        // Get decrypted file
        if (crypto.getNewFiles().length > 0) {
            baseApkFiles = crypto.getNewFiles();
            decryptedFiles.addAll(Arrays.asList(baseApkFiles));
        }
        // baseApkFiles should be a singleton array
        if (baseApkFiles.length != 1) {
            throw new BackupException("Incorrect number of APK files: " + baseApkFiles.length);
        }
        File baseApkFile = baseApkFiles[0];
        // Get certificate checksums
        try {
            String[] checksums = ConvertUtils.getChecksumsFromApk(baseApkFile, destMetadata.checksumAlgo);
            for (int i = 0; i < checksums.length; ++i) {
                checksum.add(CERT_PREFIX + i, checksums[i]);
            }
        } catch (Exception ignore) {
        }
        // Backup APK file
        File sourceFile = new ProxyFile(tmpBackupPath, SOURCE_PREFIX + getExt(destMetadata.tarType));
        File[] sourceFiles;
        try {
            sourceFiles = TarUtils.create(destMetadata.tarType, baseApkFile, sourceFile, /* language=regexp */
                    new String[]{".*\\.apk"}, null, null, false).toArray(new File[0]);
        } catch (Throwable th) {
            throw new BackupException("APK files backup is requested but no APK files have been backed up.", th);
        }
        if (!crypto.encrypt(sourceFiles)) {
            throw new BackupException("Failed to encrypt " + Arrays.toString(sourceFiles));
        }
        // Overwrite with the new files
        sourceFiles = crypto.getNewFiles();
        for (File file : sourceFiles) {
            checksum.add(file.getName(), DigestUtils.getHexDigest(destMetadata.checksumAlgo, file));
        }
    }

    private void backupData() throws BackupException {
        List<File> dataFiles = new ArrayList<>(2);
        if (destMetadata.flags.backupInternalData()) {
            dataFiles.add(new ProxyFile(backupLocation, CryptoUtils.getAppropriateFilename(packageName + ".zip",
                    sourceMetadata.crypto)));
        }
        if (destMetadata.flags.backupExternalData()) {
            dataFiles.add(new ProxyFile(new File(backupLocation, EXTERNAL_FILES), CryptoUtils.getAppropriateFilename(
                    packageName + ".zip", sourceMetadata.crypto)));
        }
        int i = 0;
        File[] files;
        for (File dataFile : dataFiles) {
            files = new File[]{dataFile};
            // Decrypt APK file if needed
            if (!crypto.decrypt(files)) {
                throw new BackupException("Failed to decrypt " + Arrays.toString(files));
            }
            // Get decrypted file
            if (crypto.getNewFiles().length > 0) {
                files = crypto.getNewFiles();
                decryptedFiles.addAll(Arrays.asList(files));
            }
            // baseApkFiles should be a singleton array
            if (files.length != 1) {
                throw new BackupException("Incorrect number of APK files: " + files.length);
            }
            File backup = new ProxyFile(tmpBackupPath, DATA_PREFIX + (i++) + getExt(destMetadata.tarType));
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new ProxyInputStream(files[0])));
                 SplitOutputStream sos = new SplitOutputStream(backup, DEFAULT_SPLIT_SIZE);
                 BufferedOutputStream bos = new BufferedOutputStream(sos)) {
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
                            tmpFile = IOUtils.getTempFile();
                            try (OutputStream fos = new FileOutputStream(tmpFile)) {
                                IOUtils.copy(zis, fos);
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
                                IOUtils.copy(fis, tos);
                            } finally {
                                tmpFile.delete();
                            }
                        }
                        tos.closeArchiveEntry();
                    }
                    tos.finish();
                }
                // Encrypt backups
                File[] newBackupFiles = sos.getFiles().toArray(new File[0]);
                if (!crypto.encrypt(newBackupFiles)) {
                    throw new BackupException("Failed to encrypt " + Arrays.toString(newBackupFiles));
                }
                // Overwrite with the new files
                newBackupFiles = crypto.getNewFiles();
                for (File file : newBackupFiles) {
                    checksum.add(file.getName(), DigestUtils.getHexDigest(destMetadata.checksumAlgo, file));
                }
            } catch (IOException | RemoteException e) {
                throw new BackupException("Backup failed for " + dataFile, e);
            }
        }
    }
}
