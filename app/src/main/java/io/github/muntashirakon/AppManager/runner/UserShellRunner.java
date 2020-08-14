package io.github.muntashirakon.AppManager.runner;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import eu.chainfire.libsuperuser.Shell;

public class UserShellRunner extends Runner {
    @SuppressLint("StaticFieldLeak")
    private static UserShellRunner rootShellRunner;
    public static UserShellRunner getInstance() {
        if (rootShellRunner == null) rootShellRunner = new UserShellRunner();
        return rootShellRunner;
    }

    public static Result runCommand(String command) {
        return getInstance().run(command);
    }

    @Override
    public Result runCommand() {
        List<String> stdout = Collections.synchronizedList(new ArrayList<>());
        List<String> stderr = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger retVal = new AtomicInteger(FAILED_RET_VAL);
        try {
            retVal.set(eu.chainfire.libsuperuser.Shell.Pool.SH.run(TextUtils.join("; ", commands), stdout, stderr, true));
            if (stderr.size() > 0) Log.e("RootShellRunner", TextUtils.join("\n", stderr));
        } catch (Shell.ShellDiedException e) {
            e.printStackTrace();
        }
        clear();
        lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return retVal.get() == 0;
            }

            @Override
            public List<String> getOutputAsList() {
                return stdout;
            }

            @Override
            public List<String> getOutputAsList(int first_index) {
                return stdout.subList(first_index, stdout.size());
            }

            @Override
            public List<String> getOutputAsList(int first_index, int length) {
                return stdout.subList(first_index, first_index + length);
            }

            @Override
            public String getOutput() {
                return TextUtils.join("\n", stdout);
            }
        };
        return lastResult;
    }

    @Override
    protected Result run(String command) {
        clear();
        addCommand(command);
        return runCommand();
    }
}
