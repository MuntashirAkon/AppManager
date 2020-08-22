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

package io.github.muntashirakon.AppManager.apk;

import android.util.Log;

import java.io.File;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;

public final class PackageInstallerShell implements IPackageInstaller {
    public static final String TAG = "SASI";

    private static final String installCmd = RunnerUtils.CMD_PM;

    private static PackageInstallerShell INSTANCE;

    public static PackageInstallerShell getInstance() {
        if (INSTANCE == null) INSTANCE = new PackageInstallerShell();
        return INSTANCE;
    }

    int sessionId = -1;

    private PackageInstallerShell() {
    }

    // https://cs.android.com/android/_/android/platform/system/core/+/5b940dc7f9c0364d84469cad7b47a5ffaa33600b:adb/client/adb_install.cpp;drc=71afeb9a5e849e8752c470aa31c568be2e48d0b6;l=538
    @Override
    public boolean installMultiple(@NonNull File[] apkFiles) {
        long totalSize = 0;
        // Get file size
        try {
            for (File apkFile : apkFiles) totalSize += apkFile.length();
        } catch (SecurityException e) {
            Log.e(TAG, "InstallMultiple: Cannot access apk files.", e);
            return false;
        }
        // Create install session
        StringBuilder cmd = new StringBuilder(installCmd).append(" install-create -r -S ")
                .append(totalSize).append(" -i ").append(BuildConfig.APPLICATION_ID);
        for (File apkFile : apkFiles)
            cmd.append(" \"").append(apkFile.getAbsolutePath()).append("\"");
        Runner.Result result = Runner.runCommand(cmd.toString());
        String buf = result.getOutput();
        if (!result.isSuccessful() || buf == null || !buf.contains("Success")) {
            Log.e(TAG, "InstallMultiple: Failed to create install session.");
            return false;
        }
        int start = buf.indexOf('[');
        int end = buf.indexOf(']');
        try {
            sessionId = Integer.parseInt(buf.substring(start + 1, end));
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            Log.e(TAG, "InstallMultiple: Failed to parse session id.", e);
            return false;
        }
        if (sessionId < 0) {
            Log.e(TAG, "InstallMultiple: Session id cannot be less than 0.");
            return false;
        }
        // Write apk files
        for (File apkFile : apkFiles) {
            cmd = new StringBuilder(installCmd).append(" install-write -S ")
                    .append(apkFile.length()).append(" ").append(sessionId).append(" ")
                    .append(apkFile.getName()).append(" \"").append(apkFile.getAbsolutePath()).append("\"");
            result = Runner.runCommand(cmd.toString());
            buf = result.getOutput();
            if (!result.isSuccessful() || buf == null || !buf.contains("Success")) {
                Log.e(TAG, String.format("InstallMultiple: Failed to write %s.", apkFile.getName()));
                return abandon();
            }
        }
        // Finalize session
        result = Runner.runCommand(installCmd + " install-commit " + sessionId);
        buf = result.getOutput();
        if (!result.isSuccessful() || buf == null || !buf.contains("Success")) {
            Log.e(TAG, "Abandon: Failed to abandon session.");
            return abandon();
        }
        return true;
    }

    private boolean abandon() {
        Runner.Result result = Runner.runCommand(installCmd + " install-abandon " + sessionId);
        String buf = result.getOutput();
        if (!result.isSuccessful() || buf == null || !buf.contains("Success")) {
            Log.e(TAG, "Abandon: Failed to abandon session.");
        }
        return false;
    }
}
