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
        Thread thread = new Thread(() -> {
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
                        return result.getOutputAsList();
                    }

                    @Override
                    public List<String> getOutputAsList(int first_index) {
                        return result.getOutputAsList().subList(first_index, result.getOutputAsList().size());
                    }

                    @Override
                    public List<String> getOutputAsList(int first_index, int length) {
                        return result.getOutputAsList().subList(first_index, first_index + length);
                    }

                    @Override
                    public String getOutput() {
                        return result.getOutput();
                    }
                };
            } catch (InterruptedException | IOException | NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        });
        try {
            thread.start();
            thread.join(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }
        return lastResult;
    }

    @Override
    protected Result run(String command) {
        clear();
        addCommand(command);
        return run();
    }
}
