/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.backup;

import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.net.Uri;
import android.os.Build;
import android.util.Pair;

import org.json.JSONException;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.PackageInfoCompat;

import androidx.core.content.pm.PermissionInfoCompat;
import io.github.muntashirakon.AppManager.misc.VMRuntime;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerShell;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.RulesImporter;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.server.common.OpEntry;
import io.github.muntashirakon.AppManager.server.common.PackageOps;
import io.github.muntashirakon.AppManager.servermanager.ApiSupporter;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.types.FreshFile;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class BackupManager {
    public static final String TAG = "BackupManager";

    private static final String EXT_DATA = "/Android/data/";
    private static final String EXT_MEDIA = "/Android/media/";
    private static final String EXT_OBB = "/Android/obb/";
    private static final String[] CACHE_DIRS = new String[]{"cache", "code_cache"};
    private static final String SOURCE_PREFIX = "source";
    private static final String DATA_PREFIX = "data";
    private static final String KEYSTORE_PREFIX = "keystore";
    private static final int KEYSTORE_PLACEHOLDER = -1000;

    static final String RULES_TSV = "rules.am.tsv";
    static final String PERMS_TSV = "perms.am.tsv";
    static final String CHECKSUMS_TXT = "checksums.txt";
    static final String CERT_PREFIX = "cert_";
    static final String MASTER_KEY = ".masterkey";

    @NonNull
    private static String getExt(@TarUtils.TarType String tarType) {
        if (TarUtils.TAR_BZIP2.equals(tarType)) return ".tar.bz2";
        else return ".tar.gz";
    }

    /**
     * @param packageName Package name of the app
     * @param flags       One or more of the {@link BackupFlags.BackupFlag}
     */
    @NonNull
    public static BackupManager getNewInstance(String packageName, int flags) {
        return new BackupManager(packageName, flags);
    }

    @NonNull
    private final String packageName;
    @NonNull
    private final MetadataManager metadataManager;
    @NonNull
    private final BackupFlags requestedFlags;
    @NonNull
    private final int[] userHandles;

    protected BackupManager(@NonNull String packageName, int flags) {
        this.packageName = packageName;
        metadataManager = MetadataManager.getNewInstance();
        requestedFlags = new BackupFlags(flags);
        if (requestedFlags.backupAllUsers()) {
            userHandles = Users.getUsersHandles();
        } else userHandles = new int[]{Users.getCurrentUserHandle()};
        Log.e(TAG, "Users: " + Arrays.toString(userHandles));
    }

    public boolean backup(@Nullable String[] backupNames) {
        if (requestedFlags.isEmpty()) {
            Log.e(BackupOp.TAG, "Backup is requested without any flags.");
            return false;
        }
        backupNames = getProcessedBackupNames(backupNames);
        BackupFiles[] backupFilesList = new BackupFiles[userHandles.length];
        for (int i = 0; i < userHandles.length; ++i) {
            backupFilesList[i] = new BackupFiles(packageName, userHandles[i], backupNames);
        }
        for (int i = 0; i < userHandles.length; ++i) {
            BackupFiles.BackupFile[] backupFiles = requestedFlags.backupMultiple() ?
                    backupFilesList[i].getFreshBackupPaths() :
                    backupFilesList[i].getBackupPaths(true);
            for (BackupFiles.BackupFile backupFile : backupFiles) {
                try (BackupOp backupOp = new BackupOp(backupFile, userHandles[i])) {
                    if (!backupOp.runBackup()) return false;
                } catch (BackupException e) {
                    Log.e(BackupOp.TAG, e.getMessage(), e);
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    private String[] getProcessedBackupNames(@Nullable String[] backupNames) {
        if (requestedFlags.backupMultiple()) {
            // Multiple backups requested
            if (backupNames == null) {
                // Create a singleton backupNames array with current time
                backupNames = new String[]{new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss",
                        Locale.ROOT).format(Calendar.getInstance().getTime())};
            }
            for (int i = 0; i < backupNames.length; ++i) {
                // Replace illegal characters
                backupNames[i] = backupNames[i].trim().replaceAll("[\\\\/?\"<>|\\s]+", "_");  // [\\/:?"<>|\s]
            }
            Log.e(TAG, "Backup names: " + Arrays.toString(backupNames));
            return backupNames;
        } else return null; // Overwrite existing backup
    }

    /**
     * Restore a single backup but could be for all users
     *
     * @param backupNames Backup names is a singleton array consisting of the full name of a backup.
     *                    Full name means backup name along with the user handle, ie., for user 0,
     *                    the full name of base backup is {@code 0} and the full name of another
     *                    backup {@code foo} is {@code 0_foo}.
     * @return {@code true} on success and {@code false} on failure
     */
    public boolean restore(@Nullable String[] backupNames) {
        if (requestedFlags.isEmpty()) {
            Log.e(RestoreOp.TAG, "Restore is requested without any flags.");
            return false;
        }
        if (backupNames != null && backupNames.length != 1) {
            Log.e(RestoreOp.TAG, "Restore is requested from more than one backups!");
            return false;
        }
        int backupUserHandle = -1;
        if (backupNames != null) {
            // Strip userHandle from backup name
            String backupName = BackupUtils.getShortBackupName(backupNames[0]);
            backupUserHandle = BackupUtils.getUserHandleFromBackupName(backupNames[0]);
            if (backupName != null) {
                // There's a backup name, not just user handle
                backupNames = new String[]{backupName};
            } else {
                // There's only user handle. Set backupNames to null to restore base backup goes
                // by the name
                backupNames = null;
            }
        }
        for (int userHandle : userHandles) {
            // Set backup userHandle to the userHandle we're working with.
            // This value is only set if backupNames is null or it consisted of only user handle
            if (backupUserHandle == -1) backupUserHandle = userHandle;
            BackupFiles backupFiles = new BackupFiles(packageName, backupUserHandle, backupNames);
            BackupFiles.BackupFile[] backupFileList = backupFiles.getBackupPaths(false);
            // Only restore from the first backup though we shouldn't have more than one backup.
            if (backupFileList.length > 0) {
                if (backupFileList.length > 1) {
                    Log.w(RestoreOp.TAG, "More than one backups found!");
                }
                try (RestoreOp restoreOp = new RestoreOp(backupFileList[0], userHandle)) {
                    if (!restoreOp.runRestore()) return false;
                } catch (BackupException e) {
                    e.printStackTrace();
                    Log.e(RestoreOp.TAG, e.getMessage(), e);
                    return false;
                }
            } else {
                Log.e(RestoreOp.TAG, "No backups found.");
            }
        }
        return true;
    }

    public boolean deleteBackup(@Nullable String[] backupNames) {
        if (backupNames == null) {
            // No backup names supplied, use user handle
            for (int userHandle : userHandles) {
                BackupFiles backupFiles = new BackupFiles(packageName, userHandle, null);
                BackupFiles.BackupFile[] backupFileList = backupFiles.getBackupPaths(false);
                for (BackupFiles.BackupFile backupFile : backupFileList) {
                    if (!backupFile.delete()) return false;
                }
            }
        } else {
            // backupNames is not null but that doesn't mean that it's not empty,
            // requested for only single backups
            for (String backupName : backupNames) {
                new BackupFiles.BackupFile(
                        new PrivilegedFile(BackupFiles.getPackagePath(packageName), backupName),
                        false
                ).delete();
            }
        }
        return true;
    }

    @Nullable
    private File[] getSourceFiles(@NonNull File backupPath) {
        return backupPath.listFiles((dir, name) -> name.startsWith(SOURCE_PREFIX));
    }

    @Nullable
    private File[] getKeyStoreFiles(@NonNull File backupPath) {
        return backupPath.listFiles((dir, name) -> name.startsWith(KEYSTORE_PREFIX));
    }

    @Nullable
    private File[] getDataFiles(@NonNull File backupPath, int index) {
        final String dataPrefix = DATA_PREFIX + index;
        return backupPath.listFiles((dir, name) -> name.startsWith(dataPrefix));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void deleteFiles(@NonNull FreshFile[] files) {
        for (FreshFile file : files) file.delete();
    }

    class BackupOp implements Closeable {
        static final String TAG = "BackupOp";

        @NonNull
        private final BackupFiles.BackupFile backupFile;
        @NonNull
        private final MetadataManager metadataManager;
        @NonNull
        private final BackupFlags backupFlags;
        @NonNull
        private final MetadataManager.Metadata metadata;
        @NonNull
        private final PackageInfo packageInfo;
        @NonNull
        private final ApplicationInfo applicationInfo;
        @NonNull
        private final PrivilegedFile tmpBackupPath;
        private final int userHandle;
        @NonNull
        private final Crypto crypto;
        @NonNull
        private final BackupFiles.Checksum checksum;

        BackupOp(@NonNull BackupFiles.BackupFile backupFile, int userHandle) throws BackupException {
            this.backupFile = backupFile;
            this.userHandle = userHandle;
            this.metadataManager = BackupManager.this.metadataManager;
            this.backupFlags = BackupManager.this.requestedFlags;
            this.tmpBackupPath = this.backupFile.getBackupPath();
            try {
                packageInfo = ApiSupporter.getInstance(LocalServer.getInstance()).getPackageInfo(
                        packageName, PackageManager.GET_META_DATA | PackageUtils.flagSigningInfo
                                | PackageManager.GET_PERMISSIONS, userHandle);
                this.applicationInfo = packageInfo.applicationInfo;
                // Override existing metadata
                this.metadata = metadataManager.setupMetadata(packageInfo, userHandle, backupFlags);
            } catch (Exception e) {
                this.backupFile.cleanup();
                throw new BackupException("Failed to setup metadata.", e);
            }
            try {
                // Setup crypto
                CryptoUtils.setupCrypto(this.metadata);
                this.crypto = CryptoUtils.getCrypto(metadata);
            } catch (CryptoException e) {
                this.backupFile.cleanup();
                throw new BackupException("Failed to get crypto " + metadata.crypto, e);
            }
            try {
                this.checksum = new BackupFiles.Checksum(
                        this.backupFile.getChecksumFile(CryptoUtils.MODE_NO_ENCRYPTION),
                        "w");
                String[] certChecksums = PackageUtils.getSigningCertChecksums(metadata.checksumAlgo, packageInfo, false);
                for (int i = 0; i < certChecksums.length; ++i) {
                    checksum.add(CERT_PREFIX + i, certChecksums[i]);
                }
            } catch (IOException e) {
                this.backupFile.cleanup();
                throw new BackupException("Failed to create checksum file.", e);
            }
        }

        @Override
        public void close() {
            crypto.close();
        }

        boolean runBackup() {
            // Fail backup if the app has items in Android KeyStore and backup isn't enabled
            if (backupFlags.backupData() && metadata.keyStore) {
                if (!(boolean) AppPref.get(AppPref.PrefKey.PREF_BACKUP_ANDROID_KEYSTORE_BOOL)) {
                    Log.e(TAG, "The app has keystore items and KeyStore backup isn't enabled.");
                    return backupFile.cleanup();
                }
            }
            try {
                // Backup source
                if (backupFlags.backupSource()) backupSource();
                // Backup data
                if (backupFlags.backupData()) {
                    backupData();
                    // Backup KeyStore
                    if (metadata.keyStore) backupKeyStore();
                }
                // Backup permissions
                if (backupFlags.backupPermissions()) backupPermissions();
                // Export rules
                if (metadata.hasRules) backupRules();
            } catch (BackupException e) {
                Log.e(TAG, e.getMessage(), e);
                return backupFile.cleanup();
            }
            // Set backup time
            metadata.backupTime = System.currentTimeMillis();
            // Write modified metadata
            metadataManager.setMetadata(metadata);
            try {
                metadataManager.writeMetadata(backupFile);
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Failed to write metadata.", e);
                return backupFile.cleanup();
            }
            // Store checksum for metadata
            checksum.add(MetadataManager.META_FILE, DigestUtils.getHexDigest(metadata.checksumAlgo, backupFile.getMetadataFile()));
            checksum.close();
            // Encrypt checksum
            File checksumFile = backupFile.getChecksumFile(CryptoUtils.MODE_NO_ENCRYPTION);
            if (!crypto.encrypt(new File[]{checksumFile})) {
                Log.e(TAG, "Failed to encrypt " + checksumFile.getName());
                return backupFile.cleanup();
            }
            // Replace current backup:
            // There's hardly any chance of getting a false here but checks are done anyway.
            if (backupFile.commit()) {
                return true;
            }
            Log.e(TAG, "Unknown error occurred. This message should never be printed.");
            return backupFile.cleanup();
        }

        private void backupSource() throws BackupException {
            final File dataAppPath = OsEnvironment.getDataAppDirectory();
            final File sourceFile = new File(tmpBackupPath, SOURCE_PREFIX + getExt(metadata.tarType) + ".");
            String sourceDir = PackageUtils.getSourceDir(applicationInfo);
            if (dataAppPath.getAbsolutePath().equals(sourceDir)) {
                // Backup only the apk file (no split apk support for this type of apk)
                sourceDir = new File(sourceDir, metadata.apkName).getAbsolutePath();
            }
            File[] sourceFiles = TarUtils.create(metadata.tarType, new File(sourceDir), sourceFile,
                    backupFlags.backupOnlyApk() ? new String[]{"*.apk"} : null, null, null);
            if (sourceFiles.length == 0) {
                throw new BackupException("Source backup is requested but no source directory has been backed up.");
            }
            if (!crypto.encrypt(sourceFiles)) {
                throw new BackupException("Failed to encrypt " + Arrays.toString(sourceFiles));
            }
            // Overwrite with the new files
            sourceFiles = crypto.getNewFiles();
            for (File file : sourceFiles) {
                checksum.add(file.getName(), DigestUtils.getHexDigest(metadata.checksumAlgo, file));
            }
        }

        private void backupData() throws BackupException {
            File sourceFile;
            File[] dataFiles;
            for (int i = 0; i < metadata.dataDirs.length; ++i) {
                sourceFile = new File(tmpBackupPath, DATA_PREFIX + i + getExt(metadata.tarType) + ".");
                dataFiles = TarUtils.create(metadata.tarType, new File(metadata.dataDirs[i]), sourceFile,
                        null, null, backupFlags.excludeCache() ? CACHE_DIRS : null);
                if (dataFiles.length == 0) {
                    throw new BackupException("Failed to backup data directory at " + metadata.dataDirs[i]);
                }
                if (!crypto.encrypt(dataFiles)) {
                    throw new BackupException("Failed to encrypt " + Arrays.toString(dataFiles));
                }
                // Overwrite with the new files
                dataFiles = crypto.getNewFiles();
                for (File file : dataFiles) {
                    checksum.add(file.getName(), DigestUtils.getHexDigest(metadata.checksumAlgo, file));
                }
            }
        }

        private void backupKeyStore() throws BackupException {  // Called only when the app has an keystore item
            PrivilegedFile keyStorePath = KeyStoreUtils.getKeyStorePath(userHandle);
            PrivilegedFile masterKeyFile = KeyStoreUtils.getMasterKey(userHandle);
            if (masterKeyFile.exists()) {
                // Master key exists, so take it's checksum to verify it during the restore
                checksum.add(MASTER_KEY, DigestUtils.getHexDigest(metadata.checksumAlgo,
                        IOUtils.getFileContent(masterKeyFile).getBytes()));
            }
            // Store the KeyStore files
            File cachePath;
            try {
                cachePath = IOUtils.getCachePath();
            } catch (IOException e) {
                throw new BackupException("Could not get cache path", e);
            }
            List<String> cachedKeyStoreFileNames = new ArrayList<>();
            for (String keyStoreFileName : KeyStoreUtils.getKeyStoreFiles(applicationInfo.uid, userHandle)) {
                String newFileName = Utils.replaceOnce(keyStoreFileName,
                        String.valueOf(applicationInfo.uid), String.valueOf(KEYSTORE_PLACEHOLDER));
                if (!RunnerUtils.cp(new File(keyStorePath, keyStoreFileName), new File(cachePath, newFileName))) {
                    throw new BackupException("Could not cache " + keyStoreFileName);
                }
                cachedKeyStoreFileNames.add(newFileName);
            }
            if (cachedKeyStoreFileNames.size() == 0) {
                throw new BackupException("There were some KeyStore items but they couldn't be cached before taking a backup.");
            }
            File keyStoreSavePath = new File(tmpBackupPath, KEYSTORE_PREFIX + getExt(metadata.tarType) + ".");
            File[] backedUpKeyStoreFiles = TarUtils.create(metadata.tarType, cachePath,
                    keyStoreSavePath, cachedKeyStoreFileNames.toArray(new String[0]), null, null);
            // Remove cache
            for (String name : cachedKeyStoreFileNames) {
                //noinspection ResultOfMethodCallIgnored
                new File(cachePath, name).delete();
            }
            if (backedUpKeyStoreFiles.length == 0) {
                throw new BackupException("Could not backup KeyStore item.");
            }
            if (!crypto.encrypt(backedUpKeyStoreFiles)) {
                throw new BackupException("Failed to encrypt " + Arrays.toString(backedUpKeyStoreFiles));
            }
            // Overwrite with the new files
            backedUpKeyStoreFiles = crypto.getNewFiles();
            for (File file : backedUpKeyStoreFiles) {
                checksum.add(file.getName(), DigestUtils.getHexDigest(metadata.checksumAlgo, file));
            }
        }

        private void backupPermissions() throws BackupException {
            File permsFile = backupFile.getPermsFile(CryptoUtils.MODE_NO_ENCRYPTION);
            @NonNull String[] permissions = ArrayUtils.defeatNullable(packageInfo.requestedPermissions);
            int[] permissionFlags = packageInfo.requestedPermissionsFlags;
            PackageManager pm = AppManager.getContext().getPackageManager();
            List<OpEntry> opEntries = new ArrayList<>();
            try {
                List<PackageOps> packageOpsList = new AppOpsService().getOpsForPackage(packageInfo.applicationInfo.uid, packageName, null, userHandle);
                if (packageOpsList.size() == 1) opEntries.addAll(packageOpsList.get(0).getOps());
            } catch (Exception ignore) {
            }
            PermissionInfo info;
            int basePermissionType;
            int protectionLevels;
            try (OutputStream outputStream = new FileOutputStream(permsFile)) {
                // Backup permissions
                for (int i = 0; i < permissions.length; ++i) {
                    try {
                        info = pm.getPermissionInfo(permissions[i], 0);
                        basePermissionType = PermissionInfoCompat.getProtection(info);
                        protectionLevels = PermissionInfoCompat.getProtectionFlags(info);
                        if (basePermissionType != PermissionInfo.PROTECTION_DANGEROUS
                                && (protectionLevels & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) == 0) {
                            // Don't include permissions that are neither dangerous nor development
                            continue;
                        }
                        outputStream.write(String.format("%s\t%s\t%s\t%s\n", packageName, permissions[i],
                                RulesStorageManager.Type.PERMISSION, (permissionFlags[i]
                                        & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0).getBytes());
                    } catch (PackageManager.NameNotFoundException ignore) {
                    }
                }
                // Backup app ops
                for (OpEntry entry : opEntries) {
                    outputStream.write(String.format("%s\t%s\t%s\t%s\n", packageName, entry.getOp(), RulesStorageManager.Type.APP_OP, entry.getMode()).getBytes());
                }
            } catch (IOException e) {
                throw new BackupException("Error during creating permission file.", e);
            }
            if (!crypto.encrypt(new File[]{permsFile})) {
                throw new BackupException("Failed to encrypt " + permsFile.getName());
            }
            // Overwrite with the new file
            permsFile = backupFile.getPermsFile(metadata.crypto);
            // Store checksum
            checksum.add(permsFile.getName(), DigestUtils.getHexDigest(metadata.checksumAlgo, permsFile));
        }

        private void backupRules() throws BackupException {
            File rulesFile = backupFile.getRulesFile(CryptoUtils.MODE_NO_ENCRYPTION);
            try (OutputStream outputStream = new FileOutputStream(rulesFile);
                 ComponentsBlocker cb = ComponentsBlocker.getInstance(packageName, userHandle)) {
                for (RulesStorageManager.Entry entry : cb.getAll()) {
                    // TODO: Do it in ComponentUtils
                    outputStream.write(String.format("%s\t%s\t%s\t%s\n", packageName, entry.name,
                            entry.type.name(), entry.extra).getBytes());
                }
            } catch (IOException e) {
                throw new BackupException("Rules backup is requested but encountered an error during fetching rules.", e);
            }
            if (!crypto.encrypt(new File[]{rulesFile})) {
                throw new BackupException("Failed to encrypt " + rulesFile.getName());
            }
            // Overwrite with the new file
            rulesFile = backupFile.getRulesFile(metadata.crypto);
            // Store checksum
            checksum.add(rulesFile.getName(), DigestUtils.getHexDigest(metadata.checksumAlgo, rulesFile));
        }
    }

    class RestoreOp implements Closeable {
        static final String TAG = "RestoreOp";

        @NonNull
        private final BackupFlags backupFlags;
        @NonNull
        private final BackupFlags requestedFlags;
        @NonNull
        private final MetadataManager.Metadata metadata;
        @NonNull
        private final PrivilegedFile backupPath;
        @NonNull
        private final BackupFiles.BackupFile backupFile;
        @NonNull
        private final ApiSupporter apiSupporter;
        @Nullable
        private PackageInfo packageInfo;
        @NonNull
        private final Crypto crypto;
        @NonNull
        private final BackupFiles.Checksum checksum;
        private final int userHandle;
        private boolean isInstalled;
        private final List<File> decryptedFiles = new ArrayList<>();

        RestoreOp(@NonNull BackupFiles.BackupFile backupFile, int userHandle) throws BackupException {
            this.requestedFlags = BackupManager.this.requestedFlags;
            this.backupFile = backupFile;
            this.backupPath = this.backupFile.getBackupPath();
            this.userHandle = userHandle;
            this.apiSupporter = ApiSupporter.getInstance(LocalServer.getInstance());
            try {
                metadataManager.readMetadata(this.backupFile);
                metadata = metadataManager.getMetadata();
                backupFlags = metadata.flags;
            } catch (JSONException e) {
                throw new BackupException("Failed to read metadata. Possibly due to malformed json file.", e);
            }
            // Setup crypto
            if (!CryptoUtils.isAvailable(metadata.crypto)) {
                throw new BackupException("Mode " + metadata.crypto + " is currently unavailable.");
            }
            try {
                crypto = CryptoUtils.getCrypto(metadata);
            } catch (CryptoException e) {
                throw new BackupException("Failed to get crypto " + metadata.crypto, e);
            }
            File checksumFile = this.backupFile.getChecksumFile(metadata.crypto);
            // Decrypt checksum
            if (!crypto.decrypt(new File[]{checksumFile})) {
                throw new BackupException("Failed to decrypt " + checksumFile.getName());
            }
            // Get checksums
            try {
                checksumFile = this.backupFile.getChecksumFile(CryptoUtils.MODE_NO_ENCRYPTION);
                decryptedFiles.addAll(Arrays.asList(crypto.getNewFiles()));
                this.checksum = new BackupFiles.Checksum(checksumFile, "r");
            } catch (IOException e) {
                this.backupFile.cleanup();
                throw new BackupException("Failed to get checksums.", e);
            }
            // Verify metadata
            if (!requestedFlags.skipSignatureCheck()) {
                File metadataFile = this.backupFile.getMetadataFile();
                String checksum = DigestUtils.getHexDigest(metadata.checksumAlgo, metadataFile);
                if (!checksum.equals(this.checksum.get(metadataFile.getName()))) {
                    throw new BackupException("Couldn't verify permission file." +
                            "\nFile: " + metadataFile +
                            "\nFound: " + checksum +
                            "\nRequired: " + this.checksum.get(metadataFile.getName()));
                }
            }
            // Check user handle
            if (metadata.userHandle != userHandle) {
                Log.w(TAG, "Using different user handle.");
            }
            // Get package info
            packageInfo = null;
            try {
                packageInfo = apiSupporter.getPackageInfo(packageName, PackageUtils.flagSigningInfo, userHandle);
            } catch (Exception ignore) {
            }
            isInstalled = packageInfo != null;
        }

        @Override
        public void close() {
            Log.d(TAG, "Close called");
            crypto.close();
            for (File file : decryptedFiles) {
                Log.d(TAG, "Deleting " + file);
                IOUtils.deleteSilently(new PrivilegedFile(file));
            }
        }

        boolean runRestore() {
            try {
                if (requestedFlags.backupData() && metadata.keyStore
                        && !requestedFlags.skipSignatureCheck()) {
                    // Check checksum of master key first
                    checkMasterKey();
                }
                if (requestedFlags.backupSource()) restoreSource();
                if (requestedFlags.backupData()) {
                    restoreData();
                    if (metadata.keyStore) restoreKeyStore();
                }
                if (requestedFlags.backupPermissions()) restorePermissions();
                if (requestedFlags.backupRules()) restoreRules();
            } catch (BackupException e) {
                Log.e(TAG, e.getMessage(), e);
                return false;
            }
            return true;
        }

        private void checkMasterKey() throws BackupException {
            String oldChecksum = checksum.get(MASTER_KEY);
            PrivilegedFile masterKey = KeyStoreUtils.getMasterKey(userHandle);
            if (!masterKey.exists()) {
                if (oldChecksum == null) return;
                else throw new BackupException("Master key existed when the checksum was made but now it doesn't.");
            }
            if (oldChecksum == null) {
                throw new BackupException("Master key exists but it didn't exist when the backup was made.");
            }
            String newChecksum = DigestUtils.getHexDigest(metadata.checksumAlgo,
                    IOUtils.getFileContent(masterKey).getBytes());
            if (!newChecksum.equals(oldChecksum)) {
                throw new BackupException("Checksums for master key did not match.");
            }
        }

        private void restoreSource() throws BackupException {
            if (!backupFlags.backupSource()) {
                throw new BackupException("Source restore is requested but backup doesn't contain any source files.");
            }
            File[] backupSourceFiles = getSourceFiles(backupPath);
            if (backupSourceFiles == null || backupSourceFiles.length == 0) {
                // No source backup found
                throw new BackupException("Source restore is requested but there are no source files.");
            }
            boolean reinstallNeeded = false;
            if (packageInfo != null) {
                // Check signature of the installed app
                List<String> certChecksumList = Arrays.asList(PackageUtils.getSigningCertChecksums(metadata.checksumAlgo, packageInfo, false));
                String[] certChecksums = BackupFiles.Checksum.getCertChecksums(checksum);
                boolean isVerified = true;
                for (String checksum : certChecksums) {
                    if (certChecksumList.contains(checksum)) continue;
                    isVerified = false;
                    if (!requestedFlags.skipSignatureCheck()) {
                        throw new BackupException("Signing info verification failed." +
                                "\nInstalled: " + certChecksumList.toString() +
                                "\nBackup: " + Arrays.toString(certChecksums));
                    }
                }
                if (!isVerified) {
                    // Signature verification failed but still here because signature check is disabled.
                    // The only way to restore is to reinstall the app
                    reinstallNeeded = true;
                } else if (PackageInfoCompat.getLongVersionCode(packageInfo) > metadata.versionCode) {
                    // Installed package has higher version code. The only way to downgrade is to
                    // reinstall the package.
                    reinstallNeeded = true;
                }
            }
            if (!requestedFlags.skipSignatureCheck()) {
                String checksum;
                for (File file : backupSourceFiles) {
                    checksum = DigestUtils.getHexDigest(metadata.checksumAlgo, file);
                    if (!checksum.equals(this.checksum.get(file.getName()))) {
                        throw new BackupException("Source file verification failed." +
                                "\nFile: " + file +
                                "\nFound: " + checksum +
                                "\nRequired: " + this.checksum.get(file.getName()));
                    }
                }
            }
            if (reinstallNeeded) {
                // A complete reinstall needed, first uninstall the package with -k and then install
                // the package again with -r
                if (!RunnerUtils.uninstallPackageWithoutData(packageName, userHandle).isSuccessful()) {
                    throw new BackupException("An uninstall was necessary but couldn't perform it.");
                }
            }
            // Setup package staging directory
            File packageStagingDirectory = PackageUtils.PACKAGE_STAGING_DIRECTORY;
            if (!RunnerUtils.fileExists(packageStagingDirectory)) {
                packageStagingDirectory = backupPath;
            }
            // Setup apk files, including split apk
            FreshFile baseApk = new FreshFile(packageStagingDirectory, metadata.apkName);
            final int splitCount = metadata.splitConfigs.length;
            String[] allApkNames = new String[splitCount + 1];
            FreshFile[] allApks = new FreshFile[splitCount + 1];
            allApks[0] = baseApk;
            allApkNames[0] = metadata.apkName;
            for (int i = 1; i < allApkNames.length; ++i) {
                allApkNames[i] = metadata.splitConfigs[i - 1];
                allApks[i] = new FreshFile(packageStagingDirectory, allApkNames[i]);
            }
            // Decrypt sources
            if (!crypto.decrypt(backupSourceFiles)) {
                throw new BackupException("Failed to decrypt " + Arrays.toString(backupSourceFiles));
            }
            // Get decrypted file
            if (crypto.getNewFiles().length > 0) {
                backupSourceFiles = crypto.getNewFiles();
                decryptedFiles.addAll(Arrays.asList(backupSourceFiles));
            }
            // Extract apk files to the package staging directory
            if (!TarUtils.extract(metadata.tarType, backupSourceFiles, packageStagingDirectory, allApkNames, null)) {
                throw new BackupException("Failed to extract the apk file(s).");
            }
            // A normal update will do it now
            PackageInstallerShell packageInstaller = new PackageInstallerShell(userHandle);
            if (!packageInstaller.install(allApks, packageName)) {
                deleteFiles(allApks);
                throw new BackupException("A (re)install was necessary but couldn't perform it.");
            }
            deleteFiles(allApks);  // Clean up apk files
            // Get package info, again
            try {
                packageInfo = apiSupporter.getPackageInfo(packageName, PackageUtils.flagSigningInfo, userHandle);
                isInstalled = true;
            } catch (Exception e) {
                throw new BackupException("Apparently the install wasn't complete in the previous section.", e);
            }
            // Get instruction set
            // TODO(10/9/20): Investigate support for unmatched instruction set as well
            final String instructionSet = VMRuntime.getInstructionSet(Build.SUPPORTED_ABIS[0]);
            final File dataAppPath = OsEnvironment.getDataAppDirectory();
            final File sourceDir = new File(PackageUtils.getSourceDir(packageInfo.applicationInfo));
            // Restore source directory only if instruction set is matched or app path is not /data/app
            // Or only apk restoring is requested
            if (!requestedFlags.backupOnlyApk()  // Only apk restoring is not requested
                    && metadata.instructionSet.equals(instructionSet)  // Instruction set matched
                    && !dataAppPath.equals(sourceDir)) {  // Path is not /data/app
                // Restore source: Get installed source directory and copy backups directly
                if (!TarUtils.extract(metadata.tarType, backupSourceFiles, sourceDir, null, null)) {
                    throw new BackupException("Failed to restore the source files.");
                }
                // Restore permissions
                Runner.runCommand(new String[]{"restorecon", "-R", sourceDir.getAbsolutePath()});
            } else {
                Log.w(TAG, "Skipped restoring files due to mismatched architecture or the path is /data/app or only apk restoring is requested.");
            }
        }

        private void restoreKeyStore() throws BackupException {
            if (packageInfo == null) {
                throw new BackupException("KeyStore restore is requested but the app isn't installed.");
            }
            File[] keyStoreFiles = getKeyStoreFiles(backupPath);
            if (keyStoreFiles == null || keyStoreFiles.length == 0) {
                throw new BackupException("KeyStore files should've existed but they didn't");
            }
            // Decrypt sources
            if (!crypto.decrypt(keyStoreFiles)) {
                throw new BackupException("Failed to decrypt " + Arrays.toString(keyStoreFiles));
            }
            // Get decrypted file
            if (crypto.getNewFiles().length > 0) {
                keyStoreFiles = crypto.getNewFiles();
                decryptedFiles.addAll(Arrays.asList(keyStoreFiles));
            }
            // Restore KeyStore files
            PrivilegedFile keyStorePath = KeyStoreUtils.getKeyStorePath(userHandle);
            if (!TarUtils.extract(metadata.tarType, keyStoreFiles, keyStorePath, null, null)) {
                throw new BackupException("Failed to restore the KeyStore files.");
            }
            // Rename files
            int uid = packageInfo.applicationInfo.uid;
            List<String> keyStoreFileNames = KeyStoreUtils.getKeyStoreFiles(KEYSTORE_PLACEHOLDER, userHandle);
            for (String keyStoreFileName : keyStoreFileNames) {
                if (!RunnerUtils.mv(new File(keyStorePath, keyStoreFileName), new File(keyStorePath,
                        Utils.replaceOnce(keyStoreFileName, String.valueOf(KEYSTORE_PLACEHOLDER),
                                String.valueOf(uid))))) {
                    throw new BackupException("Failed to rename KeyStore files");
                }
            }
            // TODO Restore permissions
            Runner.runCommand(new String[]{"restorecon", "-R", keyStorePath.getAbsolutePath()});
        }

        @SuppressLint("SdCardPath")
        private void restoreData() throws BackupException {
            // Data restore is requested: Data restore is only possible if the app is actually
            // installed. So, check if it's installed first.
            if (packageInfo == null) {
                throw new BackupException("Data restore is requested but the app isn't installed.");
            }
            File[] dataFiles;
            if (!requestedFlags.skipSignatureCheck()) {
                // Verify integrity of the data backups
                String checksum;
                for (int i = 0; i < metadata.dataDirs.length; ++i) {
                    dataFiles = getDataFiles(backupPath, i);
                    if (dataFiles == null || dataFiles.length == 0) {
                        throw new BackupException("Data restore is requested but there are no data files for index " + i + ".");
                    }
                    for (File file : dataFiles) {
                        checksum = DigestUtils.getHexDigest(metadata.checksumAlgo, file);
                        if (!checksum.equals(this.checksum.get(file.getName()))) {
                            throw new BackupException("Data file verification failed for index " + i + "." +
                                    "\nFile: " + file +
                                    "\nFound: " + checksum +
                                    "\nRequired: " + this.checksum.get(file.getName()));
                        }
                    }
                }
            }
            // Force stop app
            RunnerUtils.forceStopPackage(packageName, RunnerUtils.USER_ALL);
            // Clear app data
            RunnerUtils.clearPackageData(packageName, userHandle);
            // Restore backups
            String dataSource;
            boolean isExternal;
            for (int i = 0; i < metadata.dataDirs.length; ++i) {
                dataSource = Utils.replaceOnce(metadata.dataDirs[i], "/" + metadata.userHandle + "/", "/" + userHandle + "/");
                dataFiles = getDataFiles(backupPath, i);
                Pair<Integer, Integer> uidAndGid = null;
                if (RunnerUtils.fileExists(dataSource)) {
                    uidAndGid = BackupUtils.getUidAndGid(dataSource, packageInfo.applicationInfo.uid);
                }
                if (dataFiles == null || dataFiles.length == 0) {
                    throw new BackupException("Data restore is requested but there are no data files for index " + i + ".");
                }
                // External storage checks
                if (dataSource.startsWith("/storage") || dataSource.startsWith("/sdcard")) {
                    isExternal = true;
                    // Skip if external data restore is not requested
                    if (!requestedFlags.backupExtData() && dataSource.contains(EXT_DATA))
                        continue;
                    // Skip if media/obb restore not requested
                    if (!requestedFlags.backupMediaObb() && (dataSource.contains(EXT_MEDIA)
                            || dataSource.contains(EXT_OBB))) continue;
                } else isExternal = false;
                // Fix problem accessing external directory in Android API < 23
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    if (dataSource.contains("/storage/emulated/")) {
                        dataSource = dataSource.replace("/storage/emulated/", "/mnt/shell/emulated/");
                    }
                }
                // Create data folder if not exists
                PrivilegedFile dataSourceFile = new PrivilegedFile(dataSource);
                if (!dataSourceFile.exists()) {
                    // FIXME(10/9/20): Check if the media is mounted and readable before running
                    //  mkdir, otherwise it may create a folder to a path that will be gone
                    //  after a restart
                    if (!dataSourceFile.mkdirs()) {
                        throw new BackupException("Failed to create data folder for index " + i + ".");
                    }
                }
                // Decrypt data
                if (!crypto.decrypt(dataFiles)) {
                    throw new BackupException("Failed to decrypt " + Arrays.toString(dataFiles));
                }
                // Get decrypted files
                if (crypto.getNewFiles().length > 0) {
                    dataFiles = crypto.getNewFiles();
                    decryptedFiles.addAll(Arrays.asList(dataFiles));
                }
                // Extract data to the data directory
                if (!TarUtils.extract(metadata.tarType, dataFiles, dataSourceFile,
                        null, requestedFlags.excludeCache() ? CACHE_DIRS : null)) {
                    throw new BackupException("Failed to restore data files for index " + i + ".");
                }
                // Fix UID and GID
                if (uidAndGid != null && !Runner.runCommand(String.format(Runner.TOYBOX + " chown -R %d:%d \"%s\"", uidAndGid.first, uidAndGid.second, dataSource)).isSuccessful()) {
                    throw new BackupException("Failed to restore ownership info for index " + i + ".");
                }
                // Restore permissions
                if (!isExternal) Runner.runCommand(new String[]{"restorecon", "-R", dataSource});
            }
        }

        private void restorePermissions() throws BackupException {
            // Apply rules
            if (!isInstalled) {
                throw new BackupException("Permission restore is requested but the app isn't installed.");
            }
            File permsFile = backupFile.getPermsFile(metadata.crypto);
            if (permsFile.exists()) {
                if (!requestedFlags.skipSignatureCheck()) {
                    String checksum = DigestUtils.getHexDigest(metadata.checksumAlgo, permsFile);
                    if (!checksum.equals(this.checksum.get(permsFile.getName()))) {
                        throw new BackupException("Couldn't verify permission file." +
                                "\nFile: " + permsFile +
                                "\nFound: " + checksum +
                                "\nRequired: " + this.checksum.get(permsFile.getName()));
                    }
                }
                // Decrypt permission file
                if (!crypto.decrypt(new File[]{permsFile})) {
                    throw new BackupException("Failed to decrypt " + permsFile.getName());
                }
                // Get decrypted file
                permsFile = backupFile.getPermsFile(CryptoUtils.MODE_NO_ENCRYPTION);
                decryptedFiles.addAll(Arrays.asList(crypto.getNewFiles()));
                try (RulesImporter importer = new RulesImporter(Arrays.asList(RulesStorageManager.Type.values()), new int[]{userHandle})) {
                    importer.addRulesFromUri(Uri.fromFile(permsFile));
                    importer.setPackagesToImport(Collections.singletonList(packageName));
                    importer.applyRules();
                } catch (IOException e) {
                    throw new BackupException("Failed to restore permissions.", e);
                }
            } // else there are no permissions, just skip
        }

        private void restoreRules() throws BackupException {
            // Apply rules
            if (!isInstalled) {
                throw new BackupException("Rules restore is requested but the app isn't installed.");
            }
            File rulesFile = backupFile.getRulesFile(metadata.crypto);
            if (rulesFile.exists()) {
                if (!requestedFlags.skipSignatureCheck()) {
                    String checksum = DigestUtils.getHexDigest(metadata.checksumAlgo, rulesFile);
                    if (!checksum.equals(this.checksum.get(rulesFile.getName()))) {
                        throw new BackupException("Couldn't verify permission file." +
                                "\nFile: " + rulesFile +
                                "\nFound: " + checksum +
                                "\nRequired: " + this.checksum.get(rulesFile.getName()));
                    }
                }
                // Decrypt rules file
                if (!crypto.decrypt(new File[]{rulesFile})) {
                    throw new BackupException("Failed to decrypt " + rulesFile.getName());
                }
                // Get decrypted file
                rulesFile = backupFile.getRulesFile(CryptoUtils.MODE_NO_ENCRYPTION);
                decryptedFiles.addAll(Arrays.asList(crypto.getNewFiles()));
                try (RulesImporter importer = new RulesImporter(Arrays.asList(RulesStorageManager.Type.values()), new int[]{userHandle})) {
                    importer.addRulesFromUri(Uri.fromFile(rulesFile));
                    importer.setPackagesToImport(Collections.singletonList(packageName));
                    importer.applyRules();
                } catch (IOException e) {
                    throw new BackupException("Failed to restore rules file.", e);
                }
            } else if (metadata.hasRules) {
                throw new BackupException("Rules file is missing.");
            } // else there are no rules, just skip
        }
    }

    public static class BackupException extends Throwable {
        @NonNull
        private final String detailMessage;

        public BackupException(@NonNull String message) {
            super(message);
            this.detailMessage = message;
        }

        public BackupException(@NonNull String message, @NonNull Throwable cause) {
            super(message, cause);
            detailMessage = message;
        }

        @NonNull
        @Override
        public String getMessage() {
            return detailMessage;
        }
    }
}
