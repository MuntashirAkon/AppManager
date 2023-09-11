// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static io.github.muntashirakon.AppManager.backup.BackupManager.DATA_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PLACEHOLDER;
import static io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.MASTER_KEY;
import static io.github.muntashirakon.AppManager.backup.BackupManager.SOURCE_PREFIX;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.GET_SIGNING_CERTIFICATES;

import android.app.AppOpsManager;
import android.app.INotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.system.ErrnoException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.installer.InstallerOptions;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.DeviceIdleManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.magisk.MagiskDenyList;
import io.github.muntashirakon.AppManager.magisk.MagiskHide;
import io.github.muntashirakon.AppManager.permission.PermUtils;
import io.github.muntashirakon.AppManager.permission.Permission;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.rules.PseudoRules;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.RulesImporter;
import io.github.muntashirakon.AppManager.rules.struct.AppOpRule;
import io.github.muntashirakon.AppManager.rules.struct.MagiskDenyListRule;
import io.github.muntashirakon.AppManager.rules.struct.MagiskHideRule;
import io.github.muntashirakon.AppManager.rules.struct.NetPolicyRule;
import io.github.muntashirakon.AppManager.rules.struct.PermissionRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.rules.struct.SsaidRule;
import io.github.muntashirakon.AppManager.rules.struct.UriGrantRule;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.ssaid.SsaidSettings;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.UidGidPair;

@WorkerThread
class RestoreOp implements Closeable {
    static final String TAG = RestoreOp.class.getSimpleName();
    private static final Object sLock = new Object();

    @NonNull
    private final String mPackageName;
    @NonNull
    private final BackupFlags mBackupFlags;
    @NonNull
    private final BackupFlags mRequestedFlags;
    @NonNull
    private final MetadataManager.Metadata mMetadata;
    @NonNull
    private final Path mBackupPath;
    @NonNull
    private final BackupFiles.BackupFile mBackupFile;
    @Nullable
    private PackageInfo mPackageInfo;
    @NonNull
    private final Crypto mCrypto;
    @NonNull
    private final BackupFiles.Checksum mChecksum;
    private final int mUserId;
    private boolean mIsInstalled;
    private final List<Path> mDecryptedFiles = new ArrayList<>();

    private boolean mRequiresRestart;

