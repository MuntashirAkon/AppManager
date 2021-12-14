// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.appops.OpEntry;
import io.github.muntashirakon.AppManager.appops.PackageOps;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.db.entity.FileHash;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.rules.PseudoRules;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.servermanager.PermissionCompat;
import io.github.muntashirakon.AppManager.ssaid.SsaidSettings;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.MagiskUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.ProxyFile;

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
    private final Context context = AppManager.getContext();
    @NonNull
    private final String packageName;
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
    private final Path tmpBackupPath;
    private final int userHandle;
    @NonNull
    private final Crypto crypto;
    @NonNull
    private final BackupFiles.Checksum checksum;
    // We don't need privileged package manager here
    @NonNull
    private final PackageManager pm = context.getPackageManager();

    BackupOp(@NonNull String packageName, @NonNull MetadataManager metadataManager,
             @NonNull BackupFlags backupFlags, @NonNull BackupFiles.BackupFile backupFile,
             int userHandle) throws BackupException {
        this.packageName = packageName;
        this.backupFile = backupFile;
        this.userHandle = userHandle;
        this.metadataManager = metadataManager;
        this.backupFlags = backupFlags;
        this.tmpBackupPath = this.backupFile.getBackupPath();
        try {
            packageInfo = PackageManagerCompat.getPackageInfo(this.packageName,
                    PackageManager.GET_META_DATA | PackageUtils.flagSigningInfo
                            | PackageManager.GET_PERMISSIONS, userHandle);
            this.applicationInfo = packageInfo.applicationInfo;
            // Override existing metadata
            this.metadata = this.metadataManager.setupMetadata(packageInfo, userHandle, backupFlags);
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
        } catch (Throwable e) {
            this.backupFile.cleanup();
            throw new BackupException("Failed to create checksum file.", e);
        }
    }

    @Override
    public void close() {
        crypto.close();
    }

    void runBackup() throws BackupException {
        // Fail backup if the app has items in Android KeyStore and backup isn't enabled
        if (backupFlags.backupData() && metadata.keyStore) {
            if (!(boolean) AppPref.get(AppPref.PrefKey.PREF_BACKUP_ANDROID_KEYSTORE_BOOL)) {
                try {
                    throw new BackupException("The app has keystore items and KeyStore backup isn't enabled.");
                } finally {
                    backupFile.cleanup();
                }
            }
        }
        try {
            // Backup icon
            backupIcon();
            // Backup source
            if (backupFlags.backupApkFiles()) backupApkFiles();
            // Backup data
            if (backupFlags.backupData()) {
                backupData();
                // Backup KeyStore
                if (metadata.keyStore) backupKeyStore();
            }
            // Backup permissions
            if (backupFlags.backupExtras()) backupExtras();
            // Export rules
            if (metadata.hasRules) backupRules();
        } catch (BackupException e) {
            try {
                throw e;
            } finally {
                backupFile.cleanup();
            }
        }
        // Set backup time
        metadata.backupTime = System.currentTimeMillis();
        // Write modified metadata
        metadataManager.setMetadata(metadata);
        try {
            metadataManager.writeMetadata(backupFile);
        } catch (IOException e) {
            try {
                throw new BackupException("Failed to write metadata.", e);
            } finally {
                backupFile.cleanup();
            }
        }
        // Store checksum for metadata
        try {
            checksum.add(MetadataManager.META_FILE, DigestUtils.getHexDigest(metadata.checksumAlgo,
                    backupFile.getMetadataFile()));
        } catch (IOException e) {
            try {
                throw new BackupException("Failed to get meta.json");
            } finally {
                backupFile.cleanup();
            }
        }
        checksum.close();
        // Encrypt checksum
        try {
            Path checksumFile = backupFile.getChecksumFile(CryptoUtils.MODE_NO_ENCRYPTION);
            if (!crypto.encrypt(new Path[]{checksumFile})) {
                try {
                    throw new BackupException("Failed to encrypt " + checksumFile.getName());
                } finally {
                    backupFile.cleanup();
                }
            }
        } catch (IOException e) {
            try {
                throw new BackupException("Failed to get checksum.txt");
            } finally {
                backupFile.cleanup();
            }
        }
        // Replace current backup:
        // There's hardly any chance of getting a false here but checks are done anyway.
        if (!backupFile.commit()) {
            try {
                throw new BackupException("Unknown error occurred. This message should never be printed.");
            } finally {
                backupFile.cleanup();
            }
        }
    }

    private void backupIcon() {
        try {
            Path iconFile = tmpBackupPath.createNewFile(ICON_FILE, null);
            try (OutputStream outputStream = iconFile.openOutputStream()) {
                Bitmap bitmap = FileUtils.getBitmapFromDrawable(applicationInfo.loadIcon(pm));
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                outputStream.flush();
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not back up icon.");
        }
    }

    private void backupApkFiles() throws BackupException {
        final File dataAppPath = OsEnvironment.getDataAppDirectory();
        final String sourceBackupFilePrefix = SOURCE_PREFIX + getExt(metadata.tarType);
        String sourceDir = PackageUtils.getSourceDir(applicationInfo);
        if (dataAppPath.getAbsolutePath().equals(sourceDir)) {
            // Backup only the apk file (no split apk support for this type of apk)
            sourceDir = new File(sourceDir, metadata.apkName).getAbsolutePath();
        }
        Path[] sourceFiles;
        try {
            sourceFiles = TarUtils.create(metadata.tarType, new Path(context, new File(sourceDir)), tmpBackupPath,
                    sourceBackupFilePrefix, /* language=regexp */ new String[]{".*\\.apk"}, null, null,
                    false).toArray(new Path[0]);
        } catch (Throwable th) {
            throw new BackupException("APK files backup is requested but no source directory has been backed up.", th);
        }
        if (!crypto.encrypt(sourceFiles)) {
            throw new BackupException("Failed to encrypt " + Arrays.toString(sourceFiles));
        }
        // Overwrite with the new files
        sourceFiles = crypto.getNewFiles();
        for (Path file : sourceFiles) {
            checksum.add(file.getName(), DigestUtils.getHexDigest(metadata.checksumAlgo, file));
        }
    }

    private void backupData() throws BackupException {
        String sourceBackupFilePrefix;
        Path[] dataFiles;
        // Store file hash in a separate thread
        new Thread(() -> {
            for (String dir : metadata.dataDirs) {
                FileHash fileHash = new FileHash();
                fileHash.path = dir;
                fileHash.hash = DigestUtils.getHexDigest(DigestUtils.SHA_256, new ProxyFile(dir));
                AppManager.getDb().fileHashDao().insert(fileHash);
            }
        }).start();
        for (int i = 0; i < metadata.dataDirs.length; ++i) {
            sourceBackupFilePrefix = DATA_PREFIX + i + getExt(metadata.tarType);
            try {
                dataFiles = TarUtils.create(metadata.tarType, new Path(context, new ProxyFile(metadata.dataDirs[i])),
                        tmpBackupPath, sourceBackupFilePrefix, null, null,
                        BackupUtils.getExcludeDirs(!backupFlags.backupCache(), null), false)
                        .toArray(new Path[0]);
            } catch (Throwable th) {
                throw new BackupException("Failed to backup data directory at " + metadata.dataDirs[i], th);
            }
            if (!crypto.encrypt(dataFiles)) {
                throw new BackupException("Failed to encrypt " + Arrays.toString(dataFiles));
            }
            // Overwrite with the new files
            dataFiles = crypto.getNewFiles();
            for (Path file : dataFiles) {
                checksum.add(file.getName(), DigestUtils.getHexDigest(metadata.checksumAlgo, file));
            }
        }
    }

    private void backupKeyStore() throws BackupException {  // Called only when the app has an keystore item
        Path keyStorePath = new Path(context, KeyStoreUtils.getKeyStorePath(userHandle));
        ProxyFile masterKeyFile = KeyStoreUtils.getMasterKey(userHandle);
        if (masterKeyFile.exists()) {
            // Master key exists, so take it's checksum to verify it during the restore
            checksum.add(MASTER_KEY, DigestUtils.getHexDigest(metadata.checksumAlgo,
                    FileUtils.getFileContent(masterKeyFile).getBytes()));
        }
        // Store the KeyStore files
        Path cachePath;
        try {
            cachePath = new Path(context, FileUtils.getCachePath());
        } catch (IOException e) {
            throw new BackupException("Could not get cache path", e);
        }
        List<String> cachedKeyStoreFileNames = new ArrayList<>();
        List<String> keyStoreFilters = new ArrayList<>();
        for (String keyStoreFileName : KeyStoreUtils.getKeyStoreFiles(applicationInfo.uid, userHandle)) {
            try {
                String newFileName = Utils.replaceOnce(keyStoreFileName, String.valueOf(applicationInfo.uid),
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
        String keyStorePrefix = KEYSTORE_PREFIX + getExt(metadata.tarType);
        Path[] backedUpKeyStoreFiles;
        try {
            backedUpKeyStoreFiles = TarUtils.create(metadata.tarType, cachePath, tmpBackupPath, keyStorePrefix,
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
        if (!crypto.encrypt(backedUpKeyStoreFiles)) {
            throw new BackupException("Failed to encrypt " + Arrays.toString(backedUpKeyStoreFiles));
        }
        // Overwrite with the new files
        backedUpKeyStoreFiles = crypto.getNewFiles();
        for (Path file : backedUpKeyStoreFiles) {
            checksum.add(file.getName(), DigestUtils.getHexDigest(metadata.checksumAlgo, file));
        }
    }

    private void backupExtras() throws BackupException {
        PseudoRules rules = new PseudoRules(packageName, userHandle);
        Path miscFile;
        try {
            miscFile = backupFile.getMiscFile(CryptoUtils.MODE_NO_ENCRYPTION);
        } catch (IOException e) {
            throw new BackupException("Couldn't get misc.am.tsv", e);
        }
        // Backup permissions
        @NonNull String[] permissions = ArrayUtils.defeatNullable(packageInfo.requestedPermissions);
        int[] permissionFlags = packageInfo.requestedPermissionsFlags;
        List<OpEntry> opEntries = new ArrayList<>();
        try {
            List<PackageOps> packageOpsList = new AppOpsService().getOpsForPackage(packageInfo.applicationInfo.uid, packageName, null);
            if (packageOpsList.size() == 1) opEntries.addAll(packageOpsList.get(0).getOps());
        } catch (Exception ignore) {
        }
        PermissionInfo info;
        int basePermissionType;
        int protectionLevels;
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
                boolean isGranted = (permissionFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                int permFlags = PermissionCompat.getPermissionFlags(info.name, packageName, userHandle);
                rules.setPermission(permissions[i], isGranted, permFlags);
            } catch (PackageManager.NameNotFoundException | RemoteException ignore) {
            }
        }
        // Backup app ops
        for (OpEntry entry : opEntries) {
            rules.setAppOp(entry.getOp(), entry.getMode());
        }
        // Backup Magisk status
        if (MagiskUtils.isHidden(packageName)) {
            rules.setMagiskHide(true);
        }
        // Backup allowed notification listeners aka BIND_NOTIFICATION_LISTENER_SERVICE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            INotificationManager notificationManager = INotificationManager.Stub.asInterface(ProxyBinder.getService(Context.NOTIFICATION_SERVICE));
            try {
                List<ComponentName> notificationComponents;
                if (LocalServer.isAMServiceAlive()) {
                    notificationComponents = notificationManager.getEnabledNotificationListeners(userHandle);
                } else notificationComponents = Collections.emptyList();
                List<String> componentsForThisPkg = new ArrayList<>();
                for (ComponentName componentName : notificationComponents) {
                    if (packageName.equals(componentName.getPackageName())) {
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
        String targetString = "user," + packageName + "," + applicationInfo.uid;
        Runner.Result result = Runner.runCommand(new String[]{"dumpsys", "deviceidle", "whitelist"});
        if (result.isSuccessful() && result.getOutput().contains(targetString)) {
            rules.setBatteryOptimization(false);
        }
        // Backup net policy
        int policies = NetworkPolicyManagerCompat.getUidPolicy(applicationInfo.uid);
        if (policies > 0) {
            // Store only if there is a policy
            rules.setNetPolicy(policies);
        }
        // Backup URI grants
        List<UriManager.UriGrant> uriGrants = new UriManager().getGrantedUris(packageName);
        if (uriGrants != null) {
            for (UriManager.UriGrant uriGrant : uriGrants) {
                if (uriGrant.targetUserId == userHandle) {
                    rules.setUriGrant(uriGrant);
                }
            }
        }
        // Backup SSAID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                String ssaid = new SsaidSettings(packageName, applicationInfo.uid).getSsaid();
                if (ssaid != null) rules.setSsaid(ssaid);
            } catch (IOException e) {
                // Ignore exception
                Log.e(TAG, e);
            }
        }
        rules.commitExternal(miscFile);
        if (!miscFile.exists()) return;
        if (!crypto.encrypt(new Path[]{miscFile})) {
            throw new BackupException("Failed to encrypt " + miscFile.getName());
        }
        try {
            // Overwrite with the new file
            miscFile = backupFile.getMiscFile(metadata.crypto);
            // Store checksum
            checksum.add(miscFile.getName(), DigestUtils.getHexDigest(metadata.checksumAlgo, miscFile));
        } catch (IOException e) {
            throw new BackupException("Couldn't get misc.am.tsv for generating checksum", e);
        }
    }

    private void backupRules() throws BackupException {
        try {
            Path rulesFile = backupFile.getRulesFile(CryptoUtils.MODE_NO_ENCRYPTION);
            try (OutputStream outputStream = rulesFile.openOutputStream();
                 ComponentsBlocker cb = ComponentsBlocker.getInstance(packageName, userHandle)) {
                ComponentUtils.storeRules(outputStream, cb.getAll(), true);
            }
            if (!rulesFile.exists()) return;
            if (!crypto.encrypt(new Path[]{rulesFile})) {
                throw new BackupException("Failed to encrypt " + rulesFile.getName());
            }
            // Overwrite with the new file
            rulesFile = backupFile.getRulesFile(metadata.crypto);
            // Store checksum
            checksum.add(rulesFile.getName(), DigestUtils.getHexDigest(metadata.checksumAlgo, rulesFile));
        } catch (IOException e) {
            throw new BackupException("Rules backup is requested but encountered an error during fetching rules.", e);
        }
    }
}
