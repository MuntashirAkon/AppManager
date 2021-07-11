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

import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.ProxyFileReader;
import io.github.muntashirakon.io.ProxyFileWriter;

public class BackupFiles {
    static final String APK_SAVING_DIRECTORY = "apks";
    static final String TEMPORARY_DIRECTORY = ".tmp";

    static final String RULES_TSV = "rules.am.tsv";
    static final String MISC_TSV = "misc.am.tsv";
    static final String CHECKSUMS_TXT = "checksums.txt";
    static final String FREEZE = ".freeze";
    static final String NO_MEDIA = ".nomedia";

    @NonNull
    public static Path getBackupDirectory() throws FileNotFoundException {
        return AppPref.getAppManagerDirectory();
    }

    @NonNull
    public static Path getTemporaryDirectory() throws IOException {
        return getBackupDirectory().findOrCreateDirectory(TEMPORARY_DIRECTORY);
    }

    @NonNull
    public static Path getPackagePath(@NonNull String packageName, boolean create) throws IOException {
        if (create) {
            return getBackupDirectory().findOrCreateDirectory(packageName);
        } else return getBackupDirectory().findFile(packageName);
    }

    @NonNull
    private static synchronized Path getTemporaryBackupPath() throws IOException {
        // FIXME: 9/7/21 Temporary backup path should be in the package path in order to make it easy to rename it in SAF
        Path tmpDir = getTemporaryDirectory();
        String tmpFilename = "backup_" + System.currentTimeMillis();
        String newFilename = tmpFilename;
        int i = 0;
        while (tmpDir.hasFile(newFilename)) {
            newFilename = tmpFilename + "_" + (++i);
        }
        return tmpDir.findOrCreateDirectory(newFilename);
    }

    @NonNull
    public static Path getApkBackupDirectory() throws IOException {
        return getBackupDirectory().findOrCreateDirectory(APK_SAVING_DIRECTORY);
    }

    public static void createNoMediaIfNotExists() throws IOException {
        Path backupDirectory = getBackupDirectory();
        if (backupDirectory.hasFile(NO_MEDIA)) {
            backupDirectory.createNewFile(NO_MEDIA, null);
        }
    }

    public static class BackupFile {
        @NonNull
        private final Path backupPath;
        @NonNull
        private final Path tmpBackupPath;
        private final boolean isTemporary;

        public BackupFile(@NonNull Path backupPath, boolean hasTemporary) throws IOException {
            this.backupPath = backupPath;
            this.isTemporary = hasTemporary;
            if (hasTemporary) {
                backupPath.mkdirs();  // Create backup path if not exists
                tmpBackupPath = getTemporaryBackupPath();
            } else tmpBackupPath = this.backupPath;
        }

        @NonNull
        public Path getBackupPath() {
            return isTemporary ? tmpBackupPath : backupPath;
        }

        @NonNull
        public Path getMetadataFile() throws IOException {
            if (isTemporary) {
                return getBackupPath().findOrCreateFile(MetadataManager.META_FILE, null);
            } else return getBackupPath().findFile(MetadataManager.META_FILE);
        }

        @NonNull
        public Path getChecksumFile(@CryptoUtils.Mode String mode) throws IOException {
            if (isTemporary) {
                return getBackupPath().findOrCreateFile(CHECKSUMS_TXT + CryptoUtils.getExtension(mode), null);
            } else return getBackupPath().findFile(CHECKSUMS_TXT + CryptoUtils.getExtension(mode));
        }

        @NonNull
        public Path getMiscFile(@CryptoUtils.Mode String mode) throws IOException {
            if (isTemporary) {
                return getBackupPath().findOrCreateFile(MISC_TSV + CryptoUtils.getExtension(mode), null);
            } else return getBackupPath().findFile(MISC_TSV + CryptoUtils.getExtension(mode));
        }

