// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PLACEHOLDER;
import static io.github.muntashirakon.AppManager.backup.BackupManager.MASTER_KEY;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.GET_SIGNING_CERTIFICATES;

import android.app.AppOpsManager;
import android.app.INotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.system.ErrnoException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.installer.InstallerOptions;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.DeviceIdleManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
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
import io.github.muntashirakon.AppManager.rules.struct.FreezeRule;
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
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
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
    private final BackupMetadataV5.Info mBackupInfo;
    @NonNull
    private final BackupMetadataV5.Metadata mBackupMetadata;
    @NonNull
    private final BackupItems.BackupItem mBackupItem;
    @Nullable
    private PackageInfo mPackageInfo;
    private int mUid;
    @NonNull
    private final BackupItems.Checksum mChecksum;
    private final int mUserId;
    private boolean mIsInstalled;
    private boolean mRequiresRestart;

    RestoreOp(@NonNull String packageName, @NonNull BackupFlags requestedFlags,
              @NonNull BackupItems.BackupItem backupItem, int userId) throws BackupException {
        mPackageName = packageName;
        mRequestedFlags = requestedFlags;
        mBackupItem = backupItem;
        mUserId = userId;
        try {
            mBackupInfo = mBackupItem.getInfo();
            mBackupFlags = mBackupInfo.flags;
        } catch (IOException e) {
            mBackupItem.cleanup();
            throw new BackupException("Could not read backup info. Possibly due to a malformed json file.", e);
        }
        // Setup crypto
        if (!CryptoUtils.isAvailable(mBackupInfo.crypto)) {
            mBackupItem.cleanup();
            throw new BackupException("Mode " + mBackupInfo.crypto + " is currently unavailable.");
        }
        try {
            mBackupItem.setCrypto(mBackupInfo.getCrypto());
        } catch (CryptoException e) {
            mBackupItem.cleanup();
            throw new BackupException("Could not get crypto " + mBackupInfo.crypto, e);
        }
        try {
            mBackupMetadata = mBackupItem.getMetadata(mBackupInfo).metadata;
        } catch (IOException e) {
            mBackupItem.cleanup();
            throw new BackupException("Could not read backup metadata. Possibly due to a malformed json file.", e);
        }
        // Get checksums
        try {
            mChecksum = mBackupItem.getChecksum();
        } catch (Throwable e) {
            mBackupItem.cleanup();
            throw new BackupException("Failed to get checksums.", e);
        }
        // Verify metadata
        if (!requestedFlags.skipSignatureCheck()) {
            try {
                verifyMetadata();
            } catch (BackupException e) {
                mBackupItem.cleanup();
                throw e;
            }
        }
        // Check user handle
        if (mBackupInfo.userId != userId) {
            Log.w(TAG, "Using different user handle.");
        }
        // Get package info
        mPackageInfo = null;
        try {
            mPackageInfo = PackageManagerCompat.getPackageInfo(packageName, GET_SIGNING_CERTIFICATES
                    | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
            mUid = Objects.requireNonNull(mPackageInfo.applicationInfo).uid;
        } catch (Exception ignore) {
        }
        mIsInstalled = mPackageInfo != null;
    }

    @Override
    public void close() {
        Log.d(TAG, "Close called");
        mChecksum.close();
        mBackupItem.cleanup();
    }

    void runRestore(@Nullable ProgressHandler progressHandler) throws BackupException {
        try {
            if (mRequestedFlags.backupData() && mBackupMetadata.keyStore && !mRequestedFlags.skipSignatureCheck()) {
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
                if (mBackupMetadata.keyStore) {
                    restoreKeyStore();
                }
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

    private void verifyMetadata() throws BackupException {
        boolean isV5AndUp = mBackupItem.isV5AndUp();
        if (isV5AndUp) {
            Path infoFile;
            try {
                infoFile = mBackupItem.getInfoFile();
            } catch (IOException e) {
                throw new BackupException("Could not get metadata file.", e);
            }
            String checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, infoFile);
            if (!checksum.equals(mChecksum.get(infoFile.getName()))) {
                throw new BackupException("Couldn't verify metadata file." +
                        "\nFile: " + infoFile +
                        "\nFound: " + checksum +
                        "\nRequired: " + mChecksum.get(infoFile.getName()));
            }
        }
        Path metadataFile;
        try {
            metadataFile = isV5AndUp ? mBackupItem.getMetadataV5File() : mBackupItem.getMetadataV2File();
        } catch (IOException e) {
            throw new BackupException("Could not get metadata file.", e);
        }
        String checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, metadataFile);
        if (!checksum.equals(mChecksum.get(metadataFile.getName()))) {
            throw new BackupException("Couldn't verify metadata file." +
                    "\nFile: " + metadataFile +
                    "\nFound: " + checksum +
                    "\nRequired: " + mChecksum.get(metadataFile.getName()));
        }
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
            else
                throw new BackupException("Master key existed when the checksum was made but now it doesn't.");
        }
        if (oldChecksum == null) {
            throw new BackupException("Master key exists but it didn't exist when the backup was made.");
        }
        String newChecksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, masterKey.getContentAsString().getBytes());
        if (!newChecksum.equals(oldChecksum)) {
            throw new BackupException("Checksums for master key did not match.");
        }
    }

    private void restoreApkFiles() throws BackupException {
        if (!mBackupFlags.backupApkFiles()) {
            throw new BackupException("APK restore is requested but backup doesn't contain any source files.");
        }
        Path[] backupSourceFiles = mBackupItem.getSourceFiles();
        if (backupSourceFiles.length == 0) {
            // No source backup found
            throw new BackupException("Source restore is requested but there are no source files.");
        }
        boolean isVerified = true;
        if (mPackageInfo != null) {
            // Check signature of the installed app
            List<String> certChecksumList = Arrays.asList(PackageUtils.getSigningCertChecksums(mBackupInfo.checksumAlgo, mPackageInfo, false));
            String[] certChecksums = BackupItems.Checksum.getCertChecksums(mChecksum);
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
                checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, file);
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
            packageStagingDirectory = mBackupItem.getUnencryptedBackupPath();
        }
        synchronized (sLock) {
            // Setup apk files, including split apk
            final int splitCount = mBackupMetadata.splitConfigs.length;
            String[] allApkNames = new String[splitCount + 1];
            Path[] allApks = new Path[splitCount + 1];
            try {
                Path baseApk = packageStagingDirectory.createNewFile(mBackupMetadata.apkName, null);
                allApks[0] = baseApk;
                allApkNames[0] = mBackupMetadata.apkName;
                for (int i = 1; i < allApkNames.length; ++i) {
                    allApkNames[i] = mBackupMetadata.splitConfigs[i - 1];
                    allApks[i] = packageStagingDirectory.createNewFile(allApkNames[i], null);
                }
            } catch (IOException e) {
                throw new BackupException("Could not create staging files", e);
            }
            // Decrypt sources
            try {
                backupSourceFiles = mBackupItem.decrypt(backupSourceFiles);
            } catch (IOException e) {
                throw new BackupException("Failed to decrypt " + Arrays.toString(backupSourceFiles), e);
            }
            // Extract apk files to the package staging directory
            try {
                TarUtils.extract(mBackupInfo.tarType, backupSourceFiles, packageStagingDirectory, allApkNames, null, null);
            } catch (Throwable th) {
                throw new BackupException("Failed to extract the apk file(s).", th);
            }
            // A normal update will do it now
            InstallerOptions options = InstallerOptions.getDefault();
            options.setInstallerName(mBackupMetadata.installer);
            options.setUserId(mUserId);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                options.setInstallScenario(PackageManager.INSTALL_SCENARIO_BULK);
            }
            AtomicReference<String> status = new AtomicReference<>();
            PackageInstallerCompat packageInstaller = PackageInstallerCompat.getNewInstance();
            packageInstaller.setOnInstallListener(new PackageInstallerCompat.OnInstallListener() {
                @Override
                public void onStartInstall(int sessionId, String packageName) {
                }

                // MIUI-begin: MIUI 12.5+ workaround
                @Override
                public void onAnotherAttemptInMiui(@Nullable ApkFile apkFile) {
                    // This works because the parent install method still remains active until a final status is
                    // received after all the attempts are finished, which is, then, returned to the parent.
                    packageInstaller.install(allApks, mPackageName, options);
                }
                // MIUI-end

                // HyperOS-begin: HyperOS 2.0+ workaround
                @Override
                public void onSecondAttemptInHyperOsWithoutInstaller(@Nullable ApkFile apkFile) {
                    // This works because the parent install method still remains active until a final status is
                    // received after all the attempts are finished, which is, then, returned to the parent.
                    options.setInstallerName("com.android.shell");
                    packageInstaller.install(allApks, mPackageName, options);
                }
                // HyperOS-end

                @Override
                public void onFinishedInstall(int sessionId, String packageName, int result, @Nullable String blockingPackage, @Nullable String statusMessage) {
                    status.set(statusMessage);
                }
            });
            try {
                if (!packageInstaller.install(allApks, mPackageName, options)) {
                    String statusMessage;
                    if (!isVerified) {
                        // Previously installed app was uninstalled.
                        statusMessage = "Couldn't perform a re-installation";
                    } else {
                        statusMessage = "Couldn't perform an installation";
                    }
                    if (status.get() != null) {
                        statusMessage += ": " + status.get();
                    } else statusMessage += ".";
                    throw new BackupException(statusMessage);
                }
            } finally {
                deleteFiles(allApks);  // Clean up apk files
            }
            // Get package info, again
            try {
                mPackageInfo = PackageManagerCompat.getPackageInfo(mPackageName, GET_SIGNING_CERTIFICATES
                        | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, mUserId);
                mUid = Objects.requireNonNull(mPackageInfo.applicationInfo).uid;
                mIsInstalled = true;
            } catch (Exception e) {
                throw new BackupException("Apparently the install wasn't complete in the previous section.", e);
            }
        }
    }

    private void restoreKeyStore() throws BackupException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // keystore v2 is not supported.
            Log.w(TAG, "Ignoring KeyStore backups for %s", mPackageName);
            return;
        }
        if (mPackageInfo == null) {
            throw new BackupException("KeyStore restore is requested but the app isn't installed.");
        }
        Path[] keyStoreFiles = mBackupItem.getKeyStoreFiles();
        if (keyStoreFiles.length == 0) {
            throw new BackupException("KeyStore files should've existed but they didn't");
        }
        if (!mRequestedFlags.skipSignatureCheck()) {
            String checksum;
            for (Path file : keyStoreFiles) {
                checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, file);
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
            keyStoreFiles = mBackupItem.decrypt(keyStoreFiles);
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
            TarUtils.extract(mBackupInfo.tarType, keyStoreFiles, keyStorePath, null, null, null);
            // Restore folder permission
            Paths.chown(keyStorePath, uidGidPair.uid, uidGidPair.gid);
            //noinspection OctalInteger
            Paths.chmod(keyStorePath, mode & 0777);
        } catch (Throwable th) {
            throw new BackupException("Failed to restore the KeyStore files.", th);
        }
        // Rename files
        List<String> keyStoreFileNames = KeyStoreUtils.getKeyStoreFiles(KEYSTORE_PLACEHOLDER, mUserId);
        for (String keyStoreFileName : keyStoreFileNames) {
            try {
                String newFilename = Utils.replaceOnce(keyStoreFileName, String.valueOf(KEYSTORE_PLACEHOLDER), String.valueOf(mUid));
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
            for (int i = 0; i < mBackupMetadata.dataDirs.length; ++i) {
                Path[] dataFiles = mBackupItem.getDataFiles(i);
                if (dataFiles.length == 0) {
                    throw new BackupException("Data restore is requested but there are no data files for index " + i + ".");
                }
                for (Path file : dataFiles) {
                    checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, file);
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
        for (int i = 0; i < mBackupMetadata.dataDirs.length; ++i) {
            String dataSource = BackupUtils.getWritableDataDirectory(mBackupMetadata.dataDirs[i], mBackupInfo.userId, mUserId);
            BackupDataDirectoryInfo dataDirectoryInfo = BackupDataDirectoryInfo.getInfo(dataSource, mUserId);
            Path dataSourceFile = dataDirectoryInfo.getDirectory();

            Path[] dataFiles = mBackupItem.getDataFiles(i);
            if (dataFiles.length == 0) {
                throw new BackupException("Data restore is requested but there are no data files for index " + i + ".");
            }
            UidGidPair uidGidPair = dataSourceFile.getUidGid();
            if (uidGidPair == null) {
                // Fallback to app UID
                uidGidPair = new UidGidPair(mUid, mUid);
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
                    if (!Utils.isRoboUnitTest()) {
                        throw new BackupException("External directory containing " + dataSource + " is not mounted.");
                    } // else Skip checking for mounted partition for robolectric tests
                }
                if (!dataSourceFile.mkdirs()) {
                    throw new BackupException("Could not create directory " + dataSourceFile);
                }
                if (!dataDirectoryInfo.isExternal()) {
                    // Restore UID, GID
                    dataSourceFile.setUidGid(uidGidPair);
                }
            }
            // Decrypt data
            try {
                dataFiles = mBackupItem.decrypt(dataFiles);
            } catch (IOException e) {
                throw new BackupException("Failed to decrypt " + Arrays.toString(dataFiles), e);
            }
            // Extract data to the data directory
            try {
                String publicSourceDir = new File(Objects.requireNonNull(mPackageInfo.applicationInfo).publicSourceDir).getParent();
                TarUtils.extract(mBackupInfo.tarType, dataFiles, dataSourceFile, null, BackupUtils
                        .getExcludeDirs(!mRequestedFlags.backupCache(), null), publicSourceDir);
            } catch (Throwable th) {
                throw new BackupException("Failed to restore data files for index " + i + ".", th);
            }
            // Restore UID and GID
            if (!Runner.runCommand(String.format(Locale.ROOT, "chown -R %d:%d \"%s\"", uidGidPair.uid, uidGidPair.gid, dataSourceFile.getFilePath())).isSuccessful()) {
                if (!Utils.isRoboUnitTest()) {
                    throw new BackupException("Failed to restore ownership info for index " + i + ".");
                } // else Don't care about permissions
            }
            // Restore context
            if (!dataDirectoryInfo.isExternal()) {
                Runner.runCommand(new String[]{"restorecon", "-R", dataSourceFile.getFilePath()});
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
                            appOpsManager.setMode(Integer.parseInt(entry.name), mUid, mPackageName,
                                    ((AppOpRule) entry).getMode());
                        }
                        break;
                    case NET_POLICY:
                        if (canChangeNetPolicy) {
                            NetworkPolicyManagerCompat.setUidPolicy(mUid,
                                    ((NetPolicyRule) entry).getPolicies());
                        }
                        break;
                    case PERMISSION: {
                        PermissionRule permissionRule = (PermissionRule) entry;
                        Permission permission = permissionRule.getPermission(true);
                        permission.setAppOpAllowed(permission.getAppOp() != AppOpsManagerCompat.OP_NONE && appOpsManager
                                .checkOperation(permission.getAppOp(), mUid, mPackageName) == AppOpsManager.MODE_ALLOWED);
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
                            MagiskHide.apply(magiskHideRule.getMagiskProcess(), false);
                        } else {
                            // Fall-back to Magisk DenyList
                            MagiskDenyList.apply(magiskHideRule.getMagiskProcess(), false);
                        }
                        break;
                    }
                    case MAGISK_DENY_LIST: {
                        MagiskDenyList.apply(((MagiskDenyListRule) entry).getMagiskProcess(), false);
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
                            new SsaidSettings(mUserId).setSsaid(mPackageName, mUid,
                                    ((SsaidRule) entry).getSsaid());
                            mRequiresRestart = true;
                        }
                        break;
                    case FREEZE:
                        int freezeType = ((FreezeRule) entry).getFreezeType();
                        FreezeUtils.storeFreezeMethod(mPackageName, freezeType);
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
            miscFile = mBackupItem.getMiscFile();
        } catch (IOException e) {
            // There are no permissions, just skip
            return;
        }
        if (!mRequestedFlags.skipSignatureCheck()) {
            String checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, miscFile);
            if (!checksum.equals(mChecksum.get(miscFile.getName()))) {
                throw new BackupException("Couldn't verify misc file." +
                        "\nFile: " + miscFile +
                        "\nFound: " + checksum +
                        "\nRequired: " + mChecksum.get(miscFile.getName()));
            }
        }
        // Decrypt permission file
        try {
            miscFile = mBackupItem.decrypt(new Path[]{miscFile})[0];
        } catch (IOException | IndexOutOfBoundsException e) {
            throw new BackupException("Failed to decrypt " + miscFile.getName(), e);
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
            rulesFile = mBackupItem.getRulesFile();
        } catch (IOException e) {
            if (mBackupMetadata.hasRules) {
                throw new BackupException("Rules file is missing.", e);
            } else {
                // There are no rules, just skip
                return;
            }
        }
        if (!mRequestedFlags.skipSignatureCheck()) {
            String checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, rulesFile);
            if (!checksum.equals(mChecksum.get(rulesFile.getName()))) {
                throw new BackupException("Couldn't verify permission file." +
                        "\nFile: " + rulesFile +
                        "\nFound: " + checksum +
                        "\nRequired: " + mChecksum.get(rulesFile.getName()));
            }
        }
        // Decrypt rules file
        try {
            rulesFile = mBackupItem.decrypt(new Path[]{rulesFile})[0];
        } catch (IOException | IndexOutOfBoundsException e) {
            throw new BackupException("Failed to decrypt " + rulesFile.getName(), e);
        }
        try (RulesImporter importer = new RulesImporter(Arrays.asList(RuleType.values()), new int[]{mUserId})) {
            importer.addRulesFromPath(rulesFile);
            importer.setPackagesToImport(Collections.singletonList(mPackageName));
            importer.applyRules(true);
        } catch (IOException e) {
            throw new BackupException("Failed to restore rules file.", e);
        }
    }

    private void deleteFiles(@NonNull Path[] files) {
        for (Path file : files) {
            file.delete();
        }
    }
}