package io.github.muntashirakon.AppManager.adb;

import com.tananaev.adblib.AdbConnection;
import com.tananaev.adblib.AdbStream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;

public class AdbShell {
    private static AdbConnection adbConnection;
    private static AdbStream shell;
    private static final String adbHost = "127.0.0.1";
    private static final int adbPort = 5555;

    public interface CommandResult {
        boolean isSuccessful();
        String getOutput();
        List<String> getOutputAsList();
    }

    @NonNull
    public static CommandResult run(String command)
            throws IOException, InterruptedException, NoSuchAlgorithmException {
        if (adbConnection == null) {
            adbConnection = AdbConnectionManager.connect(AppManager.getContext(), adbHost, adbPort);
        }
        if (shell == null) shell = adbConnection.open("shell:");
        shell.write(command + "; echo $? AlsoSprachZarathustra\n");
        String lines;
        // Strip previous command
        do {
            lines = new String(shell.read(), StandardCharsets.US_ASCII);
        } while (!lines.contains("AlsoSprachZarathustra"));
        // Strip null
        do {
            lines = new String(shell.read(), StandardCharsets.US_ASCII);
        } while (!lines.equals("^@"));
        // Strip this command
        do {
            lines = new String(shell.read(), StandardCharsets.US_ASCII);
        } while (!lines.contains("AlsoSprachZarathustra"));
        // Get output
        lines = new String(shell.read(), StandardCharsets.US_ASCII);
        StringBuilder sb = new StringBuilder();
        while (!lines.contains("AlsoSprachZarathustra")) {
            sb.append(lines);
            lines = new String(shell.read(), StandardCharsets.US_ASCII);
        }
        int returnCode = Integer.parseInt(lines.substring(0, lines.indexOf(" AlsoSprachZarathustra")));
        return new CommandResult() {
            @Override
            public boolean isSuccessful() {
                return returnCode == 0;
            }

            @Override
            public String getOutput() {
                return sb.toString();
            }

            @Override
            public List<String> getOutputAsList() {
                return Arrays.asList(sb.toString().split("\\r?\\n"));
            }
        };
    }


}
