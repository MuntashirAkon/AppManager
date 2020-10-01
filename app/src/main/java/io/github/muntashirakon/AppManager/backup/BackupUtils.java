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

import android.text.TextUtils;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.runner.RootShellRunner;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public final class BackupUtils {
    @Nullable
    public static MetadataManager.Metadata getBackupInfo(String packageName) {
        MetadataManager.Metadata[] metadata = MetadataManager.getMetadata(packageName);
        if (metadata.length == 0) return null;
        int maxIndex = 0;
        long maxTime = 0;
        for (int i = 0; i < metadata.length; ++i) {
            if (metadata[i].backupTime > maxTime) {
                maxIndex = i;
                maxTime = metadata[i].backupTime;
            }
        }
        return metadata[maxIndex];
    }

    @NonNull
    public static List<String> getBackupApplications() {
        PrivilegedFile backupPath = BackupFiles.getBackupDirectory();
        List<String> packages;
        String[] files = backupPath.list((dir, name) -> new PrivilegedFile(dir, name).isDirectory());
        if (files != null) packages = new ArrayList<>(Arrays.asList(files));
        else return new ArrayList<>();
        packages.remove(BackupFiles.APK_SAVING_DIRECTORY);
        packages.remove(BackupFiles.TEMPORARY_DIRECTORY);
        // We don't need to check the contents of the packages at this stage.
        // It's the caller's job to check contents if needed.
        return packages;
    }

    @NonNull
    static Pair<Integer, Integer> getUidAndGid(String filepath, int uid) {
        // Default UID and GID should be the same as the kernel user ID, and will fallback to it
        // if the stat command fails
        Pair<Integer, Integer> defaultUidGid = new Pair<>(uid, uid);
        Runner.Result result = RootShellRunner.runCommand(String.format("stat -c \"%%u %%g\" \"%s\"", filepath));
        if (!result.isSuccessful()) return defaultUidGid;
        String[] uidGid = result.getOutput().split(" ");
        if (uidGid.length != 2) return defaultUidGid;
        // Fix for Magisk bug
        if (uidGid[0].equals("0")) return defaultUidGid;
        try {
            // There could be other underlying bugs as well
            return new Pair<>(Integer.parseInt(uidGid[0]), Integer.parseInt(uidGid[1]));
        } catch (Exception e) {
            return defaultUidGid;
        }
    }

    static boolean hasKeyStore(int uid) {
        // FIXME(10/7/20): Check for /data/misc/keystore/.masterkey as well?
        return PackageUtils.hasKeyStore(uid);
    }

    @Nullable
    static String getShortBackupName(@NonNull String backupFileName) {
        if (TextUtils.isDigitsOnly(backupFileName)) {
            // It's already a user handle
            return null;
        } else {
            int firstUnderscore = backupFileName.indexOf('_');
            if (firstUnderscore != -1) {
                // Found an underscore
                String userHandle = backupFileName.substring(0, firstUnderscore);
                if (TextUtils.isDigitsOnly(userHandle)) {
                    // The new backup system
                    return backupFileName.substring(firstUnderscore + 1);
                }
            }
            // Could be the old naming style
            throw new IllegalArgumentException("Invalid backup name " + backupFileName);
        }
    }

    static int getUserHandleFromBackupName(@NonNull String backupFileName) {
        if (TextUtils.isDigitsOnly(backupFileName)) return Integer.parseInt(backupFileName);
        else {
            int firstUnderscore = backupFileName.indexOf('_');
            if (firstUnderscore != -1) {
                // Found an underscore
                String userHandle = backupFileName.substring(0, firstUnderscore);
                if (TextUtils.isDigitsOnly(userHandle)) {
                    // The new backup system
                    return Integer.parseInt(userHandle);
                }
            }
            throw new IllegalArgumentException("Invalid backup name");
        }
    }
}
