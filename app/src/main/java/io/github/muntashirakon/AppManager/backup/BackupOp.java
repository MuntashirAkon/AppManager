// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

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
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PermissionInfoCompat;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.appops.OpEntry;
import io.github.muntashirakon.AppManager.appops.PackageOps;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.db.entity.FileHash;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.magisk.MagiskDenyList;
import io.github.muntashirakon.AppManager.magisk.MagiskHide;
import io.github.muntashirakon.AppManager.magisk.MagiskProcess;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.rules.PseudoRules;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.ssaid.SsaidSettings;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

import static io.github.muntashirakon.AppManager.backup.BackupManager.CERT_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.DATA_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.ICON_FILE;
import static io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PLACEHOLDER;
import static io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.MASTER_KEY;
import static io.github.muntashirakon.AppManager.backup.BackupManager.SOURCE_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.getExt;

@WorkerThread
class BackupOp implements Closeable {
    static final String TAG = BackupOp.class.getSimpleName();

    @NonNull
    private final Context mContext = AppManager.getContext();
    @NonNull
    private final String mPackageName;
    @NonNull
    private final BackupFiles.BackupFile mBackupFile;
    @NonNull
    private final MetadataManager mMetadataManager;
    @NonNull
    private final BackupFlags mBackupFlags;
    @NonNull
    private final MetadataManager.Metadata mMetadata;
    @NonNull
    private final PackageInfo mPackageInfo;
    @NonNull
    private final ApplicationInfo mApplicationInfo;
    @NonNull
    private final Path mTempBackupPath;
    @UserIdInt
    private final int mUserId;
    @NonNull
    private final Crypto mCrypto;
    @NonNull
    private final BackupFiles.Checksum mChecksum;
    // We don't need privileged package manager here
    @NonNull
    private final PackageManager mPm = mContext.getPackageManager();

    BackupOp(@NonNull String packageName, @NonNull MetadataManager metadataManager, @NonNull BackupFlags backupFlags,
             @NonNull BackupFiles.BackupFile backupFile, @UserIdInt int userId) throws BackupException {
        this.mPackageName = packageName;
        this.mBackupFile = backupFile;
        this.mUserId = userId;
        this.mMetadataManager = metadataManager;
        this.mBackupFlags = backupFlags;
        this.mTempBackupPath = this.mBackupFile.getBackupPath();
        try {
            mPackageInfo = PackageManagerCompat.getPackageInfo(this.mPackageName,
                    PackageManager.GET_META_DATA | PackageUtils.flagSigningInfo
                            | PackageManager.GET_PERMISSIONS, userId);
            this.mApplicationInfo = mPackageInfo.applicationInfo;
            // Override existing metadata
            this.mMetadata = this.mMetadataManager.setupMetadata(mPackageInfo, userId, backupFlags);
        } catch (Exception e) {
            this.mBackupFile.cleanup();
            throw new BackupException("Failed to setup metadata.", e);
        }
        try {
            // Setup crypto
            CryptoUtils.setupCrypto(this.mMetadata);
            this.mCrypto = CryptoUtils.getCrypto(mMetadata);
        } catch (CryptoException e) {
            this.mBackupFile.cleanup();
            throw new BackupException("Failed to get crypto " + mMetadata.crypto, e);
        }
        try {
            this.mChecksum = new BackupFiles.Checksum(
                    this.mBackupFile.getChecksumFile(CryptoUtils.MODE_NO_ENCRYPTION),
                    "w");
            String[] certChecksums = PackageUtils.getSigningCertChecksums(mMetadata.checksumAlgo, mPackageInfo, false);
            for (int i = 0; i < certChecksums.length; ++i) {
                mChecksum.add(CERT_PREFIX + i, certChecksums[i]);
            }
        } catch (Throwable e) {
            this.mBackupFile.cleanup();
            throw new BackupException("Failed to create checksum file.", e);
        }
    }

    @Override
    public void close() {
        mCrypto.close();
    }

