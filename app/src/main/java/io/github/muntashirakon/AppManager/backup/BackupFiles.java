/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.backup;

import android.os.Environment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;

public class BackupFiles {
    static final String APK_SAVING_DIRECTORY = "apks";
    static final String TEMPORARY_DIRECTORY = "tmp";

    private static final PrivilegedFile DEFAULT_BACKUP_PATH = new PrivilegedFile(Environment.getExternalStorageDirectory(), "AppManager");

    @NonNull
    public static PrivilegedFile getBackupDirectory() {
        return DEFAULT_BACKUP_PATH;
    }

    @NonNull
    public static PrivilegedFile getTemporaryDirectory() {
        return new PrivilegedFile(getBackupDirectory(), TEMPORARY_DIRECTORY);
    }

    @NonNull
    public static PrivilegedFile getPackagePath(@NonNull String packageName) {
        return new PrivilegedFile(getBackupDirectory(), packageName);
    }

    @NonNull
    public static PrivilegedFile getTemporaryBackupPath() {
        PrivilegedFile tmpFile = new PrivilegedFile(getTemporaryDirectory(), String.valueOf(System.currentTimeMillis()));
        //noinspection ResultOfMethodCallIgnored
        tmpFile.mkdirs();
        return tmpFile;
    }

    @NonNull
    public static PrivilegedFile getApkBackupDirectory() {
        return new PrivilegedFile(getBackupDirectory(), APK_SAVING_DIRECTORY);
    }

    public static class BackupFile {
        @NonNull
        private PrivilegedFile backupPath;
        @NonNull
        private PrivilegedFile tmpBackupPath;
        private boolean isTemporary;

        public BackupFile(@NonNull PrivilegedFile backupPath, boolean hasTemporary) {
            this.backupPath = backupPath;
            this.isTemporary = hasTemporary;
            if (hasTemporary) {
                //noinspection ResultOfMethodCallIgnored
                backupPath.mkdirs();  // Create backup path if not exists
                tmpBackupPath = getTemporaryBackupPath();
            } else tmpBackupPath = this.backupPath;
        }

        @NonNull
        public PrivilegedFile getBackupPath() {
            return isTemporary ? tmpBackupPath : backupPath;
        }

        @NonNull
        public PrivilegedFile getMetadataFile() {
            return new PrivilegedFile(getBackupPath(), MetadataManager.META_FILE);
        }

        @NonNull
        public PrivilegedFile getChecksumFile(@BackupMode.Mode int mode) {
            return new PrivilegedFile(getBackupPath(), BackupManager.CHECKSUMS_TXT + BackupMode.getExtension(mode));
        }

        @NonNull
        public PrivilegedFile getPermsFile(@BackupMode.Mode int mode) {
            return new PrivilegedFile(getBackupPath(), BackupManager.PERMS_TSV + BackupMode.getExtension(mode));
        }

        @NonNull
        public PrivilegedFile getRulesFile(@BackupMode.Mode int mode) {
            return new PrivilegedFile(getBackupPath(), BackupManager.RULES_TSV + BackupMode.getExtension(mode));
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
    }

    @NonNull
    private String packageName;
    private int userHandle;
    @NonNull
    private String[] backupNames;
    @NonNull
    private PrivilegedFile packagePath;

    /**
     * Create and handle {@link BackupFile}.
     *
     * @param packageName Name of the package whose backups has to be managed
     * @param userHandle  The user handle (aka user ID) to whom the package belong to
     * @param backupNames Name of the backups. If {@code null}, user handle will be used. If not
     *                    null, the backup names will have the format {@code userHandle_backupName}.
     */
    BackupFiles(@NonNull String packageName, int userHandle, @Nullable String[] backupNames) {
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

    BackupFile[] getBackupPaths(boolean hasTemporary) {
        BackupFile[] backupFiles = new BackupFile[backupNames.length];
        for (int i = 0; i < backupNames.length; ++i) {
            backupFiles[i] = new BackupFile(new PrivilegedFile(packagePath, backupNames[i]), hasTemporary);
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

    private PrivilegedFile getFreshBackupPath(String backupName) {
        PrivilegedFile file = new PrivilegedFile(packagePath, backupName);
        int i = 0;
        while (file.exists()) {
            file = new PrivilegedFile(packagePath, backupName + "_" + (++i));
        }
        return file;
    }

    static class Checksum implements Closeable {
        private PrintWriter writer;
        private HashMap<String, String> checksums = new HashMap<>();
        private String mode;

        @NonNull
        public static String[] getCertChecksums(@NonNull Checksum checksum) {
            List<String> certChecksums = new ArrayList<>();
            for (String name : checksum.checksums.keySet()) {
                if (name.startsWith(BackupManager.CERT_PREFIX)) {
                    certChecksums.add(checksum.checksums.get(name));
                }
            }
            return certChecksums.toArray(new String[0]);
        }

        Checksum(@NonNull File checksumFile, String mode) throws IOException {
            this.mode = mode;
            if ("w".equals(mode)) {
                writer = new PrintWriter(new BufferedWriter(new FileWriter(checksumFile)));
            } else if ("r".equals(mode)) {
                BufferedReader reader = new BufferedReader(new FileReader(checksumFile));
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
            } else throw new IOException("Unknown mode: " + mode);
        }

        void add(@NonNull String fileName, @NonNull String checksum) {
            if (!"w".equals(mode)) throw new IllegalStateException("add is inaccessible in mode " + mode);
            writer.println(String.format("%s\t%s", checksum, fileName));
            this.checksums.put(fileName, checksum);
            writer.flush();
        }

        @Nullable
        String get(String fileName) {
            return checksums.get(fileName);
        }

        @Override
        public void close() {
            if (writer != null) writer.close();
        }
    }
}
