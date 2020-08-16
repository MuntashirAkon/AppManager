package io.github.muntashirakon.AppManager.adb;

import android.text.TextUtils;
import android.util.Log;

import com.tananaev.adblib.AdbConnection;
import com.tananaev.adblib.AdbStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.utils.Utils;

public class AdbShell {
    private static AdbConnection adbConnection;
    private static AdbStream shell;

    private static final String adbHost = "127.0.0.1";
    private static final int adbPort = 5555;
    private static final File cachePath = Objects.requireNonNull(AppManager.getContext().getExternalCacheDir());
    public static final File stdoutPath = new File(cachePath, ".stdout");
    public static final File cmdPath = new File(cachePath, ".cmd");
    public static final File retCodePath =  new File(cachePath, ".code");
    private static final String formattedCmdString = "#!/bin/sh\n%s\necho $? > " + retCodePath.getAbsolutePath();

    public static class CommandResult {
        public int returnCode;
        public @NonNull List<String> stdout;

        private CommandResult(@NonNull List<String> stdout, int returnCode) {
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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @NonNull
    synchronized public static CommandResult run(String command)
            throws IOException, InterruptedException, NoSuchAlgorithmException {
        if (adbConnection == null) {
            adbConnection = AdbConnectionManager.connect(AppManager.getContext(), adbHost, adbPort);
        }
        if (shell != null) shell.close();
        shell = adbConnection.open("shell:");
        if (!cachePath.exists()) cachePath.mkdir();
        // Write command
        try (FileOutputStream cmdStream = new FileOutputStream(cmdPath)) {
            cmdStream.write(String.format(formattedCmdString, command).getBytes());
        }
        // Execute command
        shell.write((" /bin/sh " + cmdPath.getAbsolutePath() + " | awk '{ print $0 }' > " + stdoutPath.getAbsolutePath() + "; exit\n").getBytes(), true);
        // Debug output
        while (!shell.isClosed()) {
            try {
                Log.d("AdbShell - Raw", new String(shell.read(), StandardCharsets.US_ASCII));
            } catch (Exception e) {
                break;
            }
        }
        // Wait for result
        while (!retCodePath.exists()) Thread.sleep(500);
        while (!stdoutPath.exists()) Thread.sleep(500);
        // Read exit code
        final String retCode = Utils.getFileContent(retCodePath);
        int returnCode = -1;
        try {
            returnCode = Integer.parseInt(retCode.trim());
        } catch (Exception ignored) {}
        // Read standard output
        List<String> stdout = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(stdoutPath))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) stdout.add(line.trim());
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Debug logs
        Log.d("AdbShell - Command", command);
        Log.d("AdbShell - Stdout", stdout.toString());
        Log.d("AdbShell - Return Code", returnCode + " (" + retCode + ")");
        // Delete files
        stdoutPath.delete();
        retCodePath.delete();
        return new CommandResult(stdout, returnCode);
    }
}