    void runBackup() throws BackupException {
        boolean backupSuccess = false;
        try {
            // Fail backup if the app has items in Android KeyStore and backup isn't enabled
            if (mBackupFlags.backupData()
                    && mMetadata.keyStore
                    && !AppPref.getBoolean(AppPref.PrefKey.PREF_BACKUP_ANDROID_KEYSTORE_BOOL)) {
                throw new BackupException("The app has keystore items and KeyStore backup isn't enabled.");
            }
            // Backup icon
            backupIcon();
            // Backup source
            if (mBackupFlags.backupApkFiles()) {
                backupApkFiles();
            }
            // Backup data
            if (mBackupFlags.backupData()) {
                backupData();
                // Backup KeyStore
                if (mMetadata.keyStore) backupKeyStore();
            }
            // Backup extras
            if (mBackupFlags.backupExtras()) {
                backupExtras();
            }
            // Export rules
            if (mMetadata.hasRules) {
                backupRules();
            }
            // Set backup time
            mMetadata.backupTime = System.currentTimeMillis();
            // Write modified metadata
            mMetadataManager.setMetadata(mMetadata);
            try {
                mMetadataManager.writeMetadata(mBackupFile);
            } catch (IOException e) {
                throw new BackupException("Failed to write metadata.", e);
            }
            // Store checksum for metadata
            try {
                mChecksum.add(MetadataManager.META_FILE, DigestUtils.getHexDigest(mMetadata.checksumAlgo,
                        mBackupFile.getMetadataFile()));
            } catch (IOException e) {
                throw new BackupException("Failed to generate checksum for meta.json", e);
            }
            mChecksum.close();
            // Encrypt checksum
            try {
                Path checksumFile = mBackupFile.getChecksumFile(CryptoUtils.MODE_NO_ENCRYPTION);
                encrypt(new Path[]{checksumFile});
            } catch (IOException e) {
                throw new BackupException("Failed to write checksums.txt", e);
            }
            // Replace current backup
            try {
                mBackupFile.commit();
            } catch (IOException e) {
                throw new BackupException("Could not finalise backup.", e);
            }
            backupSuccess = true;
        } catch (BackupException e) {
            throw e;
        } catch (Throwable th) {
            throw new BackupException("Unknown error occurred.", th);
        } finally {
            if (!backupSuccess) {
                mBackupFile.cleanup();
            }
        }
    }

