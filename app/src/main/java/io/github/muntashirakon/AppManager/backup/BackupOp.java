// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static io.github.muntashirakon.AppManager.backup.BackupManager.CERT_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PLACEHOLDER;
import static io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.MASTER_KEY;
import static io.github.muntashirakon.AppManager.backup.BackupManager.SOURCE_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.getExt;
import static io.github.muntashirakon.AppManager.backup.BackupUtils.TAR_TYPES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.GET_SIGNING_CERTIFICATES;

import android.annotation.UserIdInt;
import android.app.INotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.content.pm.PermissionInfoCompat;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.BackupCompat;
import io.github.muntashirakon.AppManager.compat.DeviceIdleManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.magisk.MagiskDenyList;
import io.github.muntashirakon.AppManager.magisk.MagiskHide;
import io.github.muntashirakon.AppManager.magisk.MagiskProcess;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.rules.PseudoRules;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.ssaid.SsaidSettings;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.BitmapRandomizer;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ParcelFileDescriptorUtil;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@WorkerThread
class BackupOp implements Closeable {
    static final String TAG = BackupOp.class.getSimpleName();

    @NonNull
    private final String mPackageName;
    @NonNull
    private final BackupItems.BackupItem mBackupItem;
    @NonNull
    private final BackupFlags mBackupFlags;
    @NonNull
    private final BackupMetadataV5 mMetadata;
    @NonNull
    private final PackageInfo mPackageInfo;
    @NonNull
    private final ApplicationInfo mApplicationInfo;
    @UserIdInt
    private final int mUserId;
    @NonNull
    private final BackupItems.Checksum mChecksum;
    // We don't need privileged package manager here
    @NonNull
    private final PackageManager mPm;

