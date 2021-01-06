package io.github.muntashirakon.AppManager.backup;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.graphics.Bitmap;

import com.android.internal.util.ArrayUtils;

import org.json.JSONException;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.pm.PermissionInfoCompat;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.appops.OpEntry;
import io.github.muntashirakon.AppManager.appops.PackageOps;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

import static io.github.muntashirakon.AppManager.backup.BackupManager.CACHE_DIRS;
import static io.github.muntashirakon.AppManager.backup.BackupManager.CERT_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.DATA_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.ICON_FILE;
import static io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PLACEHOLDER;
import static io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.MASTER_KEY;
import static io.github.muntashirakon.AppManager.backup.BackupManager.SOURCE_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.getExt;

class BackupOp implements Closeable {
    static final String TAG = "BackupOp";

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
    private final PrivilegedFile tmpBackupPath;
    private final int userHandle;
    @NonNull
    private final Crypto crypto;
    @NonNull
    private final BackupFiles.Checksum checksum;
    // TODO(26/12/20): Get IPackageManager instead of PackageManager.
    @NonNull
    private final PackageManager pm = AppManager.getContext().getPackageManager();

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
            // Backup icon
            backupIcon();
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

    private void backupIcon() {
        final File iconFile = new File(tmpBackupPath, ICON_FILE);
        try (OutputStream outputStream = new FileOutputStream(iconFile)) {
            Bitmap bitmap = IOUtils.getBitmapFromDrawable(applicationInfo.loadIcon(pm));
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
        } catch (IOException e) {
            Log.w(TAG, "Could not back up icon.");
        }
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
        List<OpEntry> opEntries = new ArrayList<>();
        try {
            List<PackageOps> packageOpsList = new AppOpsService().getOpsForPackage(packageInfo.applicationInfo.uid, packageName, null);
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
