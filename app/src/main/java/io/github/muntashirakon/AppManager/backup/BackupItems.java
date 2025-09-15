// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static io.github.muntashirakon.AppManager.backup.BackupManager.DATA_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.SOURCE_PREFIX;

import android.annotation.UserIdInt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.DummyCrypto;
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.PathReader;
import io.github.muntashirakon.io.PathWriter;
import io.github.muntashirakon.io.Paths;

public class BackupItems {
    private static final String APK_SAVING_DIRECTORY = "apks";

    private static final String ICON_FILE = "icon.png";
    private static final String RULES_TSV = "rules.am.tsv";
    private static final String MISC_TSV = "misc.am.tsv";
    private static final String CHECKSUMS_TXT = "checksums.txt";
    private static final String FREEZE = ".freeze";
    private static final String NO_MEDIA = ".nomedia";

    @NonNull
    private static Path getBaseDirectory() {
        return Prefs.Storage.getAppManagerDirectory();
    }

    @NonNull
    public static BackupItem findBackupItem(@NonNull String relativeDir) throws FileNotFoundException {
        return new BackupItem(getBaseDirectory().findFile(relativeDir));
    }

    @NonNull
    public static BackupItem findBackupItemV4(@UserIdInt int userId, @Nullable String backupName, @NonNull String packageName) throws FileNotFoundException {
        return findBackupItem(BackupUtils.getV4RelativeDir(userId, backupName, packageName));
    }

    @NonNull
    public static BackupItem findBackupItemV5(@NonNull String backupUuid) throws FileNotFoundException {
        return findBackupItem(backupUuid);
    }

    @NonNull
    public static BackupItem findOrCreateBackupItem(@UserIdInt int userId, @Nullable String backupName, @Nullable String packageName, @Nullable String backupUuid) throws IOException {
        if (MetadataManager.CURRENT_BACKUP_META_VERSION < 5 && packageName == null) {
            throw new IllegalArgumentException("packageName must be set for meta version 4 and earlier");
        }
        Path backupPath;
        BackupItem previousV4Backup = null;
        if (backupUuid != null || MetadataManager.CURRENT_BACKUP_META_VERSION >= 5) {
            //noinspection ConstantValue
            if (backupUuid == null) {
                // When switching from v4 to v5 backups
                backupUuid = UUID.randomUUID().toString();
            }
            Path baseDir = getBaseDirectory();
            boolean exists = baseDir.hasFile(backupUuid);
            backupPath = baseDir.findOrCreateDirectory(backupUuid);
            //noinspection ConstantValue
            if (!exists && packageName != null) {
                // There may be a previous v4 backup which may need to be deleted
                previousV4Backup = getPreviousV4Backup(userId, backupName, packageName);
            }
        } else {
            backupPath = getBaseDirectory()
                    .findOrCreateDirectory(packageName)
                    .findOrCreateDirectory(BackupUtils.getV4BackupName(userId, backupName));
        }
        BackupItem backupItem = new BackupItem(backupPath, true);
        backupItem.setBackupName(BackupUtils.getCompatBackupName(backupName));
        backupItem.setPreviousV4Backup(previousV4Backup);
        return backupItem;
    }

    @NonNull
    public static BackupItem createBackupItemGracefully(@UserIdInt int userId, @Nullable String backupName, @Nullable String packageName) throws IOException {
        if (MetadataManager.CURRENT_BACKUP_META_VERSION < 5 && packageName == null) {
            throw new IllegalArgumentException("packageName must be set for meta version 4 and earlier");
        }
        Path backupPath;
        if (MetadataManager.CURRENT_BACKUP_META_VERSION >= 5) {
            String backupUuid = UUID.randomUUID().toString();
            backupPath = getBaseDirectory().findOrCreateDirectory(backupUuid);
        } else {
            Path baseDir = getBaseDirectory().findOrCreateDirectory(packageName);
            String backupItemName = BackupUtils.getV4BackupName(userId, backupName);
            String newBackupName = backupItemName;
            int i = 0;
            while (baseDir.hasFile(newBackupName)) {
                newBackupName = backupItemName + "_" + (++i);
            }
            backupPath = baseDir.createNewDirectory(newBackupName);
        }
        BackupItem backupItem = new BackupItem(backupPath, true);
        backupItem.setBackupName(BackupUtils.getCompatBackupName(backupName));
        return backupItem;
    }