    RestoreOp(@NonNull String packageName, @NonNull MetadataManager metadataManager,
              @NonNull BackupFlags requestedFlags, @NonNull BackupFiles.BackupFile backupFile,
              int userId) throws BackupException {
        mPackageName = packageName;
        mRequestedFlags = requestedFlags;
        mBackupFile = backupFile;
        mBackupPath = mBackupFile.getBackupPath();
        mUserId = userId;
        try {
            metadataManager.readMetadata(mBackupFile);
            mMetadata = metadataManager.getMetadata();
            mBackupFlags = mMetadata.flags;
        } catch (IOException e) {
            throw new BackupException("Failed to read metadata. Possibly due to malformed json file.", e);
        }
        // Setup crypto
        if (!CryptoUtils.isAvailable(mMetadata.crypto)) {
            throw new BackupException("Mode " + mMetadata.crypto + " is currently unavailable.");
        }
        try {
            mCrypto = CryptoUtils.getCrypto(mMetadata);
        } catch (CryptoException e) {
            throw new BackupException("Failed to get crypto " + mMetadata.crypto, e);
        }
        Path checksumFile;
        try {
            checksumFile = mBackupFile.getChecksumFile(mMetadata.crypto);
        } catch (IOException e) {
            throw new BackupException("Could not get encrypted checksum.txt file.", e);
        }
        // Decrypt checksum
        try {
            decrypt(new Path[]{checksumFile});
        } catch (IOException e) {
            throw new BackupException("Failed to decrypt " + checksumFile.getName(), e);
        }
        // Get checksums
        try {
            mChecksum = mBackupFile.getChecksum(CryptoUtils.MODE_NO_ENCRYPTION);
        } catch (Throwable e) {
            mBackupFile.cleanup();
            throw new BackupException("Failed to get checksums.", e);
        }
        // Verify metadata
        if (!requestedFlags.skipSignatureCheck()) {
            Path metadataFile;
            try {
                metadataFile = mBackupFile.getMetadataFile();
            } catch (IOException e) {
                throw new BackupException("Could not get metadata file.", e);
            }
            String checksum = DigestUtils.getHexDigest(mMetadata.checksumAlgo, metadataFile);
            if (!checksum.equals(mChecksum.get(metadataFile.getName()))) {
                throw new BackupException("Couldn't verify metadata file." +
                        "\nFile: " + metadataFile +
                        "\nFound: " + checksum +
                        "\nRequired: " + mChecksum.get(metadataFile.getName()));
            }
        }
        // Check user handle
        if (mMetadata.userHandle != userId) {
            Log.w(TAG, "Using different user handle.");
        }
        // Get package info
        mPackageInfo = null;
        try {
            mPackageInfo = PackageManagerCompat.getPackageInfo(packageName, GET_SIGNING_CERTIFICATES
                    | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
        } catch (Exception ignore) {
        }
        mIsInstalled = mPackageInfo != null;
    }

    @Override
    public void close() {
        Log.d(TAG, "Close called");
        mCrypto.close();
        for (Path file : mDecryptedFiles) {
            Log.d(TAG, "Deleting %s", file);
            file.delete();
        }
    }

    @NonNull
    public MetadataManager.Metadata getMetadata() {
        return mMetadata;
    }

    void runRestore(@Nullable ProgressHandler progressHandler) throws BackupException {
        try {
            if (mRequestedFlags.backupData() && mMetadata.keyStore && !mRequestedFlags.skipSignatureCheck()) {
                // Check checksum of master key first
                checkMasterKey();
            }
            incrementProgress(progressHandler);
            if (mRequestedFlags.backupApkFiles()) {
                restoreApkFiles();
                incrementProgress(progressHandler);
            }
            if (mRequestedFlags.backupData()) {
                restoreData();
                if (mMetadata.keyStore) restoreKeyStore();
                incrementProgress(progressHandler);
            }
            if (mRequestedFlags.backupExtras()) {
                restoreExtras();
                incrementProgress(progressHandler);
            }
            if (mRequestedFlags.backupRules()) {
                restoreRules();
                incrementProgress(progressHandler);
            }
        } catch (BackupException e) {
            throw e;
        } catch (Throwable th) {
            throw new BackupException("Unknown error occurred", th);
        }
    }

    private static void incrementProgress(@Nullable ProgressHandler progressHandler) {
        if (progressHandler == null) {
            return;
        }
        float current = progressHandler.getLastProgress() + 1;
        progressHandler.postUpdate(current);
    }

    public boolean requiresRestart() {
        return mRequiresRestart;
    }

    private void checkMasterKey() throws BackupException {
        if (true) {
            // TODO: 6/2/22 MasterKey may not actually be necessary.
            return;
        }
        String oldChecksum = mChecksum.get(MASTER_KEY);
        Path masterKey;
        try {
            masterKey = KeyStoreUtils.getMasterKey(mUserId);
        } catch (FileNotFoundException e) {
            if (oldChecksum == null) return;
            else throw new BackupException("Master key existed when the checksum was made but now it doesn't.");
        }
        if (oldChecksum == null) {
            throw new BackupException("Master key exists but it didn't exist when the backup was made.");
        }
        String newChecksum = DigestUtils.getHexDigest(mMetadata.checksumAlgo, masterKey.getContentAsString().getBytes());
        if (!newChecksum.equals(oldChecksum)) {
            throw new BackupException("Checksums for master key did not match.");
        }
    }

    private void restoreApkFiles() throws BackupException {
        if (!mBackupFlags.backupApkFiles()) {
            throw new BackupException("APK restore is requested but backup doesn't contain any source files.");
        }
        Path[] backupSourceFiles = getSourceFiles(mBackupPath);
        if (backupSourceFiles.length == 0) {
            // No source backup found
            throw new BackupException("Source restore is requested but there are no source files.");
        }
        boolean isVerified = true;
        if (mPackageInfo != null) {
            // Check signature of the installed app
            List<String> certChecksumList = Arrays.asList(PackageUtils.getSigningCertChecksums(mMetadata.checksumAlgo, mPackageInfo, false));
            String[] certChecksums = BackupFiles.Checksum.getCertChecksums(mChecksum);
            for (String checksum : certChecksums) {
                if (certChecksumList.contains(checksum)) continue;
                isVerified = false;
                if (!mRequestedFlags.skipSignatureCheck()) {
                    throw new BackupException("Signing info verification failed." +
                            "\nInstalled: " + certChecksumList +
                            "\nBackup: " + Arrays.toString(certChecksums));
                }
            }
        }
        if (!mRequestedFlags.skipSignatureCheck()) {
            String checksum;
            for (Path file : backupSourceFiles) {
                checksum = DigestUtils.getHexDigest(mMetadata.checksumAlgo, file);
                if (!checksum.equals(mChecksum.get(file.getName()))) {
                    throw new BackupException("Source file verification failed." +
                            "\nFile: " + file +
                            "\nFound: " + checksum +
                            "\nRequired: " + mChecksum.get(file.getName()));
                }
            }
        }
        if (!isVerified) {
            // Signature verification failed but still here because signature check is disabled.
            // The only way to restore is to reinstall the app
            synchronized (sLock) {
                PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                if (installer.uninstall(mPackageName, mUserId, false)) {
                    throw new BackupException("An uninstallation was necessary but couldn't perform it.");
                }
            }
        }
        // Setup package staging directory
        Path packageStagingDirectory = Paths.get(PackageUtils.PACKAGE_STAGING_DIRECTORY);
        try {
            synchronized (sLock) {
                PackageUtils.ensurePackageStagingDirectoryPrivileged();
            }
        } catch (Exception ignore) {
        }
        if (!packageStagingDirectory.canWrite()) {
            packageStagingDirectory = mBackupPath;
        }
        synchronized (sLock) {
            // Setup apk files, including split apk
            final int splitCount = mMetadata.splitConfigs.length;
            String[] allApkNames = new String[splitCount + 1];
            Path[] allApks = new Path[splitCount + 1];
            try {
                Path baseApk = packageStagingDirectory.createNewFile(mMetadata.apkName, null);
                allApks[0] = baseApk;
                allApkNames[0] = mMetadata.apkName;
                for (int i = 1; i < allApkNames.length; ++i) {
                    allApkNames[i] = mMetadata.splitConfigs[i - 1];
                    allApks[i] = packageStagingDirectory.createNewFile(allApkNames[i], null);
                }
            } catch (IOException e) {
                throw new BackupException("Could not create staging files", e);
            }
            // Decrypt sources
            try {
                backupSourceFiles = decrypt(backupSourceFiles);
            } catch (IOException e) {
                throw new BackupException("Failed to decrypt " + Arrays.toString(backupSourceFiles), e);
            }
            // Extract apk files to the package staging directory
            try {
                TarUtils.extract(mMetadata.tarType, backupSourceFiles, packageStagingDirectory, allApkNames, null, null);
            } catch (Throwable th) {
                throw new BackupException("Failed to extract the apk file(s).", th);
            }
            // A normal update will do it now
            InstallerOptions options = new InstallerOptions();
            options.setInstallerName(mMetadata.installer);
            options.setUserId(mUserId);
            PackageInstallerCompat packageInstaller = PackageInstallerCompat.getNewInstance();
            packageInstaller.setOnInstallListener(new PackageInstallerCompat.OnInstallListener() {
                @Override
                public void onStartInstall(int sessionId, String packageName) {
                }

                @Override
                public void onAnotherAttemptInMiui(@Nullable ApkFile apkFile) {
                    // This works because the parent install method still remains active until a final status is
                    // received after all the attempts are finished, which is, then, returned to the parent.
                    packageInstaller.install(allApks, mPackageName, options);
                }

                @Override
                public void onFinishedInstall(int sessionId, String packageName, int result, @Nullable String blockingPackage, @Nullable String statusMessage) {
                }
            });
            try {
                if (!packageInstaller.install(allApks, mPackageName, options)) {
                    throw new BackupException("A (re)install was necessary but couldn't perform it.");
                }
            } finally {
                deleteFiles(allApks);  // Clean up apk files
            }
            // Get package info, again
            try {
                mPackageInfo = PackageManagerCompat.getPackageInfo(mPackageName, GET_SIGNING_CERTIFICATES
                        | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, mUserId);
                mIsInstalled = true;
            } catch (Exception e) {
                throw new BackupException("Apparently the install wasn't complete in the previous section.", e);
            }
        }
    }

    private void restoreKeyStore() throws BackupException {
        if (mPackageInfo == null) {
            throw new BackupException("KeyStore restore is requested but the app isn't installed.");
        }
        Path[] keyStoreFiles = getKeyStoreFiles(mBackupPath);
        if (keyStoreFiles.length == 0) {
            throw new BackupException("KeyStore files should've existed but they didn't");
        }
        if (!mRequestedFlags.skipSignatureCheck()) {
            String checksum;
            for (Path file : keyStoreFiles) {
                checksum = DigestUtils.getHexDigest(mMetadata.checksumAlgo, file);
                if (!checksum.equals(mChecksum.get(file.getName()))) {
                    throw new BackupException("KeyStore file verification failed." +
                            "\nFile: " + file +
                            "\nFound: " + checksum +
                            "\nRequired: " + mChecksum.get(file.getName()));
                }
            }
        }
        // Decrypt sources
        try {
            keyStoreFiles = decrypt(keyStoreFiles);
        } catch (IOException e) {
            throw new BackupException("Failed to decrypt " + Arrays.toString(keyStoreFiles), e);
        }
        // Restore KeyStore files to the /data/misc/keystore folder
        Path keyStorePath = KeyStoreUtils.getKeyStorePath(mUserId);
        // Note down UID/GID
        UidGidPair uidGidPair;
        int mode;
        try {
            uidGidPair = Objects.requireNonNull(keyStorePath.getFile()).getUidGid();
            mode = keyStorePath.getFile().getMode();
        } catch (ErrnoException e) {
            throw new BackupException("Failed to access properties of the KeyStore folder.", e);
        }
        try {
            TarUtils.extract(mMetadata.tarType, keyStoreFiles, keyStorePath, null, null, null);
            // Restore folder permission
            Paths.chown(keyStorePath, uidGidPair.uid, uidGidPair.gid);
            //noinspection OctalInteger
            Paths.chmod(keyStorePath, mode & 0777);
        } catch (Throwable th) {
            throw new BackupException("Failed to restore the KeyStore files.", th);
        }
        // Rename files
        int uid = mPackageInfo.applicationInfo.uid;
        List<String> keyStoreFileNames = KeyStoreUtils.getKeyStoreFiles(KEYSTORE_PLACEHOLDER, mUserId);
        for (String keyStoreFileName : keyStoreFileNames) {
            try {
                String newFilename = Utils.replaceOnce(keyStoreFileName, String.valueOf(KEYSTORE_PLACEHOLDER), String.valueOf(uid));
                keyStorePath.findFile(keyStoreFileName).renameTo(newFilename);
                Path targetFile = keyStorePath.findFile(newFilename);
                // Restore file permission
                Paths.chown(targetFile, uidGidPair.uid, uidGidPair.gid);
                //noinspection OctalInteger
                Paths.chmod(targetFile, 0600);
            } catch (IOException | ErrnoException e) {
                throw new BackupException("Failed to rename KeyStore files", e);
            }
        }
        Runner.runCommand(new String[]{"restorecon", "-R", keyStorePath.getFilePath()});
    }

    private void restoreData() throws BackupException {
        // Data restore is requested: Data restore is only possible if the app is actually
        // installed. So, check if it's installed first.
        if (mPackageInfo == null) {
            throw new BackupException("Data restore is requested but the app isn't installed.");
        }
        if (!mRequestedFlags.skipSignatureCheck()) {
            // Verify integrity of the data backups
            String checksum;
            for (int i = 0; i < mMetadata.dataDirs.length; ++i) {
                Path[] dataFiles = getDataFiles(mBackupPath, i);
                if (dataFiles.length == 0) {
                    throw new BackupException("Data restore is requested but there are no data files for index " + i + ".");
                }
                for (Path file : dataFiles) {
                    checksum = DigestUtils.getHexDigest(mMetadata.checksumAlgo, file);
                    if (!checksum.equals(mChecksum.get(file.getName()))) {
                        throw new BackupException("Data file verification failed for index " + i + "." +
                                "\nFile: " + file +
                                "\nFound: " + checksum +
                                "\nRequired: " + mChecksum.get(file.getName()));
                    }
                }
            }
        }
        // Force-stop and clear app data
        PackageManagerCompat.clearApplicationUserData(mPackageName, mUserId);
        // Restore backups
        for (int i = 0; i < mMetadata.dataDirs.length; ++i) {
            String dataSource = BackupUtils.getWritableDataDirectory(mMetadata.dataDirs[i], mMetadata.userHandle, mUserId);
            BackupDataDirectoryInfo dataDirectoryInfo = BackupDataDirectoryInfo.getInfo(dataSource, mUserId);
            Path dataSourceFile = Paths.get(dataSource);

            Path[] dataFiles = getDataFiles(mBackupPath, i);
            if (dataFiles.length == 0) {
                throw new BackupException("Data restore is requested but there are no data files for index " + i + ".");
            }
            UidGidPair uidGidPair = dataSourceFile.getUidGid();
            if (uidGidPair == null) {
                // Fallback to app UID
                uidGidPair = new UidGidPair(mPackageInfo.applicationInfo.uid, mPackageInfo.applicationInfo.uid);
            }
            if (dataDirectoryInfo.isExternal()) {
                // Skip if external data restore is not requested
                switch (dataDirectoryInfo.subtype) {
                    case BackupDataDirectoryInfo.TYPE_ANDROID_DATA:
                        // Skip restoring Android/data directory if not requested
                        if (!mRequestedFlags.backupExternalData()) {
                            continue;
                        }
                        break;
                    case BackupDataDirectoryInfo.TYPE_ANDROID_OBB:
                    case BackupDataDirectoryInfo.TYPE_ANDROID_MEDIA:
                        // Skip restoring Android/data or Android/media if media/obb restore not requested
                        if (!mRequestedFlags.backupMediaObb()) {
                            continue;
                        }
                        break;
                    case BackupDataDirectoryInfo.TYPE_CREDENTIAL_PROTECTED:
                    case BackupDataDirectoryInfo.TYPE_CUSTOM:
                    case BackupDataDirectoryInfo.TYPE_DEVICE_PROTECTED:
                        // NOP
                        break;
                }
            } else {
                // Skip if internal data restore is not requested.
                if (!mRequestedFlags.backupInternalData()) continue;
            }
            // Create data folder if not exists
            if (!dataSourceFile.exists()) {
                if (dataDirectoryInfo.isExternal() && !dataDirectoryInfo.isMounted) {
                    throw new BackupException("External directory containing " + dataSource + " is not mounted.");
                }
                dataSourceFile.mkdirs();
                if (!dataDirectoryInfo.isExternal()) {
                    // Restore UID, GID
                    dataSourceFile.setUidGid(uidGidPair);
                }
            }
            // Decrypt data
            try {
                dataFiles = decrypt(dataFiles);
            } catch (IOException e) {
                throw new BackupException("Failed to decrypt " + Arrays.toString(dataFiles), e);
            }
            // Extract data to the data directory
            try {
                String publicSourceDir = new File(mPackageInfo.applicationInfo.publicSourceDir).getParent();
                TarUtils.extract(mMetadata.tarType, dataFiles, dataSourceFile, null, BackupUtils
                        .getExcludeDirs(!mRequestedFlags.backupCache(), null), publicSourceDir);
            } catch (Throwable th) {
                throw new BackupException("Failed to restore data files for index " + i + ".", th);
            }
            // Restore UID and GID
            if (!Runner.runCommand(String.format(Locale.ROOT, "chown -R %d:%d \"%s\"", uidGidPair.uid, uidGidPair.gid, dataSource)).isSuccessful()) {
                throw new BackupException("Failed to restore ownership info for index " + i + ".");
            }
            // Restore context
            if (!dataDirectoryInfo.isExternal()) {
                Runner.runCommand(new String[]{"restorecon", "-R", dataSource});
            }
        }
    }

    private synchronized void restoreExtras() throws BackupException {
        if (!mIsInstalled) {
            throw new BackupException("Misc restore is requested but the app isn't installed.");
        }
        PseudoRules rules = new PseudoRules(mPackageName, mUserId);
        // Backward compatibility for restoring permissions
        loadMiscRules(rules);
        // Apply rules
        List<RuleEntry> entries = rules.getAll();
        AppOpsManagerCompat appOpsManager = new AppOpsManagerCompat();
        INotificationManager notificationManager = INotificationManager.Stub.asInterface(ProxyBinder.getService(Context.NOTIFICATION_SERVICE));
        boolean magiskHideAvailable = MagiskHide.available();
        boolean canModifyAppOpMode = SelfPermissions.canModifyAppOpMode();
        boolean canChangeNetPolicy = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_NETWORK_POLICY);
        for (RuleEntry entry : entries) {
            try {
                switch (entry.type) {
                    case APP_OP:
                        if (canModifyAppOpMode) {
                            appOpsManager.setMode(Integer.parseInt(entry.name), mPackageInfo.applicationInfo.uid,
                                    mPackageName, ((AppOpRule) entry).getMode());
                        }
                        break;
                    case NET_POLICY:
                        if (canChangeNetPolicy) {
                            NetworkPolicyManagerCompat.setUidPolicy(mPackageInfo.applicationInfo.uid,
                                    ((NetPolicyRule) entry).getPolicies());
                        }
                        break;
                    case PERMISSION: {
                        PermissionRule permissionRule = (PermissionRule) entry;
                        Permission permission = permissionRule.getPermission(true);
                        permission.setAppOpAllowed(permission.getAppOp() != AppOpsManagerCompat.OP_NONE && appOpsManager
                                .checkOperation(permission.getAppOp(), mPackageInfo.applicationInfo.uid,
                                        mPackageName) == AppOpsManager.MODE_ALLOWED);
                        if (permissionRule.isGranted()) {
                            PermUtils.grantPermission(mPackageInfo, permission, appOpsManager, true, true);
                        } else {
                            PermUtils.revokePermission(mPackageInfo, permission, appOpsManager, true);
                        }
                        break;
                    }
                    case BATTERY_OPT:
                        if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.DEVICE_POWER)) {
                            DeviceIdleManagerCompat.disableBatteryOptimization(mPackageName);
                        }
                        break;
                    case MAGISK_HIDE: {
                        MagiskHideRule magiskHideRule = (MagiskHideRule) entry;
                        if (magiskHideAvailable) {
                            MagiskHide.apply(magiskHideRule.getMagiskProcess());
                        } else {
                            // Fall-back to Magisk DenyList
                            MagiskDenyList.apply(magiskHideRule.getMagiskProcess());
                        }
                        break;
                    }
                    case MAGISK_DENY_LIST: {
                        MagiskDenyList.apply(((MagiskDenyListRule) entry).getMagiskProcess());
                        break;
                    }
                    case NOTIFICATION:
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
                                && SelfPermissions.checkNotificationListenerAccess()) {
                            notificationManager.setNotificationListenerAccessGrantedForUser(
                                    new ComponentName(mPackageName, entry.name), mUserId, true);
                        }
                        break;
                    case URI_GRANT:
                        UriManager.UriGrant uriGrant = ((UriGrantRule) entry).getUriGrant();
                        UriManager.UriGrant newUriGrant = new UriManager.UriGrant(
                                uriGrant.sourceUserId, mUserId, uriGrant.userHandle,
                                uriGrant.sourcePkg, uriGrant.targetPkg, uriGrant.uri,
                                uriGrant.prefix, uriGrant.modeFlags, uriGrant.createdTime);
                        UriManager uriManager = new UriManager();
                        uriManager.grantUri(newUriGrant);
                        uriManager.writeGrantedUriPermissions();
                        mRequiresRestart = true;
                        break;
                    case SSAID:
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            new SsaidSettings(mUserId).setSsaid(mPackageName, mPackageInfo.applicationInfo.uid,
                                    ((SsaidRule) entry).getSsaid());
                            mRequiresRestart = true;
                        }
                        break;
                }
            } catch (Throwable e) {
                // There are several reason restoring these things go wrong, especially when
                // downgrading from an Android to another. It's better to simply suppress these
                // exceptions instead of causing a failure or worse, a crash
                Log.e(TAG, e);
            }
        }
    }

    private void loadMiscRules(final PseudoRules rules) throws BackupException {
        Path miscFile;
        try {
            miscFile = mBackupFile.getMiscFile(mMetadata.crypto);
        } catch (IOException e) {
            // There are no permissions, just skip
            return;
        }
        if (!mRequestedFlags.skipSignatureCheck()) {
            String checksum = DigestUtils.getHexDigest(mMetadata.checksumAlgo, miscFile);
            if (!checksum.equals(mChecksum.get(miscFile.getName()))) {
                throw new BackupException("Couldn't verify misc file." +
                        "\nFile: " + miscFile +
                        "\nFound: " + checksum +
                        "\nRequired: " + mChecksum.get(miscFile.getName()));
            }
        }
        // Decrypt permission file
        try {
            decrypt(new Path[]{miscFile});
        } catch (IOException e) {
            throw new BackupException("Failed to decrypt " + miscFile.getName(), e);
        }
        // Get decrypted file
        try {
            miscFile = mBackupFile.getMiscFile(CryptoUtils.MODE_NO_ENCRYPTION);
        } catch (IOException e) {
            throw new BackupException("Could not get decrypted misc file", e);
        }
        try {
            rules.loadExternalEntries(miscFile);
        } catch (Throwable e) {
            throw new BackupException("Failed to load rules from misc.", e);
        }
    }

    private void restoreRules() throws BackupException {
        // Apply rules
        if (!mIsInstalled) {
            throw new BackupException("Rules restore is requested but the app isn't installed.");
        }
        Path rulesFile;
        try {
            rulesFile = mBackupFile.getRulesFile(mMetadata.crypto);
        } catch (IOException e) {
            if (mMetadata.hasRules) {
                throw new BackupException("Rules file is missing.", e);
            } else {
                // There are no rules, just skip
                return;
            }
        }
        if (!mRequestedFlags.skipSignatureCheck()) {
            String checksum = DigestUtils.getHexDigest(mMetadata.checksumAlgo, rulesFile);
            if (!checksum.equals(mChecksum.get(rulesFile.getName()))) {
                throw new BackupException("Couldn't verify permission file." +
                        "\nFile: " + rulesFile +
                        "\nFound: " + checksum +
                        "\nRequired: " + mChecksum.get(rulesFile.getName()));
            }
        }
        // Decrypt rules file
        try {
            decrypt(new Path[]{rulesFile});
        } catch (IOException e) {
            throw new BackupException("Failed to decrypt " + rulesFile.getName(), e);
        }
        // Get decrypted file
        try {
            rulesFile = mBackupFile.getRulesFile(CryptoUtils.MODE_NO_ENCRYPTION);
        } catch (IOException e) {
            throw new BackupException("Could not get decrypted rules file", e);
        }
        try (RulesImporter importer = new RulesImporter(Arrays.asList(RuleType.values()), new int[]{mUserId})) {
            importer.addRulesFromPath(rulesFile);
            importer.setPackagesToImport(Collections.singletonList(mPackageName));
            importer.applyRules(true);
        } catch (IOException e) {
            throw new BackupException("Failed to restore rules file.", e);
        }
    }

    @NonNull
    private Path[] getSourceFiles(@NonNull Path backupPath) {
        String mode = CryptoUtils.getExtension(mMetadata.crypto);
        return backupPath.listFiles((dir, name) -> name.startsWith(SOURCE_PREFIX) && name.endsWith(mode));
    }

    private void deleteFiles(@NonNull Path[] files) {
        for (Path file : files) {
            file.delete();
        }
    }

    @NonNull
    private Path[] getKeyStoreFiles(@NonNull Path backupPath) {
        String mode = CryptoUtils.getExtension(mMetadata.crypto);
        return backupPath.listFiles((dir, name) -> name.startsWith(KEYSTORE_PREFIX) && name.endsWith(mode));
    }

    @NonNull
    private Path[] getDataFiles(@NonNull Path backupPath, int index) {
        String mode = CryptoUtils.getExtension(mMetadata.crypto);
        final String dataPrefix = DATA_PREFIX + index;
        return backupPath.listFiles((dir, name) -> name.startsWith(dataPrefix) && name.endsWith(mode));
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