        @NonNull
        public Path getRulesFile(@CryptoUtils.Mode String mode) throws IOException {
            if (isTemporary) {
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

        public boolean commit() {
            if (isTemporary) {
                return delete() && tmpBackupPath.moveTo(backupPath);
            }
            return true;
        }

        public void cleanup() {
            if (isTemporary) {
                tmpBackupPath.delete();
            }
        }

        public boolean delete() {
            if (backupPath.exists()) {
                return backupPath.delete();
            }
            return true;  // The backup path doesn't exist anyway
        }

        @NonNull
        private Path getFreezeFile() throws FileNotFoundException {
            return getBackupPath().findFile(FREEZE);
        }
    }

    @NonNull
    private final String packageName;
    private final int userHandle;
    @NonNull
    private final String[] backupNames;
    @NonNull
    private final Path packagePath;

    /**
     * Create and handle {@link BackupFile}.
     *
     * @param packageName Name of the package whose backups has to be managed
     * @param userHandle  The user handle (aka user ID) to whom the package belong to
     * @param backupNames Name of the backups. If {@code null}, user handle will be used. If not
     *                    null, the backup names will have the format {@code userHandle_backupName}.
     */
    public BackupFiles(@NonNull String packageName, int userHandle, @Nullable String[] backupNames) throws IOException {
        this.packageName = packageName;
        this.userHandle = userHandle;
        if (backupNames == null) {
            this.backupNames = new String[]{String.valueOf(userHandle)};
        } else {
            // Add user handle before the backup name
            this.backupNames = new String[backupNames.length];
            for (int i = 0; i < backupNames.length; ++i) {
                this.backupNames[i] = userHandle + "_" + backupNames[i].trim();
            }
        }
        packagePath = getPackagePath(packageName, true);
    }

    public BackupFile[] getBackupPaths(boolean hasTemporary) throws IOException {
        BackupFile[] backupFiles = new BackupFile[backupNames.length];
        for (int i = 0; i < backupNames.length; ++i) {
            backupFiles[i] = new BackupFile(
                    hasTemporary ?
                            packagePath.findOrCreateDirectory(backupNames[i]) :
                            packagePath.findFile(backupNames[i]),
                    hasTemporary);
        }
        return backupFiles;
    }

    BackupFile[] getFreshBackupPaths() throws IOException {
        BackupFile[] backupFiles = new BackupFile[backupNames.length];
        for (int i = 0; i < backupNames.length; ++i) {
            backupFiles[i] = new BackupFile(getFreshBackupPath(backupNames[i]), true);
        }
        return backupFiles;
    }

    private Path getFreshBackupPath(String backupName) throws IOException {
        String newBackupName = backupName;
        int i = 0;
        while (packagePath.hasFile(newBackupName)) {
            newBackupName = backupName + "_" + (++i);
        }
        return packagePath.findOrCreateDirectory(newBackupName);
    }

    public static class Checksum implements Closeable {
        private PrintWriter writer;
        private final HashMap<String, String> checksums = new HashMap<>();
        private final String mode;

        @NonNull
        public static String[] getCertChecksums(@NonNull Checksum checksum) {
            List<String> certChecksums = new ArrayList<>();
            synchronized (checksum.checksums) {
                for (String name : checksum.checksums.keySet()) {
                    if (name.startsWith(BackupManager.CERT_PREFIX)) {
                        certChecksums.add(checksum.checksums.get(name));
                    }
                }
            }
            return certChecksums.toArray(new String[0]);
        }

        public Checksum(@NonNull Path checksumFile, String mode) throws IOException {
            this.mode = mode;
            if ("w".equals(mode)) {
                writer = new PrintWriter(new BufferedWriter(new ProxyFileWriter(checksumFile)));
            } else if ("r".equals(mode)) {
                synchronized (checksums) {
                    BufferedReader reader = new BufferedReader(new ProxyFileReader(checksumFile));
                    // Get checksums
                    String line;
                    String[] lineSplits;
                    while ((line = reader.readLine()) != null) {
                        lineSplits = line.split("\t", 2);
                        if (lineSplits.length != 2) {
                            throw new RuntimeException("Illegal lines found in the checksum file.");
                        }
                        this.checksums.put(lineSplits[1], lineSplits[0]);
                    }
                    reader.close();
                }
            } else throw new IOException("Unknown mode: " + mode);
        }

        public void add(@NonNull String fileName, @NonNull String checksum) {
            synchronized (checksums) {
                if (!"w".equals(mode)) throw new IllegalStateException("add is inaccessible in mode " + mode);
                writer.println(String.format("%s\t%s", checksum, fileName));
                this.checksums.put(fileName, checksum);
                writer.flush();
            }
        }

        @Nullable
        String get(String fileName) {
            synchronized (checksums) {
                return checksums.get(fileName);
            }
        }

        @Override
        public void close() {
            synchronized (checksums) {
                if (writer != null) writer.close();
            }
        }
    }
}