    @Nullable
    private static BackupItem getPreviousV4Backup(@UserIdInt int userId, @Nullable String backupName, @NonNull String packageName) {
        Path baseDir = getBaseDirectory();
        // Format: {packagename}/{userid}[_{backup_name}]
        Path pkgDir = baseDir.findFileOrNull(packageName);
        if (pkgDir == null) {
            return null;
        }
        Path backupPath = pkgDir.findFileOrNull(BackupUtils.getV4BackupName(userId, backupName));
        return backupPath != null ? new BackupItem(backupPath) : null;
    }

    @NonNull
    public static List<BackupItem> findAllBackupItems() {
        Path baseDirectory = getBaseDirectory();
        Path[] paths = baseDirectory.listFiles(Path::isDirectory);
        List<BackupItem> backupItems = new ArrayList<>(paths.length);
        for (Path path : paths) {
            if (BackupUtils.isUuid(path.getName())) {
                // UUID-based backups only store one backup per folder
                backupItems.add(new BackupItem(path));
                continue;
            }
            if (SaveLogHelper.SAVED_LOGS_DIR.equals(path.getName())) {
                continue;
            }
            if (APK_SAVING_DIRECTORY.equals(path.getName())) {
                continue;
            }
            if (".tmp".equals(path.getName())) {
                continue;
            }
            // Other backups can store multiple backups per folder
            backupItems.addAll(Arrays.stream(path.listFiles(Path::isDirectory))
                    .map(BackupItem::new)
                    .collect(Collectors.toList()));
        }
        // We don't need to check further at this stage.
        // It's the caller's job to check the contents if needed.
        return backupItems;
    }

    @NonNull
    private static synchronized Path getTemporaryUnencryptedPath(@NonNull String backupName) throws IOException {
        Path tmpDir = Prefs.Storage.getTempPath();
        String newFilename = backupName;
        int i = 0;
        while (tmpDir.hasFile(newFilename)) {
            newFilename = backupName + "_" + (++i);
        }
        return tmpDir.findOrCreateDirectory(newFilename);
    }

    @NonNull
    private static synchronized Path getTemporaryBackupPath(@NonNull Path originalBackupPath) throws IOException {
        Path tmpDir = originalBackupPath.requireParent();
        String tmpFilename = "." + originalBackupPath.getName();
        String newFilename = tmpFilename;
        int i = 0;
        while (tmpDir.hasFile(newFilename)) {
            newFilename = tmpFilename + "_" + (++i);
        }
        return tmpDir.findOrCreateDirectory(newFilename);
    }

    @NonNull
    public static Path getApkBackupDirectory() throws IOException {
        return getBaseDirectory().findOrCreateDirectory(APK_SAVING_DIRECTORY);
    }

    public static void createNoMediaIfNotExists() throws IOException {
        Path backupDirectory = getBaseDirectory();
        if (!backupDirectory.hasFile(NO_MEDIA)) {
            backupDirectory.createNewFile(NO_MEDIA, null);
        }
    }

    public static class BackupItem {
        public static final String TAG = BackupItem.class.getSimpleName();

        @NonNull
        private final Path mBackupPath;
        @NonNull
        private final Path mTempBackupPath;
        private final Object mCryptoGuard = new Object();
        @Nullable
        private Crypto mCrypto;
        @CryptoUtils.Mode
        private String mCryptoMode = CryptoUtils.MODE_NO_ENCRYPTION;
        @Nullable
        private String mBackupName;
        private boolean mBackupNameSet = false;
        private boolean mBackupMode;
        private boolean mBackupSuccess = false;
        private final List<Path> mTemporaryFiles = new ArrayList<>();
        private Path mTempUnencyptedPath;
        @Nullable
        private BackupItem mPreviousV4Backup;

