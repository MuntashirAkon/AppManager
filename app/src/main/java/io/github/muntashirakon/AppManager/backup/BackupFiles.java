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

        public PrivilegedFile getMetadataFile() {
            return new PrivilegedFile(getBackupPath(), MetadataManager.META_FILE);
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
                tmpBackupPath.delete();
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

    BackupFiles(@NonNull String packageName, int userHandle, @Nullable String[] backupNames) {
        this.packageName = packageName;
        this.userHandle = userHandle;
        if (backupNames == null) backupNames = new String[]{String.valueOf(userHandle)};
        this.backupNames = backupNames;
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
}
