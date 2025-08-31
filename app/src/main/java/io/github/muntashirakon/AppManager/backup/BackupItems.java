// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.DummyCrypto;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.PathReader;
import io.github.muntashirakon.io.PathWriter;

public class BackupItems {
    static final String APK_SAVING_DIRECTORY = "apks";
    @Deprecated // No longer used
    static final String TEMPORARY_DIRECTORY = ".tmp";

    private static final String ICON_FILE = "icon.png";
    private static final String RULES_TSV = "rules.am.tsv";
    private static final String MISC_TSV = "misc.am.tsv";
    private static final String CHECKSUMS_TXT = "checksums.txt";
    private static final String FREEZE = ".freeze";
    private static final String NO_MEDIA = ".nomedia";

    @NonNull
    public static Path getBaseDirectory() {
        return Prefs.Storage.getAppManagerDirectory();
    }

    @NonNull
    public static Path findBackupDirectory(@NonNull String backupName, @Nullable String packageName, @Nullable String backupUuid) throws FileNotFoundException {
        if (packageName == null && backupUuid == null) {
            throw new IllegalArgumentException("Neither packageName nor backupUuid is set");
        }
        if (backupUuid != null) {
            return getBaseDirectory().findFile(backupUuid);
        } else {
            return getBaseDirectory().findFile(packageName).findFile(backupName);
        }
    }

    @Deprecated
    @NonNull
    public static Path getPackagePath(@NonNull String packageName, boolean create) throws IOException {
        if (create) {
            return getBaseDirectory().findOrCreateDirectory(packageName);
        } else return getBaseDirectory().findFile(packageName);
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
        public final String backupName;
        @NonNull
        private final Path mBackupPath;
        @NonNull
        private final Path mTempBackupPath;
        private final boolean mBackupMode;
        private final Object mCryptoGuard = new Object();
        @Nullable
        private Crypto mCrypto;
        @CryptoUtils.Mode
        private String mCryptoMode = CryptoUtils.MODE_NO_ENCRYPTION;
        private boolean mBackupSuccess = false;
        private List<Path> mTemporaryFiles = new ArrayList<>();

        public BackupItem(@NonNull Path backupPath, boolean backupMode) throws IOException {
            // For now, backup name is the same as the first path segment
            backupName = backupPath.getName();
            mBackupPath = backupPath;
            mBackupMode = backupMode;
            if (mBackupMode) {
                mBackupPath.mkdirs();  // Create backup path if not exists
                mTempBackupPath = getTemporaryBackupPath(mBackupPath);
            } else mTempBackupPath = mBackupPath;
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

        @NonNull
        public Path getBackupPath() {
            return mBackupMode ? mTempBackupPath : mBackupPath;
        }

        public Path getUnencryptedBackupPath() {
            if (mCrypto == null) {
                // Use real path for unencrypted backups
                return getBackupPath();
            } else {
                // TODO: 8/30/25 Always use temporary path for encrypted backups
                return getBackupPath();
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

        @NonNull
        public Path getMetadataFile() throws IOException {
            // meta is never encrypted
            if (mBackupMode) {
                return getBackupPath().findOrCreateFile(MetadataManager.META_FILE, null);
            } else return getBackupPath().findFile(MetadataManager.META_FILE);
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
        public Path getRulesFile(@CryptoUtils.Mode String mode) throws IOException {
            if (mBackupMode) {
                // Needs to be encrypted in backup mode
                return getUnencryptedBackupPath().findOrCreateFile(RULES_TSV, null);
            } else {
                // Needs to be decrypted in restore mode
                return getBackupPath().findFile(RULES_TSV + CryptoUtils.getExtension(mCryptoMode));
            }
        }

        public void freeze() throws IOException {
            getBackupPath().createNewFile(FREEZE, null);
        }

        public void unfreeze() throws FileNotFoundException {
            getFreezeFile().delete();
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
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
                mBackupSuccess = true;
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
            if (mCrypto != null) {
                mCrypto.close();
            }
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
    @NonNull
    private final Path mPackagePath;

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
            mBackupNames = new String[]{String.valueOf(userId)};
        } else {
            // Add user handle before the backup name
            mBackupNames = new String[backupNames.length];
            for (int i = 0; i < backupNames.length; ++i) {
                mBackupNames[i] = userId + "_" + backupNames[i].trim();
            }
        }
        mPackagePath = getPackagePath(packageName, true);
    }

    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    public BackupItem[] getBackupPaths(boolean backupMode) throws IOException {
        BackupItem[] backupFiles = new BackupItem[mBackupNames.length];
        for (int i = 0; i < mBackupNames.length; ++i) {
            backupFiles[i] = new BackupItem(
                    backupMode ?
                            mPackagePath.findOrCreateDirectory(mBackupNames[i]) :
                            mPackagePath.findFile(mBackupNames[i]),
                    backupMode);
        }
        return backupFiles;
    }

    BackupItem[] getFreshBackupPaths() throws IOException {
        BackupItem[] backupFiles = new BackupItem[mBackupNames.length];
        for (int i = 0; i < mBackupNames.length; ++i) {
            backupFiles[i] = new BackupItem(getFreshBackupPath(mBackupNames[i]), true);
        }
        return backupFiles;
    }

    private Path getFreshBackupPath(String backupName) throws IOException {
        String newBackupName = backupName;
        int i = 0;
        while (mPackagePath.hasFile(newBackupName)) {
            newBackupName = backupName + "_" + (++i);
        }
        return mPackagePath.findOrCreateDirectory(newBackupName);
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
                if (!"w".equals(mMode)) throw new IllegalStateException("add is inaccessible in mode " + mMode);
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
