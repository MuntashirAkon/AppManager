/*
 * Copyright (C) 2021 Muntashir Al-Islam
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
import android.app.INotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.INetworkPolicyManager;
import android.net.Uri;
import android.os.Build;
import android.util.Pair;

import org.json.JSONException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.misc.VMRuntime;
import io.github.muntashirakon.AppManager.rules.PseudoRules;
import io.github.muntashirakon.AppManager.rules.RulesImporter;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.types.FreshFile;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.MagiskUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.ProxyFile;

import static io.github.muntashirakon.AppManager.backup.BackupManager.CACHE_DIRS;
import static io.github.muntashirakon.AppManager.backup.BackupManager.DATA_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.EXT_DATA;
import static io.github.muntashirakon.AppManager.backup.BackupManager.EXT_MEDIA;
import static io.github.muntashirakon.AppManager.backup.BackupManager.EXT_OBB;
import static io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PLACEHOLDER;
import static io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.MASTER_KEY;
import static io.github.muntashirakon.AppManager.backup.BackupManager.SOURCE_PREFIX;

class RestoreOp implements Closeable {
        static final String TAG = "RestoreOp";

        @NonNull
        private final String packageName;
        @NonNull
        private final BackupFlags backupFlags;
        @NonNull
        private final BackupFlags requestedFlags;
        @NonNull
        private final MetadataManager.Metadata metadata;
        @NonNull
        private final ProxyFile backupPath;
        @NonNull
        private final BackupFiles.BackupFile backupFile;
        @Nullable
        private PackageInfo packageInfo;
        @NonNull
        private final Crypto crypto;
        @NonNull
        private final BackupFiles.Checksum checksum;
        private final int userHandle;
        private boolean isInstalled;
        private final List<File> decryptedFiles = new ArrayList<>();

        RestoreOp(@NonNull String packageName, @NonNull MetadataManager metadataManager,
                  @NonNull BackupFlags requestedFlags, @NonNull BackupFiles.BackupFile backupFile,
                  int userHandle) throws BackupException {
            this.packageName = packageName;
            this.requestedFlags = requestedFlags;
            this.backupFile = backupFile;
            this.backupPath = this.backupFile.getBackupPath();
            this.userHandle = userHandle;
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
            } catch (Throwable e) {
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
                packageInfo = PackageManagerCompat.getPackageInfo(packageName, PackageUtils.flagSigningInfo, userHandle);
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
                IOUtils.deleteSilently(new ProxyFile(file));
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
                if (requestedFlags.backupExtras()) restoreExtras();
                if (requestedFlags.backupRules()) restoreRules();
            } catch (BackupException e) {
                Log.e(TAG, e.getMessage(), e);
                return false;
            }
            return true;
        }

        private void checkMasterKey() throws BackupException {
            String oldChecksum = checksum.get(MASTER_KEY);
            ProxyFile masterKey = KeyStoreUtils.getMasterKey(userHandle);
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
            boolean isVerified = true;
            if (packageInfo != null) {
                // Check signature of the installed app
                List<String> certChecksumList = Arrays.asList(PackageUtils.getSigningCertChecksums(metadata.checksumAlgo, packageInfo, false));
                String[] certChecksums = BackupFiles.Checksum.getCertChecksums(checksum);
                for (String checksum : certChecksums) {
                    if (certChecksumList.contains(checksum)) continue;
                    isVerified = false;
                    if (!requestedFlags.skipSignatureCheck()) {
                        throw new BackupException("Signing info verification failed." +
                                "\nInstalled: " + certChecksumList.toString() +
                                "\nBackup: " + Arrays.toString(certChecksums));
                    }
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
            if (!isVerified) {
                // Signature verification failed but still here because signature check is disabled.
                // The only way to restore is to reinstall the app
                try {
                    PackageInstallerCompat.uninstall(packageName, userHandle, false);
                } catch (Exception e) {
                    throw new BackupException("An uninstall was necessary but couldn't perform it.", e);
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
            PackageInstallerCompat packageInstaller = PackageInstallerCompat.getNewInstance(userHandle, metadata.installer);
            // We don't need to display install completed message
            packageInstaller.setShowCompletedMessage(false);
            if (!packageInstaller.install(allApks, packageName)) {
                deleteFiles(allApks);
                throw new BackupException("A (re)install was necessary but couldn't perform it.");
            }
            deleteFiles(allApks);  // Clean up apk files
            // Get package info, again
            try {
                packageInfo = PackageManagerCompat.getPackageInfo(packageName, PackageUtils.flagSigningInfo, userHandle);
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
            ProxyFile keyStorePath = KeyStoreUtils.getKeyStorePath(userHandle);
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
            // Force-stop and clear app data
            PackageManagerCompat.clearApplicationUserData(packageName, userHandle);
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
                ProxyFile dataSourceFile = new ProxyFile(dataSource);
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

        private void restoreExtras() throws BackupException {
            if (!isInstalled) {
                throw new BackupException("Misc restore is requested but the app isn't installed.");
            }
            PseudoRules rules = new PseudoRules(AppManager.getContext(), packageName, userHandle);
            // Backward compatibility for restoring permissions
            loadMiscRules(rules);
            // Apply rules
            List<RulesStorageManager.Entry> entries = rules.getAll();
            AppOpsService appOpsService = new AppOpsService();
            INotificationManager notificationManager = INotificationManager.Stub.asInterface(ProxyBinder.getService(Context.NOTIFICATION_SERVICE));
            INetworkPolicyManager netPolicy = INetworkPolicyManager.Stub.asInterface(ProxyBinder.getService("netpolicy"));
            for (RulesStorageManager.Entry entry : entries) {
                try {
                    switch (entry.type) {
                        case APP_OP:
                            appOpsService.setMode(Integer.parseInt(entry.name),
                                    packageInfo.applicationInfo.uid, packageName,
                                    (int) entry.extra);
                            break;
                        case NET_POLICY:
                            netPolicy.setUidPolicy(packageInfo.applicationInfo.uid, (int) entry.extra);
                            break;
                        case PERMISSION:
                            if ((boolean) entry.extra /* isGranted */) {
                                PackageManagerCompat.grantPermission(packageName, entry.name, userHandle);
                            } else {
                                PackageManagerCompat.revokePermission(packageName, entry.name, userHandle);
                            }
                            break;
                        case BATTERY_OPT:
                            Runner.runCommand(new String[]{"dumpsys", "deviceidle", "whitelist", "+" + packageName});
                            break;
                        case MAGISK_HIDE:
                            MagiskUtils.hide(packageName);
                            break;
                        case NOTIFICATION:
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                notificationManager.setNotificationListenerAccessGrantedForUser(
                                        new ComponentName(packageName, entry.name), userHandle, true);
                            }
                            break;
                        case URI_GRANT:
                            UriManager.UriGrant uriGrant = (UriManager.UriGrant) entry.extra;
                            UriManager.UriGrant newUriGrant = new UriManager.UriGrant(
                                    uriGrant.sourceUserId, userHandle, uriGrant.userHandle,
                                    uriGrant.sourcePkg, uriGrant.targetPkg, uriGrant.uri,
                                    uriGrant.prefix, uriGrant.modeFlags, uriGrant.createdTime);
                            UriManager uriManager = new UriManager();
                            uriManager.grantUri(newUriGrant);
                            uriManager.writeGrantedUriPermissions();
                            break;
                    }
                } catch (Throwable e) {
                    // There are several reason restoring these things go wrong, especially when
                    // downgrading from an Android to another. It's better to simply suppress these
                    // exceptions instead of causing a failure or worse, a crash
                    e.printStackTrace();
                }
            }
        }

        private void loadMiscRules(final PseudoRules rules) throws BackupException {
            File miscFile = backupFile.getMiscFile(metadata.crypto);
            if (miscFile.exists()) {
                if (!requestedFlags.skipSignatureCheck()) {
                    String checksum = DigestUtils.getHexDigest(metadata.checksumAlgo, miscFile);
                    if (!checksum.equals(this.checksum.get(miscFile.getName()))) {
                        throw new BackupException("Couldn't verify misc file." +
                                "\nFile: " + miscFile +
                                "\nFound: " + checksum +
                                "\nRequired: " + this.checksum.get(miscFile.getName()));
                    }
                }
                // Decrypt permission file
                if (!crypto.decrypt(new File[]{miscFile})) {
                    throw new BackupException("Failed to decrypt " + miscFile.getName());
                }
                // Get decrypted file
                miscFile = backupFile.getMiscFile(CryptoUtils.MODE_NO_ENCRYPTION);
                decryptedFiles.addAll(Arrays.asList(crypto.getNewFiles()));
                try {
                    rules.loadExternalEntries(miscFile);
                } catch (Throwable e) {
                    throw new BackupException("Failed to load rules from misc.", e);
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
                    importer.applyRules(true);
                } catch (IOException e) {
                    throw new BackupException("Failed to restore rules file.", e);
                }
            } else if (metadata.hasRules) {
                throw new BackupException("Rules file is missing.");
            } // else there are no rules, just skip
        }

        @Nullable
        private File[] getSourceFiles(@NonNull File backupPath) {
            return backupPath.listFiles((dir, name) -> name.startsWith(SOURCE_PREFIX));
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        private void deleteFiles(@NonNull FreshFile[] files) {
            for (FreshFile file : files) file.delete();
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
    }