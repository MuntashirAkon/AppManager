// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.annotation.SuppressLint;
import android.app.INotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.PseudoRules;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.RulesImporter;
import io.github.muntashirakon.AppManager.rules.struct.AppOpRule;
import io.github.muntashirakon.AppManager.rules.struct.NetPolicyRule;
import io.github.muntashirakon.AppManager.rules.struct.PermissionRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.rules.struct.SsaidRule;
import io.github.muntashirakon.AppManager.rules.struct.UriGrantRule;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.servermanager.PermissionCompat;
import io.github.muntashirakon.AppManager.ssaid.SsaidSettings;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.MagiskUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.ProxyFile;

import static io.github.muntashirakon.AppManager.backup.BackupManager.DATA_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.EXT_DATA;
import static io.github.muntashirakon.AppManager.backup.BackupManager.EXT_MEDIA;
import static io.github.muntashirakon.AppManager.backup.BackupManager.EXT_OBB;
import static io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PLACEHOLDER;
import static io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.MASTER_KEY;
import static io.github.muntashirakon.AppManager.backup.BackupManager.SOURCE_PREFIX;

@WorkerThread
class RestoreOp implements Closeable {
    static final String TAG = "RestoreOp";

    @NonNull
    private final Context context = AppManager.getContext();
    @NonNull
    private final String packageName;
    @NonNull
    private final BackupFlags backupFlags;
    @NonNull
    private final BackupFlags requestedFlags;
    @NonNull
    private final MetadataManager.Metadata metadata;
    @NonNull
    private final Path backupPath;
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
    private final List<Path> decryptedFiles = new ArrayList<>();

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
        } catch (IOException e) {
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
        Path checksumFile;
        try {
            checksumFile = this.backupFile.getChecksumFile(metadata.crypto);
        } catch (IOException e) {
            throw new BackupException("Could not get encrypted checksum.txt file.", e);
        }
        // Decrypt checksum
        if (!crypto.decrypt(new Path[]{checksumFile})) {
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
            Path metadataFile;
            try {
                metadataFile = this.backupFile.getMetadataFile();
            } catch (IOException e) {
                throw new BackupException("Could not get metadata file.", e);
            }
            String checksum = DigestUtils.getHexDigest(metadata.checksumAlgo, metadataFile);
            if (!checksum.equals(this.checksum.get(metadataFile.getName()))) {
                throw new BackupException("Couldn't verify metadata file." +
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
        for (Path file : decryptedFiles) {
            Log.d(TAG, "Deleting " + file);
            file.delete();
        }
    }

    void runRestore() throws BackupException {
        if (requestedFlags.backupData() && metadata.keyStore && !requestedFlags.skipSignatureCheck()) {
            // Check checksum of master key first
            checkMasterKey();
        }
        if (requestedFlags.backupApkFiles()) restoreApkFiles();
        if (requestedFlags.backupData()) {
            restoreData();
            if (metadata.keyStore) restoreKeyStore();
        }
        if (requestedFlags.backupExtras()) restoreExtras();
        if (requestedFlags.backupRules()) restoreRules();
    }

    private void checkMasterKey() throws BackupException {
        String oldChecksum = checksum.get(MASTER_KEY);
        Path masterKey = new Path(context, KeyStoreUtils.getMasterKey(userHandle));
        if (!masterKey.exists()) {
            if (oldChecksum == null) return;
            else throw new BackupException("Master key existed when the checksum was made but now it doesn't.");
        }
        if (oldChecksum == null) {
            throw new BackupException("Master key exists but it didn't exist when the backup was made.");
        }
        String newChecksum = DigestUtils.getHexDigest(metadata.checksumAlgo,
                FileUtils.getFileContent(masterKey).getBytes());
        if (!newChecksum.equals(oldChecksum)) {
            throw new BackupException("Checksums for master key did not match.");
        }
    }

    private void restoreApkFiles() throws BackupException {
        if (!backupFlags.backupApkFiles()) {
            throw new BackupException("APK restore is requested but backup doesn't contain any source files.");
        }
        Path[] backupSourceFiles = getSourceFiles(backupPath);
        if (backupSourceFiles.length == 0) {
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
                            "\nInstalled: " + certChecksumList +
                            "\nBackup: " + Arrays.toString(certChecksums));
                }
            }
        }
        if (!requestedFlags.skipSignatureCheck()) {
            String checksum;
            for (Path file : backupSourceFiles) {
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
        Path packageStagingDirectory;
        if (AppPref.isRootOrAdbEnabled()) {
            try {
                PackageUtils.ensurePackageStagingDirectoryPrivileged();
                packageStagingDirectory = new Path(context, new ProxyFile(PackageUtils.PACKAGE_STAGING_DIRECTORY));
            } catch (Exception e) {
                throw new BackupException("Could not ensure the existence of /data/local/tmp", e);
            }
        } else {
            packageStagingDirectory = backupPath;
        }
        // Setup apk files, including split apk
        final int splitCount = metadata.splitConfigs.length;
        String[] allApkNames = new String[splitCount + 1];
        Path[] allApks = new Path[splitCount + 1];
        try {
            Path baseApk = packageStagingDirectory.createNewFile(metadata.apkName, null);
            allApks[0] = baseApk;
            allApkNames[0] = metadata.apkName;
            for (int i = 1; i < allApkNames.length; ++i) {
                allApkNames[i] = metadata.splitConfigs[i - 1];
                allApks[i] = packageStagingDirectory.createNewFile(allApkNames[i], null);
            }
        } catch (IOException e) {
            throw new BackupException("Could not create staging files", e);
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
        try {
            TarUtils.extract(metadata.tarType, backupSourceFiles, packageStagingDirectory, allApkNames, null);
        } catch (Throwable th) {
            throw new BackupException("Failed to extract the apk file(s).", th);
        }
        // A normal update will do it now
        PackageInstallerCompat packageInstaller = PackageInstallerCompat.getNewInstance(userHandle, metadata.installer);
        try {
            if (!packageInstaller.install(allApks, packageName)) {
                throw new BackupException("A (re)install was necessary but couldn't perform it.");
            }
        } finally {
            deleteFiles(allApks);  // Clean up apk files
        }
        // Get package info, again
        try {
            packageInfo = PackageManagerCompat.getPackageInfo(packageName, PackageUtils.flagSigningInfo, userHandle);
            isInstalled = true;
        } catch (Exception e) {
            throw new BackupException("Apparently the install wasn't complete in the previous section.", e);
        }
    }

    private void restoreKeyStore() throws BackupException {
        if (packageInfo == null) {
            throw new BackupException("KeyStore restore is requested but the app isn't installed.");
        }
        Path[] keyStoreFiles = getKeyStoreFiles(backupPath);
        if (keyStoreFiles.length == 0) {
            throw new BackupException("KeyStore files should've existed but they didn't");
        }
        if (!requestedFlags.skipSignatureCheck()) {
            String checksum;
            for (Path file : keyStoreFiles) {
                checksum = DigestUtils.getHexDigest(metadata.checksumAlgo, file);
                if (!checksum.equals(this.checksum.get(file.getName()))) {
                    throw new BackupException("KeyStore file verification failed." +
                            "\nFile: " + file +
                            "\nFound: " + checksum +
                            "\nRequired: " + this.checksum.get(file.getName()));
                }
            }
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
        Path keyStorePath = new Path(context, KeyStoreUtils.getKeyStorePath(userHandle));
        try {
            TarUtils.extract(metadata.tarType, keyStoreFiles, keyStorePath, null, null);
        } catch (Throwable th) {
            throw new BackupException("Failed to restore the KeyStore files.", th);
        }
        // Rename files
        int uid = packageInfo.applicationInfo.uid;
        List<String> keyStoreFileNames = KeyStoreUtils.getKeyStoreFiles(KEYSTORE_PLACEHOLDER, userHandle);
        for (String keyStoreFileName : keyStoreFileNames) {
            try {
                keyStorePath.findFile(keyStoreFileName).moveTo(keyStorePath.findOrCreateFile(Utils.replaceOnce(
                        keyStoreFileName, String.valueOf(KEYSTORE_PLACEHOLDER), String.valueOf(uid)), null));
            } catch (IOException e) {
                throw new BackupException("Failed to rename KeyStore files", e);
            }
        }
        // TODO Restore permissions
        Runner.runCommand(new String[]{"restorecon", "-R", keyStorePath.getFilePath()});
    }

    @SuppressLint("SdCardPath")
    private void restoreData() throws BackupException {
        // Data restore is requested: Data restore is only possible if the app is actually
        // installed. So, check if it's installed first.
        if (packageInfo == null) {
            throw new BackupException("Data restore is requested but the app isn't installed.");
        }
        Path[] dataFiles;
        if (!requestedFlags.skipSignatureCheck()) {
            // Verify integrity of the data backups
            String checksum;
            for (int i = 0; i < metadata.dataDirs.length; ++i) {
                dataFiles = getDataFiles(backupPath, i);
                if (dataFiles.length == 0) {
                    throw new BackupException("Data restore is requested but there are no data files for index " + i + ".");
                }
                for (Path file : dataFiles) {
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
            if (new ProxyFile(dataSource).exists()) {
                uidAndGid = BackupUtils.getUidAndGid(dataSource, packageInfo.applicationInfo.uid);
            }
            if (dataFiles.length == 0) {
                throw new BackupException("Data restore is requested but there are no data files for index " + i + ".");
            }
            // External storage checks
            if (dataSource.startsWith("/storage") || dataSource.startsWith("/sdcard")) {
                isExternal = true;
                // Skip if external data restore is not requested
                if (!requestedFlags.backupExternalData() && dataSource.contains(EXT_DATA))
                    continue;
                // Skip if media/obb restore not requested
                if (!requestedFlags.backupMediaObb() && (dataSource.contains(EXT_MEDIA)
                        || dataSource.contains(EXT_OBB))) continue;
            } else {
                isExternal = false;
                // Skip if internal data restore is not requested.
                if (!requestedFlags.backupInternalData()) continue;
            }
            // Fix problem accessing external directory in Android API < 23
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                if (dataSource.contains("/storage/emulated/")) {
                    dataSource = dataSource.replace("/storage/emulated/", "/mnt/shell/emulated/");
                }
            }
            // Create data folder if not exists
            Path dataSourceFile = new Path(context, new ProxyFile(dataSource));
            if (!dataSourceFile.exists()) {
                // FIXME(10/9/20): Check if the media is mounted and writable before running
                //  mkdir, otherwise it may create a folder to a path that will be gone
                //  after a restart
                dataSourceFile.mkdirs();
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
            try {
                TarUtils.extract(metadata.tarType, dataFiles, dataSourceFile, null, BackupUtils
                        .getExcludeDirs(!requestedFlags.backupCache(), null));
            } catch (Throwable th) {
                throw new BackupException("Failed to restore data files for index " + i + ".", th);
            }
            // Fix UID and GID
            if (uidAndGid != null && !Runner.runCommand(String.format(Locale.ROOT, "chown -R %d:%d \"%s\"", uidAndGid.first, uidAndGid.second, dataSource)).isSuccessful()) {
                throw new BackupException("Failed to restore ownership info for index " + i + ".");
            }
            // Restore permissions
            if (!isExternal) Runner.runCommand(new String[]{"restorecon", "-R", dataSource});
        }
    }

    private synchronized void restoreExtras() throws BackupException {
        if (!isInstalled) {
            throw new BackupException("Misc restore is requested but the app isn't installed.");
        }
        PseudoRules rules = new PseudoRules(packageName, userHandle);
        // Backward compatibility for restoring permissions
        loadMiscRules(rules);
        // Apply rules
        List<RuleEntry> entries = rules.getAll();
        AppOpsService appOpsService = new AppOpsService();
        INotificationManager notificationManager = INotificationManager.Stub.asInterface(ProxyBinder.getService(Context.NOTIFICATION_SERVICE));
        for (RuleEntry entry : entries) {
            try {
                switch (entry.type) {
                    case APP_OP:
                        appOpsService.setMode(Integer.parseInt(entry.name), packageInfo.applicationInfo.uid,
                                packageName, ((AppOpRule) entry).getMode());
                        break;
                    case NET_POLICY:
                        NetworkPolicyManagerCompat.setUidPolicy(packageInfo.applicationInfo.uid,
                                ((NetPolicyRule) entry).getPolicies());
                        break;
                    case PERMISSION:
                        if (((PermissionRule) entry).isGranted()) {
                            PermissionCompat.grantPermission(packageName, entry.name, userHandle);
                        } else {
                            PermissionCompat.revokePermission(packageName, entry.name, userHandle);
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
                        UriManager.UriGrant uriGrant = ((UriGrantRule) entry).getUriGrant();
                        UriManager.UriGrant newUriGrant = new UriManager.UriGrant(
                                uriGrant.sourceUserId, userHandle, uriGrant.userHandle,
                                uriGrant.sourcePkg, uriGrant.targetPkg, uriGrant.uri,
                                uriGrant.prefix, uriGrant.modeFlags, uriGrant.createdTime);
                        UriManager uriManager = new UriManager();
                        uriManager.grantUri(newUriGrant);
                        uriManager.writeGrantedUriPermissions();
                        break;
                    case SSAID:
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            new SsaidSettings(packageName, packageInfo.applicationInfo.uid)
                                    .setSsaid(((SsaidRule) entry).getSsaid());
                        }
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
        Path miscFile;
        try {
            miscFile = backupFile.getMiscFile(metadata.crypto);
        } catch (IOException e) {
            // There are no permissions, just skip
            return;
        }
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
        if (!crypto.decrypt(new Path[]{miscFile})) {
            throw new BackupException("Failed to decrypt " + miscFile.getName());
        }
        // Get decrypted file
        try {
            miscFile = backupFile.getMiscFile(CryptoUtils.MODE_NO_ENCRYPTION);
        } catch (IOException e) {
            throw new BackupException("Could not get decrypted misc file", e);
        }
        decryptedFiles.addAll(Arrays.asList(crypto.getNewFiles()));
        try {
            rules.loadExternalEntries(miscFile);
        } catch (Throwable e) {
            throw new BackupException("Failed to load rules from misc.", e);
        }
    }

    private void restoreRules() throws BackupException {
        // Apply rules
        if (!isInstalled) {
            throw new BackupException("Rules restore is requested but the app isn't installed.");
        }
        Path rulesFile;
        try {
            rulesFile = backupFile.getRulesFile(metadata.crypto);
        } catch (IOException e) {
            if (metadata.hasRules) {
                throw new BackupException("Rules file is missing.", e);
            } else {
                // There are no rules, just skip
                return;
            }
        }
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
        if (!crypto.decrypt(new Path[]{rulesFile})) {
            throw new BackupException("Failed to decrypt " + rulesFile.getName());
        }
        // Get decrypted file
        try {
            rulesFile = backupFile.getRulesFile(CryptoUtils.MODE_NO_ENCRYPTION);
        } catch (IOException e) {
            throw new BackupException("Could not get decrypted rules file", e);
        }
        decryptedFiles.addAll(Arrays.asList(crypto.getNewFiles()));
        try (RulesImporter importer = new RulesImporter(Arrays.asList(RuleType.values()), new int[]{userHandle})) {
            importer.addRulesFromPath(rulesFile);
            importer.setPackagesToImport(Collections.singletonList(packageName));
            importer.applyRules(true);
        } catch (IOException e) {
            throw new BackupException("Failed to restore rules file.", e);
        }
    }

    @NonNull
    private Path[] getSourceFiles(@NonNull Path backupPath) {
        String mode = CryptoUtils.getExtension(metadata.crypto);
        return backupPath.listFiles((dir, name) -> name.startsWith(SOURCE_PREFIX) && name.endsWith(mode));
    }

    private void deleteFiles(@NonNull Path[] files) {
        for (Path file : files) {
            file.delete();
        }
    }

    @NonNull
    private Path[] getKeyStoreFiles(@NonNull Path backupPath) {
        String mode = CryptoUtils.getExtension(metadata.crypto);
        return backupPath.listFiles((dir, name) -> name.startsWith(KEYSTORE_PREFIX) && name.endsWith(mode));
    }

    @NonNull
    private Path[] getDataFiles(@NonNull Path backupPath, int index) {
        String mode = CryptoUtils.getExtension(metadata.crypto);
        final String dataPrefix = DATA_PREFIX + index;
        return backupPath.listFiles((dir, name) -> name.startsWith(dataPrefix) && name.endsWith(mode));
    }
}