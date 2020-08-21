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

package io.github.muntashirakon.AppManager.adb;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.server.common.Shell;
import io.github.muntashirakon.AppManager.servermanager.AppOps;
import io.github.muntashirakon.AppManager.servermanager.AppOpsManager;

public class AdbShell {
    @SuppressLint("StaticFieldLeak")
    private static AppOpsManager appOpsManager;

    public static class CommandResult {
        public int returnCode;
        public @NonNull List<String> stdout;

        CommandResult(@NonNull List<String> stdout, int returnCode) {
            this.returnCode = returnCode;
            this.stdout = stdout;
        }

        public boolean isSuccessful() {
            return returnCode == 0;
        }

        public String getStdout() {
            return TextUtils.join("\n", stdout);
        }
    }

    @NonNull
    synchronized public static CommandResult run(String command) throws IOException {
        if (appOpsManager == null) {
            appOpsManager = AppOps.getInstance(AppManager.getContext());
        }
        Shell.Result result;
        try {
            result = appOpsManager.runCommand(command);
        } catch (Exception e) {
            throw new IOException(e);
        }
        if (result == null) throw new IOException("Result is null");
        // Read standard output
        List<String> stdout = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(new StringReader(result.getMessage()))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) stdout.add(line.trim());
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Debug logs
        Log.d("AdbShell - Command", command);
        Log.d("AdbShell - Stdout", result.getMessage());
        Log.d("AdbShell - Return Code", String.valueOf(result.getStatusCode()));
        // Delete files
        return new AdbShell.CommandResult(stdout, result.getStatusCode());
    }
}