        private BackupItem(@NonNull Path backupPath, boolean backupMode) throws IOException {
            mBackupPath = backupPath;
            mBackupMode = backupMode;
            if (mBackupMode) {
                mBackupPath.mkdirs();  // Create backup path if not exists
                mTempBackupPath = getTemporaryBackupPath(mBackupPath);
            } else mTempBackupPath = mBackupPath;
        }

        // Read-only instance: the point is not to throw IOException
        private BackupItem(@NonNull Path backupPath) {
            mBackupPath = backupPath;
            mBackupMode = false;
            mTempBackupPath = mBackupPath;
        }

        public void setCrypto(@Nullable Crypto crypto) {
            if (crypto == null || crypto instanceof DummyCrypto) {
                mCrypto = null;
                mCryptoMode = CryptoUtils.MODE_NO_ENCRYPTION;
            } else {
                mCrypto = crypto;
                mCryptoMode = crypto.getModeName();
            }
        }

        public void setBackupName(@Nullable String backupName) {
            mBackupName = backupName;
            mBackupNameSet = true;
        }

        @Nullable
        public String getBackupName() {
            if (mBackupNameSet) {
                return mBackupName;
            }
            if (mBackupMode) {
                throw new IllegalStateException("mBackupName must be set in backup mode.");
            }
            if (isV5AndUp()) {
                throw new IllegalStateException("getBackupName() is unavailable in backup v5 and up unless set manually.");
            }
            // For v4 or earlier backups, fallback to filename
            return BackupUtils.getRealBackupName(4, mBackupPath.getName());
        }

        public void setPreviousV4Backup(@Nullable BackupItem previousV4Backup) {
            mPreviousV4Backup = previousV4Backup;
        }

        public String getRelativeDir() {
            if (isV5AndUp()) {
                // {AppManagerDir}/{UUID}/
                return mBackupPath.getName();
            } else {
                // {AppManagerDir}/{packagename}/{userid}[_{backup_name}]
                String userIdBackupName = mBackupPath.getName();
                String packageName = mBackupPath.requireParent().getName();
                return BackupUtils.getV4RelativeDir(userIdBackupName, packageName);
            }
        }

        public boolean isBackupMode() {
            return mBackupMode;
        }

        @NonNull
        public Path getBackupPath() {
            return mBackupMode ? mTempBackupPath : mBackupPath;
        }

        public Path getUnencryptedBackupPath() {
            if (mCrypto == null) {
                // Use real path for unencrypted backups
                return getBackupPath();
            } else {
                if (mTempUnencyptedPath == null) {
                    // We can only do this once for each BackupItem
                    try {
                        mTempUnencyptedPath = getTemporaryUnencryptedPath(getBackupPath().getName());
                    } catch (IOException e) {
                        Log.w(TAG, "Could not create temporary unencrypted path, falling back to default path", e);
                        mTempUnencyptedPath = getBackupPath();
                    }
                }
                return mTempUnencyptedPath;
            }
        }

        @NonNull
        public Path[] encrypt(@NonNull Path[] files) throws IOException {
            // Encrypt the files and delete the originals
            synchronized (mCryptoGuard) {
                if (mCrypto == null) {
                    // No encryption enabled
                    return files;
                }
                List<Path> newFileList = new ArrayList<>();
                // Get desired extension
                String ext = CryptoUtils.getExtension(mCryptoMode);
                // Create necessary files (1-1 correspondence)
                for (Path inputFile : files) {
                    Path parent = getBackupPath();
                    String outputFilename = inputFile.getName() + ext;
                    Path outputPath = parent.createNewFile(outputFilename, null);
                    newFileList.add(outputPath);
                    Log.i(TAG, "Input: %s\nOutput: %s", inputFile, outputPath);
                }
                Path[] newFiles = newFileList.toArray(new Path[0]);
                // Perform actual encryption
                mCrypto.encrypt(files, newFiles);
                // Delete unencrypted files
                for (Path inputFile : files) {
                    if (!inputFile.delete()) {
                        throw new IOException("Couldn't delete old file " + inputFile);
                    }
                }
                return newFiles;
            }
        }