    BackupOp(@NonNull String packageName, @NonNull BackupFlags backupFlags,
             @NonNull BackupItems.BackupItem backupItem, @UserIdInt int userId)
            throws BackupException {
        mPackageName = packageName;
        mBackupItem = backupItem;
        mUserId = userId;
        mBackupFlags = backupFlags;
        mPm = ContextUtils.getContext().getPackageManager();
        try {
            mPackageInfo = PackageManagerCompat.getPackageInfo(mPackageName,
                    PackageManager.GET_META_DATA | GET_SIGNING_CERTIFICATES | PackageManager.GET_PERMISSIONS
                            | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
            Objects.requireNonNull(mPackageInfo);
            mApplicationInfo = Objects.requireNonNull(mPackageInfo.applicationInfo);
            // Override existing metadata
            mMetadata = setupMetadataAndCrypto();
        } catch (Throwable e) {
            mBackupItem.cleanup();
            throw new BackupException("Failed to setup metadata.", e);
        }
        try {
            mChecksum = mBackupItem.getChecksum();
            String[] certChecksums = PackageUtils.getSigningCertChecksums(mMetadata.info.checksumAlgo, mPackageInfo, false);
            for (int i = 0; i < certChecksums.length; ++i) {
                mChecksum.add(CERT_PREFIX + i, certChecksums[i]);
            }
        } catch (Throwable e) {
            mBackupItem.cleanup();
            throw new BackupException("Failed to create checksum file.", e);
        }
    }

    @Override
    public void close() {
        mBackupItem.cleanup();
    }

    @NonNull
    public BackupMetadataV5 getMetadata() {
        return mMetadata;
    }

    void runBackup(@Nullable ProgressHandler progressHandler) throws BackupException {
        try {
            // Fail backup if the app has items in Android KeyStore and backup isn't enabled
            if (mBackupFlags.backupData() && mMetadata.metadata.keyStore && !Prefs.BackupRestore.backupAppsWithKeyStore()) {
                throw new BackupException("The app has keystore items and KeyStore backup isn't enabled.");
            }
            incrementProgress(progressHandler);
            // Backup icon
            backupIcon();
            // Backup source
            if (mBackupFlags.backupApkFiles()) {
                backupApkFiles();
                incrementProgress(progressHandler);
            }
            // Backup data
            if (mBackupFlags.backupData()) {
                backupData();
                // Backup KeyStore
                if (mMetadata.metadata.keyStore) {
                    backupKeyStore();
                }
                incrementProgress(progressHandler);
            }
            // Backup extras
            if (mBackupFlags.backupExtras()) {
                backupExtras();
                incrementProgress(progressHandler);
            }
            // Export rules
            if (mMetadata.metadata.hasRules) {
                backupRules();
                incrementProgress(progressHandler);
            }
            // Write modified metadata
            try {
                Map<String, String> filenameChecksumMap = MetadataManager.writeMetadata(mMetadata, mBackupItem);
                for (Map.Entry<String, String> entry : filenameChecksumMap.entrySet()) {
                    mChecksum.add(entry.getKey(), entry.getValue());
                }
            } catch (IOException e) {
                throw new BackupException("Failed to write metadata.", e);
            }
            mChecksum.close();
            // Encrypt checksum
            try {
                mBackupItem.encrypt(new Path[]{mChecksum.getFile()});
            } catch (IOException e) {
                throw new BackupException("Failed to write checksums.txt", e);
            }
            // Replace current backup
            try {
                mBackupItem.commit();
            } catch (IOException e) {
                throw new BackupException("Could not finalise backup.", e);
            }
        } catch (BackupException e) {
            throw e;
        } catch (Throwable th) {
            throw new BackupException("Unknown error occurred.", th);
        }
    }

    private static void incrementProgress(@Nullable ProgressHandler progressHandler) {
        if (progressHandler == null) {
            return;
        }
        float current = progressHandler.getLastProgress() + 1;
        progressHandler.postUpdate(current);
    }

    public BackupMetadataV5 setupMetadataAndCrypto() throws CryptoException {
        // We don't need to backup custom users or multiple backup flags
        mBackupFlags.removeFlag(BackupFlags.BACKUP_CUSTOM_USERS | BackupFlags.BACKUP_MULTIPLE);
        String backupName = mBackupItem.getBackupName();
        long backupTime = System.currentTimeMillis();
        String tarType = Prefs.BackupRestore.getCompressionMethod();
        // Verify tar type
        if (ArrayUtils.indexOf(TAR_TYPES, tarType) == -1) {
            // Unknown tar type, set default
            tarType = TarUtils.TAR_GZIP;
        }
        String crypto = CryptoUtils.getMode();
        BackupCryptSetupHelper cryptoHelper = new BackupCryptSetupHelper(crypto, MetadataManager.getCurrentBackupMetaVersion());
        mBackupItem.setCrypto(cryptoHelper.crypto);
        BackupMetadataV5.Info backupInfo = new BackupMetadataV5.Info(backupTime, mBackupFlags,
                mUserId, tarType, DigestUtils.SHA_256, crypto, cryptoHelper.getIv(),
                cryptoHelper.getAes(), cryptoHelper.getKeyIds());
        backupInfo.setBackupItem(mBackupItem);
        BackupMetadataV5.Metadata metadata = new BackupMetadataV5.Metadata(backupName);
        metadata.keyStore = KeyStoreUtils.hasKeyStore(mApplicationInfo.uid);
        metadata.label = mApplicationInfo.loadLabel(mPm).toString();
        metadata.packageName = mPackageName;
        metadata.versionName = mPackageInfo.versionName;
        metadata.versionCode = PackageInfoCompat.getLongVersionCode(mPackageInfo);
        metadata.apkName = new File(mApplicationInfo.sourceDir).getName();
        String[] dataDirs;
        if (mBackupFlags.backupAdbData()) {
            mBackupFlags.removeFlag(BackupFlags.BACKUP_INT_DATA);
            mBackupFlags.removeFlag(BackupFlags.BACKUP_EXT_DATA);
            List<String> defaultDirs = BackupUtils.getDataDirectories(mApplicationInfo, false,
                    false, mBackupFlags.backupMediaObb());
            dataDirs = new String[defaultDirs.size() + 1];
            for (int i = 0; i < defaultDirs.size(); ++i) {
                dataDirs[i] = defaultDirs.get(i);
            }
            dataDirs[defaultDirs.size()] = BackupManager.DATA_BACKUP_SPECIAL_ADB;
        } else {
            // Non-ADB backup: default
            dataDirs = BackupUtils.getDataDirectories(mApplicationInfo, mBackupFlags.backupInternalData(),
                    mBackupFlags.backupExternalData(), mBackupFlags.backupMediaObb()).toArray(new String[0]);
        }
        metadata.dataDirs = dataDirs;
        metadata.isSystem = (mApplicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        metadata.isSplitApk = false;
        try (ApkFile apkFile = ApkSource.getApkSource(mApplicationInfo).resolve()) {
            if (apkFile.isSplit()) {
                List<ApkFile.Entry> apkEntries = apkFile.getEntries();
                int splitCount = apkEntries.size() - 1;
                metadata.isSplitApk = splitCount > 0;
                metadata.splitConfigs = new String[splitCount];
                for (int i = 0; i < splitCount; ++i) {
                    metadata.splitConfigs[i] = apkEntries.get(i + 1).getFileName();
                }
            }
        } catch (ApkFile.ApkFileException e) {
            e.printStackTrace();
        }
        metadata.splitConfigs = ArrayUtils.defeatNullable(metadata.splitConfigs);
        metadata.hasRules = false;
        if (mBackupFlags.backupRules()) {
            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(mPackageInfo.packageName, mUserId, false)) {
                metadata.hasRules = cb.entryCount() > 0;
            }
        }
        metadata.installer = PackageManagerCompat.getInstallerPackageName(mPackageInfo.packageName, mUserId);
        return new BackupMetadataV5(backupInfo, metadata);
    }

    private void backupIcon() {
        try {
            Path iconFile = mBackupItem.getIconFile();
            try (OutputStream outputStream = iconFile.openOutputStream()) {
                Bitmap bitmap = UIUtils.getMutableBitmapFromDrawable(mApplicationInfo.loadIcon(mPm));
                BitmapRandomizer.randomizePixel(bitmap);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not back up icon.");
        }
    }

    private void backupApkFiles() throws BackupException {
        Path dataAppPath = OsEnvironment.getDataAppDirectory();
        final String sourceBackupFilePrefix = SOURCE_PREFIX + getExt(mMetadata.info.tarType);
        Path sourceDir = Paths.get(PackageUtils.getSourceDir(mApplicationInfo));
        if (dataAppPath.equals(sourceDir)) {
            // APK located inside /data/app directory
            // Backup only the apk file (no split apk support for this type of apk)
            try {
                sourceDir = sourceDir.findFile(mMetadata.metadata.apkName);
            } catch (FileNotFoundException e) {
                throw new BackupException(mMetadata.metadata.apkName + " not found at " + sourceDir);
            }
        }
        Path[] sourceFiles;
        try {
            sourceFiles = TarUtils.create(mMetadata.info.tarType, sourceDir, mBackupItem.getUnencryptedBackupPath(), sourceBackupFilePrefix,
                    /* language=regexp */ new String[]{".*\\.apk"}, null, null, false).toArray(new Path[0]);
        } catch (Throwable th) {
            throw new BackupException("APK files backup is requested but no source directory has been backed up.", th);
        }
        try {
            sourceFiles = mBackupItem.encrypt(sourceFiles);
        } catch (IOException e) {
            throw new BackupException("Failed to encrypt " + Arrays.toString(sourceFiles), e);
        }
        for (Path file : sourceFiles) {
            mChecksum.add(file.getName(), DigestUtils.getHexDigest(mMetadata.info.checksumAlgo, file));
        }
    }

    private void backupData() throws BackupException {
        for (int i = 0; i < mMetadata.metadata.dataDirs.length; ++i) {
            Path[] dataFiles;
            String backupDataDir = mMetadata.metadata.dataDirs[i];
            if (backupDataDir.equals(BackupManager.DATA_BACKUP_SPECIAL_ADB)) {
                // ADB backup
                dataFiles = backupAdb(i);
            } else {
                // Regular directory backup
                dataFiles = backupDirectory(backupDataDir, i);
            }
            try {
                dataFiles = mBackupItem.encrypt(dataFiles);
            } catch (IOException e) {
                throw new BackupException("Failed to encrypt " + Arrays.toString(dataFiles));
            }
            for (Path file : dataFiles) {
                mChecksum.add(file.getName(), DigestUtils.getHexDigest(mMetadata.info.checksumAlgo, file));
            }
        }
    }

    @NonNull
    private Path[] backupDirectory(@NonNull String dir, int index) throws BackupException {
        String filePrefix = BackupUtils.getDataFilePrefix(index, getExt(mMetadata.info.tarType));
        try {
            return TarUtils.create(mMetadata.info.tarType, Paths.get(dir),
                            mBackupItem.getUnencryptedBackupPath(),
                            filePrefix, null, null,
                            BackupUtils.getExcludeDirs(!mBackupFlags.backupCache()), false)
                    .toArray(new Path[0]);
        } catch (Throwable th) {
            throw new BackupException("Failed to backup data directory at " + dir, th);
        }
    }

    @NonNull
    private Path[] backupAdb(int index) throws BackupException {
        try {
            String filePrefix = BackupUtils.getDataFilePrefix(index, ".ab");
            Path abFile = mBackupItem.getUnencryptedBackupPath().createNewFile(filePrefix, null);
            try (OutputStream os = abFile.openOutputStream()) {
                ParcelFileDescriptor fd = ParcelFileDescriptorUtil.pipeTo(os);
                BackupCompat.adbBackup(mUserId, fd, false, false, false,
                        false, false, false, false, true,
                        new String[]{mPackageName});
            }
            return new Path[]{abFile};
        } catch (Throwable th) {
            throw new BackupException("Failed to backup ADB data.", th);
        }
    }

    private void backupKeyStore() throws BackupException {  // Called only when the app has an keystore item
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // keystore v2 is not supported.
            Log.w(TAG, "Ignoring KeyStore backups for %s", mPackageName);
            return;
        }
        Path keyStorePath = KeyStoreUtils.getKeyStorePath(mUserId);
        try {
            Path masterKeyFile = KeyStoreUtils.getMasterKey(mUserId);
            // Master key exists, so take its checksum to verify it during the restore
            mChecksum.add(MASTER_KEY, DigestUtils.getHexDigest(mMetadata.info.checksumAlgo,
                    masterKeyFile.getContentAsString().getBytes()));
        } catch (FileNotFoundException ignore) {
        }
        // Store the KeyStore files
        Path cachePath = Paths.get(FileUtils.getCachePath());
        List<String> cachedKeyStoreFileNames = new ArrayList<>();
        List<String> keyStoreFilters = new ArrayList<>();
        for (String keyStoreFileName : KeyStoreUtils.getKeyStoreFiles(mApplicationInfo.uid, mUserId)) {
            try {
                String newFileName = Utils.replaceOnce(keyStoreFileName, String.valueOf(mApplicationInfo.uid),
                        String.valueOf(KEYSTORE_PLACEHOLDER));
                IoUtils.copy(keyStorePath.findFile(keyStoreFileName), cachePath.findOrCreateFile(newFileName, null));
                cachedKeyStoreFileNames.add(newFileName);
                keyStoreFilters.add(Pattern.quote(newFileName));
            } catch (Throwable e) {
                throw new BackupException("Could not cache " + keyStoreFileName, e);
            }
        }
        if (cachedKeyStoreFileNames.isEmpty()) {
            throw new BackupException("There were some KeyStore items but they couldn't be cached before taking a backup.");
        }
        String keyStorePrefix = KEYSTORE_PREFIX + getExt(mMetadata.info.tarType);
        Path[] backedUpKeyStoreFiles;
        try {
            backedUpKeyStoreFiles = TarUtils.create(mMetadata.info.tarType, cachePath, mBackupItem.getUnencryptedBackupPath(), keyStorePrefix,
                            keyStoreFilters.toArray(new String[0]), null, null, false)
                    .toArray(new Path[0]);
        } catch (Throwable th) {
            throw new BackupException("Could not backup KeyStore item.", th);
        }
        // Remove cache
        for (String name : cachedKeyStoreFileNames) {
            try {
                cachePath.findFile(name).delete();
            } catch (FileNotFoundException ignore) {
            }
        }
        try {
            backedUpKeyStoreFiles = mBackupItem.encrypt(backedUpKeyStoreFiles);
        } catch (IOException e) {
            throw new BackupException("Failed to encrypt " + Arrays.toString(backedUpKeyStoreFiles), e);
        }
        for (Path file : backedUpKeyStoreFiles) {
            mChecksum.add(file.getName(), DigestUtils.getHexDigest(mMetadata.info.checksumAlgo, file));
        }
    }

    private void backupExtras() throws BackupException {
        PseudoRules rules = new PseudoRules(mPackageName, mUserId);
        Path miscFile;
        try {
            miscFile = mBackupItem.getMiscFile();
        } catch (IOException e) {
            throw new BackupException("Couldn't get misc.am.tsv", e);
        }
        // Backup permissions
        @NonNull String[] permissions = ArrayUtils.defeatNullable(mPackageInfo.requestedPermissions);
        int[] permissionFlags = ArrayUtils.defeatNullable(mPackageInfo.requestedPermissionsFlags);
        List<AppOpsManagerCompat.OpEntry> opEntries = new ArrayList<>();
        try {
            List<AppOpsManagerCompat.PackageOps> packageOpsList = new AppOpsManagerCompat()
                    .getOpsForPackage(mApplicationInfo.uid, mPackageName, null);
            if (packageOpsList.size() == 1) opEntries.addAll(packageOpsList.get(0).getOps());
        } catch (Exception ignore) {
        }
        PermissionInfo info;
        int basePermissionType;
        int protectionLevels;
        for (int i = 0; i < permissions.length; ++i) {
            try {
                info = mPm.getPermissionInfo(permissions[i], 0);
                basePermissionType = PermissionInfoCompat.getProtection(info);
                protectionLevels = PermissionInfoCompat.getProtectionFlags(info);
                if (basePermissionType != PermissionInfo.PROTECTION_DANGEROUS
                        && (protectionLevels & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) == 0) {
                    // Don't include permissions that are neither dangerous nor development
                    continue;
                }
                boolean isGranted = (permissionFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                int permFlags;
                if (SelfPermissions.checkGetGrantRevokeRuntimePermissions()) {
                    permFlags = PermissionCompat.getPermissionFlags(info.name, mPackageName, mUserId);
                } else permFlags = PermissionCompat.FLAG_PERMISSION_NONE;
                rules.setPermission(permissions[i], isGranted, permFlags);
            } catch (PackageManager.NameNotFoundException ignore) {
            }
        }
        // Backup app ops
        for (AppOpsManagerCompat.OpEntry entry : opEntries) {
            rules.setAppOp(entry.getOp(), entry.getMode());
        }
        // Backup MagiskHide data
        Collection<MagiskProcess> magiskHiddenProcesses = MagiskHide.getProcesses(mPackageInfo);
        for (MagiskProcess magiskProcess : magiskHiddenProcesses) {
            if (magiskProcess.isEnabled()) {
                rules.setMagiskHide(magiskProcess);
            }
        }
        // Backup Magisk DenyList data
        Collection<MagiskProcess> magiskDeniedProcesses = MagiskDenyList.getProcesses(mPackageInfo);
        for (MagiskProcess magiskProcess : magiskDeniedProcesses) {
            if (magiskProcess.isEnabled()) {
                rules.setMagiskDenyList(magiskProcess);
            }
        }
        // Backup allowed notification listeners aka BIND_NOTIFICATION_LISTENER_SERVICE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && SelfPermissions.checkNotificationListenerAccess()) {
            try {
                INotificationManager notificationManager = INotificationManager.Stub.asInterface(ProxyBinder.getService(Context.NOTIFICATION_SERVICE));
                List<ComponentName> notificationComponents = notificationManager.getEnabledNotificationListeners(mUserId);
                List<String> componentsForThisPkg = new ArrayList<>();
                for (ComponentName componentName : notificationComponents) {
                    if (mPackageName.equals(componentName.getPackageName())) {
                        componentsForThisPkg.add(componentName.getClassName());
                    }
                }
                for (String component : componentsForThisPkg) {
                    rules.setNotificationListener(component, true);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        // Backup battery optimization
        boolean batteryOptimized = DeviceIdleManagerCompat.isBatteryOptimizedApp(mPackageName);
        if (!batteryOptimized) {
            rules.setBatteryOptimization(false);
        }
        // Backup net policy
        if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_NETWORK_POLICY)) {
            int policies = ExUtils.requireNonNullElse(() -> NetworkPolicyManagerCompat.getUidPolicy(mApplicationInfo.uid), 0);
            if (policies > 0) {
                // Store only if there is a policy
                rules.setNetPolicy(policies);
            }
        }
        // Backup URI grants
        List<UriManager.UriGrant> uriGrants = new UriManager().getGrantedUris(mPackageName);
        if (uriGrants != null) {
            for (UriManager.UriGrant uriGrant : uriGrants) {
                if (uriGrant.targetUserId == mUserId) {
                    rules.setUriGrant(uriGrant);
                }
            }
        }
        // Backup SSAID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                String ssaid = new SsaidSettings(mUserId).getSsaid(mPackageName, mApplicationInfo.uid);
                if (ssaid != null) rules.setSsaid(ssaid);
            } catch (IOException e) {
                // Ignore exception
                Log.e(TAG, e);
            }
        }
        // Backup freezeType
        Integer freezeType = FreezeUtils.loadFreezeMethod(mPackageName);
        if (freezeType != null) {
            rules.setFreezeType(freezeType);
        }
        // Commit
        rules.commitExternal(miscFile);
        if (!miscFile.exists()) return;
        try {
            miscFile = mBackupItem.encrypt(new Path[]{miscFile})[0];
            // Store checksum
            mChecksum.add(miscFile.getName(), DigestUtils.getHexDigest(mMetadata.info.checksumAlgo, miscFile));
        } catch (IOException | IndexOutOfBoundsException e) {
            throw new BackupException("Couldn't get misc.am.tsv for generating checksum", e);
        }
    }

    private void backupRules() throws BackupException {
        try {
            Path rulesFile = mBackupItem.getRulesFile();
            try (OutputStream outputStream = rulesFile.openOutputStream();
                 ComponentsBlocker cb = ComponentsBlocker.getInstance(mPackageName, mUserId)) {
                ComponentUtils.storeRules(outputStream, cb.getAll(), true);
            }
            if (!rulesFile.exists()) return;
            rulesFile = mBackupItem.encrypt(new Path[]{rulesFile})[0];
            // Store checksum
            mChecksum.add(rulesFile.getName(), DigestUtils.getHexDigest(mMetadata.info.checksumAlgo, rulesFile));
        } catch (IOException | IndexOutOfBoundsException e) {
            throw new BackupException("Rules backup is requested but encountered an error during fetching rules.", e);
        }
    }
}
