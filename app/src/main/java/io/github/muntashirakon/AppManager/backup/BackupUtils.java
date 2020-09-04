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

import android.util.Pair;

import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
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
        try (MetadataManager metadataManager = MetadataManager.getInstance(packageName)) {
            PrivilegedFile backupPath = new PrivilegedFile(BackupFiles.getPackagePath(packageName), String.valueOf(0));  // FIXME: Get current user handle
            metadataManager.readMetadata(new BackupFiles.BackupFile(backupPath, false));
            return metadataManager.getMetadata();
        } catch (JSONException e) {
            return null;
        }
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
        for (Iterator<String> it = packages.iterator(); it.hasNext(); ) {
            if (!MetadataManager.hasMetadata(it.next())) it.remove();
        }
        return packages;
    }

    @NonNull
    static String getSha256Sum(@NonNull File[] files) {
        if (files.length == 1) return PackageUtils.getSha256Checksum(files[0]);

        StringBuilder checksums = new StringBuilder();
        for (File file : files) {
            String checksum = PackageUtils.getSha256Checksum(file);
            checksums.append(checksum);
        }
        return PackageUtils.getSha256Checksum(checksums.toString().getBytes());
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
}
