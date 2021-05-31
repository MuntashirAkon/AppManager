// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.io.ProxyFile;
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
    public static ProxyFile getBackupDirectory() {
        return AppPref.getAppManagerDirectory();
    }

    @NonNull
    public static ProxyFile getTemporaryDirectory() {
        return new ProxyFile(getBackupDirectory(), TEMPORARY_DIRECTORY);
    }

    @NonNull
    public static ProxyFile getPackagePath(@NonNull String packageName) {
        return new ProxyFile(getBackupDirectory(), packageName);
    }

    @NonNull
    public static synchronized ProxyFile getTemporaryBackupPath() {
        String tmpFilename = "backup_" + System.currentTimeMillis();
        ProxyFile tmpFile = new ProxyFile(getTemporaryDirectory(), tmpFilename);
        int i = 0;
        while (tmpFile.exists()) {
            tmpFile = new ProxyFile(getTemporaryDirectory(), tmpFilename + "_" + (++i));
        }
        //noinspection ResultOfMethodCallIgnored
        tmpFile.mkdirs();
        return tmpFile;
    }

    @NonNull
    public static ProxyFile getApkBackupDirectory() {
        return new ProxyFile(getBackupDirectory(), APK_SAVING_DIRECTORY);
    }

    public static void createNoMediaIfNotExists() throws IOException {
        ProxyFile backupDirectory = getBackupDirectory();
        ProxyFile noMediaFile = new ProxyFile(backupDirectory, NO_MEDIA);
        if (noMediaFile.exists()) return;
        if (!backupDirectory.exists()) {
            backupDirectory.mkdirs();
        }
        noMediaFile.createNewFile();
    }

    public static class BackupFile {
        @NonNull
        private final ProxyFile backupPath;
        @NonNull
        private final ProxyFile tmpBackupPath;
        private final boolean isTemporary;

        public BackupFile(@NonNull ProxyFile backupPath, boolean hasTemporary) {
            this.backupPath = backupPath;
            this.isTemporary = hasTemporary;
            if (hasTemporary) {
                //noinspection ResultOfMethodCallIgnored
                backupPath.mkdirs();  // Create backup path if not exists
                tmpBackupPath = getTemporaryBackupPath();
            } else tmpBackupPath = this.backupPath;
        }

        @NonNull
        public ProxyFile getBackupPath() {
            return isTemporary ? tmpBackupPath : backupPath;
        }

        @NonNull
        public ProxyFile getMetadataFile() {
            return new ProxyFile(getBackupPath(), MetadataManager.META_FILE);
        }

        @NonNull
        public ProxyFile getChecksumFile(@CryptoUtils.Mode String mode) {
            return new ProxyFile(getBackupPath(), CHECKSUMS_TXT + CryptoUtils.getExtension(mode));
        }

        @NonNull
        public ProxyFile getMiscFile(@CryptoUtils.Mode String mode) {
            return new ProxyFile(getBackupPath(), MISC_TSV + CryptoUtils.getExtension(mode));
        }

        @NonNull
        public ProxyFile getRulesFile(@CryptoUtils.Mode String mode) {
            return new ProxyFile(getBackupPath(), RULES_TSV + CryptoUtils.getExtension(mode));
        }

        public void freeze() throws IOException {
            getFreezeFile().createNewFile();
        }

        public void unfreeze() {
            getFreezeFile().delete();
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean isFrozen() {
            return getFreezeFile().exists();
        }

        public boolean commit() {
            if (isTemporary) {
                return delete() && tmpBackupPath.renameTo(backupPath);
            }
            return true;
        }

        public boolean cleanup() {
            if (isTemporary) {
                //noinspection ResultOfMethodCallIgnored
                tmpBackupPath.forceDelete();
            }
            return false;
        }

        public boolean delete() {
            if (backupPath.exists()) {
                return backupPath.forceDelete();
            }
            return true;  // The backup path doesn't exist anyway
        }

        @NonNull
        private ProxyFile getFreezeFile() {
            return new ProxyFile(getBackupPath(), FREEZE);
        }
    }

    @NonNull
    private final String packageName;
    private final int userHandle;
    @NonNull
    private final String[] backupNames;
    @NonNull
    private final ProxyFile packagePath;

    /**
     * Create and handle {@link BackupFile}.
     *
     * @param packageName Name of the package whose backups has to be managed
     * @param userHandle  The user handle (aka user ID) to whom the package belong to
     * @param backupNames Name of the backups. If {@code null}, user handle will be used. If not
     *                    null, the backup names will have the format {@code userHandle_backupName}.
     */
    public BackupFiles(@NonNull String packageName, int userHandle, @Nullable String[] backupNames) {
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
        packagePath = getPackagePath(packageName);
    }

    public BackupFile[] getBackupPaths(boolean hasTemporary) {
        BackupFile[] backupFiles = new BackupFile[backupNames.length];
        for (int i = 0; i < backupNames.length; ++i) {
            backupFiles[i] = new BackupFile(new ProxyFile(packagePath, backupNames[i]), hasTemporary);
        }
        return backupFiles;
    }

    BackupFile[] getFreshBackupPaths() {
        BackupFile[] backupFiles = new BackupFile[backupNames.length];
        for (int i = 0; i < backupNames.length; ++i) {
            backupFiles[i] = new BackupFile(getFreshBackupPath(backupNames[i]), true);
        }
        return backupFiles;
    }

    private ProxyFile getFreshBackupPath(String backupName) {
        ProxyFile file = new ProxyFile(packagePath, backupName);
        int i = 0;
        while (file.exists()) {
            file = new ProxyFile(packagePath, backupName + "_" + (++i));
        }
        return file;
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

        public Checksum(@NonNull File checksumFile, String mode) throws IOException, RemoteException {
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