        @NonNull
        public Path[] decrypt(@NonNull Path[] files) throws IOException {
            // Decrypt the files but do NOT delete the originals
            synchronized (mCryptoGuard) {
                if (mCrypto == null) {
                    // No encryption enabled
                    return files;
                }
                List<Path> newFileList = new ArrayList<>();
                // Get desired extension
                String ext = CryptoUtils.getExtension(mCryptoMode);
                // Create necessary files (1-1 correspondence)
                for (Path inputFile : files) {
                    Path parent = getUnencryptedBackupPath();
                    String filename = inputFile.getName();
                    String outputFilename = filename.substring(0, filename.lastIndexOf(ext));
                    Path outputPath = parent.createNewFile(outputFilename, null);
                    newFileList.add(outputPath);
                    Log.i(TAG, "Input: %s\nOutput: %s", inputFile, outputPath);
                }
                Path[] newFiles = newFileList.toArray(new Path[0]);
                // Perform actual decryption
                mCrypto.decrypt(files, newFiles);
                mTemporaryFiles.addAll(newFileList);
                return newFiles;
            }
        }

        @NonNull
        public Path getIconFile() throws IOException {
            // Icon is never encrypted
            if (mBackupMode) {
                return getBackupPath().findOrCreateFile(ICON_FILE, null);
            } else return getBackupPath().findFile(ICON_FILE);
        }

        public boolean isV5AndUp() {
            return getBackupPath().hasFile(MetadataManager.INFO_V5_FILE);
        }

        public Path getInfoFile() throws IOException {
            // info_v5.am.json is never encrypted
            if (mBackupMode) {
                return getBackupPath().findOrCreateFile(MetadataManager.INFO_V5_FILE, null);
            } else return getBackupPath().findFile(MetadataManager.INFO_V5_FILE);
        }

        public Path getMetadataV5File() throws IOException {
            if (mBackupMode) {
                // Needs to be encrypted in backup mode
                return getBackupPath().findOrCreateFile(MetadataManager.META_V5_FILE, null);
            } else {
                // Needs to be decrypted in restore mode
                Path file = getBackupPath().findFile(MetadataManager.META_V5_FILE + CryptoUtils.getExtension(mCryptoMode));
                return decrypt(new Path[]{file})[0];
            }
        }

        @NonNull
        public Path getMetadataV2File() throws IOException {
            // meta_v2.am.json is never encrypted
            if (mBackupMode) {
                return getBackupPath().findOrCreateFile(MetadataManager.META_V2_FILE, null);
            } else return getBackupPath().findFile(MetadataManager.META_V2_FILE);
        }

        public BackupMetadataV5.Info getInfo() throws IOException {
            return MetadataManager.readInfo(this);
        }

        public BackupMetadataV5 getMetadata() throws IOException {
            return MetadataManager.readMetadataV5(this);
        }

        public BackupMetadataV5 getMetadata(BackupMetadataV5.Info backupInfo) throws IOException {
            return MetadataManager.readMetadataV5(this, backupInfo);
        }

        @NonNull
        private Path getChecksumFile() throws IOException {
            if (mBackupMode) {
                // Needs to be encrypted in backup mode
                return getUnencryptedBackupPath().findOrCreateFile(CHECKSUMS_TXT, null);
            } else {
                // Needs to be decrypted in restore mode
                Path file = getBackupPath().findFile(CHECKSUMS_TXT + CryptoUtils.getExtension(mCryptoMode));
                return decrypt(new Path[]{file})[0];
            }
        }

