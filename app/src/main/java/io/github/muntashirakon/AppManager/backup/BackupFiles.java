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

import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.PathReader;
import io.github.muntashirakon.io.PathWriter;

public class BackupFiles {
    static final String APK_SAVING_DIRECTORY = "apks";
    static final String TEMPORARY_DIRECTORY = ".tmp";

    static final String RULES_TSV = "rules.am.tsv";
    static final String MISC_TSV = "misc.am.tsv";
    static final String CHECKSUMS_TXT = "checksums.txt";
    static final String FREEZE = ".freeze";
    static final String NO_MEDIA = ".nomedia";

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

    public static class BackupFile {
        @NonNull
        private final Path mBackupPath;
        @NonNull
        private final Path mTempBackupPath;
        private final boolean mIsTemporary;

        public BackupFile(@NonNull Path backupPath, boolean hasTemporary) throws IOException {
            mBackupPath = backupPath;
            mIsTemporary = hasTemporary;
            if (mIsTemporary) {
                mBackupPath.mkdirs();  // Create backup path if not exists
                mTempBackupPath = getTemporaryBackupPath(mBackupPath);
            } else mTempBackupPath = mBackupPath;
        }

        @NonNull
        public Path getBackupPath() {
            return mIsTemporary ? mTempBackupPath : mBackupPath;
        }

        @NonNull
        public Path getMetadataFile() throws IOException {
            if (mIsTemporary) {
                return getBackupPath().findOrCreateFile(MetadataManager.META_FILE, null);
            } else return getBackupPath().findFile(MetadataManager.META_FILE);
        }

        @NonNull
        public Path getChecksumFile(@CryptoUtils.Mode String mode) throws IOException {
            if (mIsTemporary) {
                return getBackupPath().findOrCreateFile(CHECKSUMS_TXT + CryptoUtils.getExtension(mode), null);
            } else return getBackupPath().findFile(CHECKSUMS_TXT + CryptoUtils.getExtension(mode));
        }

        @NonNull
        public Checksum getChecksum(@CryptoUtils.Mode String mode) throws IOException {
            return new Checksum(getChecksumFile(mode), mIsTemporary ? "w" : "r");
        }

        @NonNull
        public Path getMiscFile(@CryptoUtils.Mode String mode) throws IOException {
            if (mIsTemporary) {
                return getBackupPath().findOrCreateFile(MISC_TSV + CryptoUtils.getExtension(mode), null);
            } else return getBackupPath().findFile(MISC_TSV + CryptoUtils.getExtension(mode));
        }

        @NonNull
        public Path getRulesFile(@CryptoUtils.Mode String mode) throws IOException {
            if (mIsTemporary) {
                return getBackupPath().findOrCreateFile(RULES_TSV + CryptoUtils.getExtension(mode), null);
            } else return getBackupPath().findFile(RULES_TSV + CryptoUtils.getExtension(mode));
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
            if (mIsTemporary) {
                if (!delete()) {
                    throw new IOException("Could not delete " + mBackupPath);
                }
                if (!mTempBackupPath.moveTo(mBackupPath)) {
                    throw new IOException("Could not move " + mTempBackupPath + " to " + mBackupPath);
                }
            }
        }

        public void cleanup() {
            if (mIsTemporary) {
                mTempBackupPath.delete();
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
     * Create and handle {@link BackupFile}.
     *
     * @param packageName Name of the package whose backups has to be managed
     * @param userId      To whom the package belong
     * @param backupNames Name of the backups. If {@code null}, user handle will be used. If not
     *                    null, the backup names will have the format {@code userHandle_backupName}.
     */
    public BackupFiles(@NonNull String packageName, int userId, @Nullable String[] backupNames) throws IOException {
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

    public BackupFile[] getBackupPaths(boolean hasTemporary) throws IOException {
        BackupFile[] backupFiles = new BackupFile[mBackupNames.length];
        for (int i = 0; i < mBackupNames.length; ++i) {
            backupFiles[i] = new BackupFile(
                    hasTemporary ?
                            mPackagePath.findOrCreateDirectory(mBackupNames[i]) :
                            mPackagePath.findFile(mBackupNames[i]),
                    hasTemporary);
        }
        return backupFiles;
    }

    BackupFile[] getFreshBackupPaths() throws IOException {
        BackupFile[] backupFiles = new BackupFile[mBackupNames.length];
        for (int i = 0; i < mBackupNames.length; ++i) {
            backupFiles[i] = new BackupFile(getFreshBackupPath(mBackupNames[i]), true);
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

        public Checksum(@NonNull Path checksumFile, String mode) throws IOException {
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
                if (mWriter != null) mWriter.close();
            }
        }
    }
}
