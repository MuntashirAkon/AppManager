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

package io.github.muntashirakon.AppManager.apk.splitapk;

import android.util.Log;

import java.io.File;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;

public final class SplitApkShellInstaller {
    public static final String TAG = "SASI";
    // https://cs.android.com/android/_/android/platform/system/core/+/5b940dc7f9c0364d84469cad7b47a5ffaa33600b:adb/client/adb_install.cpp;drc=71afeb9a5e849e8752c470aa31c568be2e48d0b6;l=538
    public static boolean installMultiple(@NonNull File[] apkFiles) {
        long total_size = 0;
        // Get file size
        try {
            for (File apkFile : apkFiles) total_size += apkFile.length();
        } catch (SecurityException e) {
            e.printStackTrace();
            Log.e(TAG, "InstallMultiple: Cannot access files.");
            return false;
        }
        // Create install session
        String install_cmd = RunnerUtils.CMD_PM;
        StringBuilder cmd = new StringBuilder(install_cmd).append(" install-create -r -S ")
                .append(total_size).append(" -i ").append(BuildConfig.APPLICATION_ID);
        for (File apkFile : apkFiles) cmd.append(" \"").append(apkFile.getAbsolutePath()).append("\"");
        Runner.Result result = Runner.runCommand(cmd.toString());
        String buf = result.getOutput();
        if (!result.isSuccessful() || buf == null || !buf.contains("Success")) {
            Log.e(TAG, "InstallMultiple: Failed to create install session.");
            return false;
        }
        int start = buf.indexOf('[');
        int end = buf.indexOf(']');
        int session_id = -1;
        try {
            session_id = Integer.parseInt(buf.substring(start + 1, end));
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            e.printStackTrace();
            Log.e(TAG, "InstallMultiple: Failed to parse session id.");
            return false;
        }
        if (session_id < 0) {
            Log.e(TAG, "InstallMultiple: Session id cannot be less than 0.");
            return false;
        }
        // Valid session, now stream apks
        for (File apkFile : apkFiles) {
            cmd = new StringBuilder(install_cmd).append(" install-write -S ")
                    .append(apkFile.length()).append(" ").append(session_id).append(" ")
                    .append(apkFile.getName()).append(" \"").append(apkFile.getAbsolutePath()).append("\"");
            result = Runner.runCommand(cmd.toString());
            buf = result.getOutput();
            if (!result.isSuccessful() || buf == null || !buf.contains("Success")) {
                Log.e(TAG, String.format("InstallMultiple: Failed to write %s.", apkFile.getName()));
                return abandon(install_cmd, session_id);
            }
        }
        // Finalize session
        result = Runner.runCommand(install_cmd + " install-commit " + session_id);
        buf = result.getOutput();
        if (!result.isSuccessful() || buf == null || !buf.contains("Success")) {
            Log.e(TAG, "Abandon: Failed to abandon session.");
            return abandon(install_cmd, session_id);
        }
        return true;
    }

    private static boolean abandon(String install_cmd, int session_id) {
        Runner.Result result = Runner.runCommand(install_cmd + " install-abandon " + session_id);
        String buf = result.getOutput();
        if (!result.isSuccessful() || buf == null || !buf.contains("Success")) {
            Log.e(TAG, "Abandon: Failed to abandon session.");
        }
        return false;
    }
}