        @NonNull
        public Checksum getChecksum() throws IOException {
            return new Checksum(getChecksumFile(), mBackupMode ? "w" : "r");
        }

        @NonNull
        public Path getMiscFile() throws IOException {
            if (mBackupMode) {
                // Needs to be encrypted in backup mode
                return getUnencryptedBackupPath().findOrCreateFile(MISC_TSV, null);
            } else {
                // Needs to be decrypted in restore mode
                return getBackupPath().findFile(MISC_TSV + CryptoUtils.getExtension(mCryptoMode));
            }
        }

        @NonNull
        public Path getRulesFile() throws IOException {
            if (mBackupMode) {
                // Needs to be encrypted in backup mode
                return getUnencryptedBackupPath().findOrCreateFile(RULES_TSV, null);
            } else {
                // Needs to be decrypted in restore mode
                return getBackupPath().findFile(RULES_TSV + CryptoUtils.getExtension(mCryptoMode));
            }
        }

        @NonNull
        public Path[] getSourceFiles() {
            String ext = CryptoUtils.getExtension(mCryptoMode);
            Path[] paths = getBackupPath().listFiles((dir, name) -> name.startsWith(SOURCE_PREFIX) && name.endsWith(ext));
            return Paths.getSortedPaths(paths);
        }

        @NonNull
        public Path[] getDataFiles(int index) {
            String ext = CryptoUtils.getExtension(mCryptoMode);
            final String dataPrefix = DATA_PREFIX + index;
            Path[] paths = getBackupPath().listFiles((dir, name) -> name.startsWith(dataPrefix) && name.endsWith(ext));
            return Paths.getSortedPaths(paths);
        }

        @NonNull
        public Path[] getKeyStoreFiles() {
            String ext = CryptoUtils.getExtension(mCryptoMode);
            Path[] paths = getBackupPath().listFiles((dir, name) -> name.startsWith(KEYSTORE_PREFIX) && name.endsWith(ext));
            return Paths.getSortedPaths(paths);
        }

        public void freeze() throws IOException {
            getBackupPath().createNewFile(FREEZE, null);
        }

        public void unfreeze() throws FileNotFoundException {
            getFreezeFile().delete();
        }

        public boolean isFrozen() {
            try {
                return getFreezeFile().exists();
            } catch (IOException e) {
                return false;
            }
        }

        public void commit() throws IOException {
            if (mBackupMode) {
                if (mBackupSuccess) {
                    // Backup already done
                    return;
                }
                if (!delete()) {
                    throw new IOException("Could not delete " + mBackupPath);
                }
                if (!mTempBackupPath.moveTo(mBackupPath)) {
                    throw new IOException("Could not move " + mTempBackupPath + " to " + mBackupPath);
                }
                if (mPreviousV4Backup != null) {
                    if (!mPreviousV4Backup.delete()) {
                        throw new IOException("Could not delete " + mPreviousV4Backup.mBackupPath);
                    }
                }
                mBackupSuccess = true;
                // Set backup mode to false to make it read-only
                mBackupMode = false;
            }
        }

        public void cleanup() {
            if (mBackupMode) {
                if (!mBackupSuccess) {
                    // Backup wasn't successful, delete the directory
                    mTempBackupPath.delete();
                }
            }
            for (Path file : mTemporaryFiles) {
                Log.d(TAG, "Deleting %s", file);
                file.delete();
            }
            if (mTempUnencyptedPath != null) {
                mTempUnencyptedPath.delete();
            }
            if (mCrypto != null) {
                mCrypto.close();
            }
        }

        public boolean exists() {
            return mBackupPath.exists();
        }

        public boolean delete() {
            if (mBackupPath.exists()) {
                return mBackupPath.delete();
            }
            return true;  // The backup path doesn't exist anyway
        }

