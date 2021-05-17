// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.server.common.Shell;
import io.github.muntashirakon.AppManager.servermanager.ApiSupporter;

public class AdbShell {
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
        Shell.Result result;
        try {
            result = ApiSupporter.runCommand(command);
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