    private void backupIcon() {
        try {
            Path iconFile = mTempBackupPath.createNewFile(ICON_FILE, null);
            try (OutputStream outputStream = iconFile.openOutputStream()) {
                Bitmap bitmap = FileUtils.getBitmapFromDrawable(mApplicationInfo.loadIcon(mPm));
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                outputStream.flush();
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not back up icon.");
        }
    }

    private void backupApkFiles() throws BackupException {
        Path dataAppPath = OsEnvironment.getDataAppDirectory();
        final String sourceBackupFilePrefix = SOURCE_PREFIX + getExt(mMetadata.tarType);
        Path sourceDir = Paths.get(PackageUtils.getSourceDir(mApplicationInfo));
        if (dataAppPath.equals(sourceDir)) {
            // APK located inside /data/app directory
            // Backup only the apk file (no split apk support for this type of apk)
            try {
                sourceDir = sourceDir.findFile(mMetadata.apkName);
            } catch (FileNotFoundException e) {
                throw new BackupException(mMetadata.apkName + " not found at " + sourceDir);
            }
        }
        Path[] sourceFiles;
        try {
            sourceFiles = TarUtils.create(mMetadata.tarType, sourceDir, mTempBackupPath, sourceBackupFilePrefix,
                    /* language=regexp */ new String[]{".*\\.apk"}, null, null, false).toArray(new Path[0]);
        } catch (Throwable th) {
            throw new BackupException("APK files backup is requested but no source directory has been backed up.", th);
        }
        try {
            sourceFiles = encrypt(sourceFiles);
        } catch (IOException e) {
            throw new BackupException("Failed to encrypt " + Arrays.toString(sourceFiles), e);
        }
        for (Path file : sourceFiles) {
            mChecksum.add(file.getName(), DigestUtils.getHexDigest(mMetadata.checksumAlgo, file));
        }
    }

    private void backupData() throws BackupException {
        String sourceBackupFilePrefix;
        Path[] dataFiles;
        // Store file hash in a separate thread
        new Thread(() -> {
            for (String dir : mMetadata.dataDirs) {
                FileHash fileHash = new FileHash();
                fileHash.path = dir;
                fileHash.hash = DigestUtils.getHexDigest(DigestUtils.SHA_256, new Path(mContext, dir));
                AppManager.getAppsDb().fileHashDao().insert(fileHash);
            }
        }).start();
        for (int i = 0; i < mMetadata.dataDirs.length; ++i) {
            sourceBackupFilePrefix = DATA_PREFIX + i + getExt(mMetadata.tarType);
            try {
                dataFiles = TarUtils.create(mMetadata.tarType, new Path(mContext, mMetadata.dataDirs[i]),
                                mTempBackupPath, sourceBackupFilePrefix, null, null,
                                BackupUtils.getExcludeDirs(!mBackupFlags.backupCache(), null), false)
                        .toArray(new Path[0]);
            } catch (Throwable th) {
                throw new BackupException("Failed to backup data directory at " + mMetadata.dataDirs[i], th);
            }
            try {
                dataFiles = encrypt(dataFiles);
            } catch (IOException e) {
                throw new BackupException("Failed to encrypt " + Arrays.toString(dataFiles));
            }
            for (Path file : dataFiles) {
                mChecksum.add(file.getName(), DigestUtils.getHexDigest(mMetadata.checksumAlgo, file));
            }
        }
    }

    private void backupKeyStore() throws BackupException {  // Called only when the app has an keystore item
        Path keyStorePath = KeyStoreUtils.getKeyStorePath(mContext, mUserId);
        try {
            Path masterKeyFile = KeyStoreUtils.getMasterKey(mContext, mUserId);
            // Master key exists, so take its checksum to verify it during the restore
            mChecksum.add(MASTER_KEY, DigestUtils.getHexDigest(mMetadata.checksumAlgo,
                    FileUtils.getFileContent(masterKeyFile).getBytes()));
        } catch (FileNotFoundException ignore) {
        }
        // Store the KeyStore files
        Path cachePath = new Path(mContext, FileUtils.getCachePath());
        List<String> cachedKeyStoreFileNames = new ArrayList<>();
        List<String> keyStoreFilters = new ArrayList<>();
        for (String keyStoreFileName : KeyStoreUtils.getKeyStoreFiles(mContext, mApplicationInfo.uid, mUserId)) {
            try {
                String newFileName = Utils.replaceOnce(keyStoreFileName, String.valueOf(mApplicationInfo.uid),
                        String.valueOf(KEYSTORE_PLACEHOLDER));
                FileUtils.copy(keyStorePath.findFile(keyStoreFileName), cachePath.findOrCreateFile(newFileName, null));
                cachedKeyStoreFileNames.add(newFileName);
                keyStoreFilters.add(Pattern.quote(newFileName));
            } catch (Throwable e) {
                throw new BackupException("Could not cache " + keyStoreFileName, e);
            }
        }
        if (cachedKeyStoreFileNames.size() == 0) {
            throw new BackupException("There were some KeyStore items but they couldn't be cached before taking a backup.");
        }
        String keyStorePrefix = KEYSTORE_PREFIX + getExt(mMetadata.tarType);
        Path[] backedUpKeyStoreFiles;
        try {
            backedUpKeyStoreFiles = TarUtils.create(mMetadata.tarType, cachePath, mTempBackupPath, keyStorePrefix,
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
            backedUpKeyStoreFiles = encrypt(backedUpKeyStoreFiles);
        } catch (IOException e) {
            throw new BackupException("Failed to encrypt " + Arrays.toString(backedUpKeyStoreFiles), e);
        }
        for (Path file : backedUpKeyStoreFiles) {
            mChecksum.add(file.getName(), DigestUtils.getHexDigest(mMetadata.checksumAlgo, file));
        }
    }

    private void backupExtras() throws BackupException {
        PseudoRules rules = new PseudoRules(mPackageName, mUserId);
        Path miscFile;
        try {
            miscFile = mBackupFile.getMiscFile(CryptoUtils.MODE_NO_ENCRYPTION);
        } catch (IOException e) {
            throw new BackupException("Couldn't get misc.am.tsv", e);
        }
        // Backup permissions
        @NonNull String[] permissions = ArrayUtils.defeatNullable(mPackageInfo.requestedPermissions);
        int[] permissionFlags = mPackageInfo.requestedPermissionsFlags;
        List<OpEntry> opEntries = new ArrayList<>();
        try {
            List<PackageOps> packageOpsList = new AppOpsService().getOpsForPackage(mPackageInfo.applicationInfo.uid, mPackageName, null);
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
                int permFlags = PermissionCompat.getPermissionFlags(info.name, mPackageName, mUserId);
                rules.setPermission(permissions[i], isGranted, permFlags);
            } catch (PackageManager.NameNotFoundException | RemoteException ignore) {
            }
        }
        // Backup app ops
        for (OpEntry entry : opEntries) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            INotificationManager notificationManager = INotificationManager.Stub.asInterface(ProxyBinder.getService(Context.NOTIFICATION_SERVICE));
            try {
                List<ComponentName> notificationComponents;
                if (LocalServer.isAMServiceAlive()) {
                    notificationComponents = notificationManager.getEnabledNotificationListeners(mUserId);
                } else notificationComponents = Collections.emptyList();
                List<String> componentsForThisPkg = new ArrayList<>();
                for (ComponentName componentName : notificationComponents) {
                    if (mPackageName.equals(componentName.getPackageName())) {
                        componentsForThisPkg.add(componentName.getClassName());
                    }
                }
                for (String component : componentsForThisPkg) {
                    rules.setNotificationListener(component, true);
                }
            } catch (RemoteException ignore) {
            }
        }
        // Backup battery optimization
        String targetString = "user," + mPackageName + "," + mApplicationInfo.uid;
        Runner.Result result = Runner.runCommand(new String[]{"dumpsys", "deviceidle", "whitelist"});
        if (result.isSuccessful() && result.getOutput().contains(targetString)) {
            rules.setBatteryOptimization(false);
        }
        // Backup net policy
        int policies = NetworkPolicyManagerCompat.getUidPolicy(mApplicationInfo.uid);
        if (policies > 0) {
            // Store only if there is a policy
            rules.setNetPolicy(policies);
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
                String ssaid = new SsaidSettings(mPackageName, mApplicationInfo.uid).getSsaid();
                if (ssaid != null) rules.setSsaid(ssaid);
            } catch (IOException e) {
                // Ignore exception
                Log.e(TAG, e);
            }
        }
        rules.commitExternal(miscFile);
        if (!miscFile.exists()) return;
        try {
            encrypt(new Path[]{miscFile});
            // Overwrite with the new file
            miscFile = mBackupFile.getMiscFile(mMetadata.crypto);
            // Store checksum
            mChecksum.add(miscFile.getName(), DigestUtils.getHexDigest(mMetadata.checksumAlgo, miscFile));
        } catch (IOException e) {
            throw new BackupException("Couldn't get misc.am.tsv for generating checksum", e);
        }
    }

    private void backupRules() throws BackupException {
        try {
            Path rulesFile = mBackupFile.getRulesFile(CryptoUtils.MODE_NO_ENCRYPTION);
            try (OutputStream outputStream = rulesFile.openOutputStream();
                 ComponentsBlocker cb = ComponentsBlocker.getInstance(mPackageName, mUserId)) {
                ComponentUtils.storeRules(outputStream, cb.getAll(), true);
            }
            if (!rulesFile.exists()) return;
            encrypt(new Path[]{rulesFile});
            // Overwrite with the new file
            rulesFile = mBackupFile.getRulesFile(mMetadata.crypto);
            // Store checksum
            mChecksum.add(rulesFile.getName(), DigestUtils.getHexDigest(mMetadata.checksumAlgo, rulesFile));
        } catch (IOException e) {
            throw new BackupException("Rules backup is requested but encountered an error during fetching rules.", e);
        }
    }

    @NonNull
    private Path[] encrypt(@NonNull Path[] files) throws IOException {
        synchronized (Crypto.class) {
            mCrypto.encrypt(files);
            return mCrypto.getNewFiles();
        }
    }
}