        @NonNull
        private Path getFreezeFile() throws FileNotFoundException {
            return getBackupPath().findFile(FREEZE);
        }
    }

    @NonNull
    private final String mPackageName;
    private final int mUserId;
    @NonNull
    private final String[] mBackupNames;

    /**
     * Create and handle {@link BackupItem}.
     *
     * @param packageName Name of the package whose backups has to be managed
     * @param userId      To whom the package belong
     * @param backupNames Name of the backups. If {@code null}, user handle will be used. If not
     *                    null, the backup names will have the format {@code userHandle_backupName}.
     */
    public BackupItems(@NonNull String packageName, int userId, @Nullable String[] backupNames) throws IOException {
        mPackageName = packageName;
        mUserId = userId;
        if (backupNames == null) {
            mBackupNames = new String[]{null};
        } else {
            mBackupNames = backupNames;
        }
    }

    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    public BackupItem[] getOrCreateItems() throws IOException {
        BackupItem[] backupItems = new BackupItem[mBackupNames.length];
        for (int i = 0; i < mBackupNames.length; ++i) {
            backupItems[i] = findOrCreateBackupItem(mUserId, mBackupNames[i], mPackageName, null);
        }
        return backupItems;
    }

    public BackupItem[] createItemsGracefully() throws IOException {
        BackupItem[] backupItems = new BackupItem[mBackupNames.length];
        for (int i = 0; i < mBackupNames.length; ++i) {
            backupItems[i] = createBackupItemGracefully(mUserId, mBackupNames[i], mPackageName);
        }
        return backupItems;
    }

    public static class Checksum implements Closeable {
        private PrintWriter mWriter;
        private final HashMap<String, String> mChecksums = new HashMap<>();
        private final String mMode;
        private final Path mFile;

        @NonNull
        public static String[] getCertChecksums(@NonNull Checksum checksum) {
            List<String> certChecksums = new ArrayList<>();
            synchronized (checksum.mChecksums) {
                for (String name : checksum.mChecksums.keySet()) {
                    if (name.startsWith(BackupManager.CERT_PREFIX)) {
                        certChecksums.add(checksum.mChecksums.get(name));
                    }
                }
            }
            return certChecksums.toArray(new String[0]);
        }

        Checksum(@NonNull Path checksumFile, String mode) throws IOException {
            mFile = checksumFile;
            mMode = mode;
            if ("w".equals(mode)) {
                mWriter = new PrintWriter(new BufferedWriter(new PathWriter(checksumFile)));
            } else if ("r".equals(mode)) {
                synchronized (mChecksums) {
                    BufferedReader reader = new BufferedReader(new PathReader(checksumFile));
                    // Get checksums
                    String line;
                    String[] lineSplits;
                    while ((line = reader.readLine()) != null) {
                        lineSplits = line.split("\t", 2);
                        if (lineSplits.length != 2) {
                            throw new RuntimeException("Illegal lines found in the checksum file.");
                        }
                        mChecksums.put(lineSplits[1], lineSplits[0]);
                    }
                    reader.close();
                }
            } else throw new IOException("Unknown mode: " + mode);
        }

        public Path getFile() {
            return mFile;
        }

        public void add(@NonNull String fileName, @NonNull String checksum) {
            synchronized (mChecksums) {
                if (!"w".equals(mMode)) {
                    throw new IllegalStateException("add is inaccessible in mode " + mMode);
                }
                mWriter.println(String.format("%s\t%s", checksum, fileName));
                mChecksums.put(fileName, checksum);
                mWriter.flush();
            }
        }

        @Nullable
        String get(String fileName) {
            synchronized (mChecksums) {
                return mChecksums.get(fileName);
            }
        }

        @Override
        public void close() {
            synchronized (mChecksums) {
                if (mWriter != null) {
                    mWriter.close();
                    mWriter = null;
                }
            }
        }
    }
}
