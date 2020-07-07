package io.github.muntashirakon.AppManager.runner;

import android.content.Context;
import android.text.TextUtils;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import io.github.muntashirakon.AppManager.adb.AdbShell;

public class AdbShellRunner extends Runner {
    protected AdbShellRunner(Context context) {
        super(context);
    }

    @Override
    public Result run() {
        try {
            AdbShell.CommandResult result = AdbShell.run(TextUtils.join("; ", commands));
            clear();
            lastResult = new Result() {
                @Override
                public boolean isSuccessful() {
                    return result.isSuccessful();
                }

                @Override
                public List<String> getOutputAsList() {
                    return result.stdout;
                }

                @Override
                public List<String> getOutputAsList(int first_index) {
                    return result.stdout.subList(first_index, result.stdout.size());
                }

                @Override
                public List<String> getOutputAsList(int first_index, int length) {
                    return result.stdout.subList(first_index, first_index + length);
                }

                @Override
                public String getOutput() {
                    return result.getStdout();
                }
            };
            return lastResult;
        } catch (InterruptedException | IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected Result run(String command) {
        clear();
        addCommand(command);
        return run();
    }
}
