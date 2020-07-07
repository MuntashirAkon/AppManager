package io.github.muntashirakon.AppManager.runner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import com.jaredrummler.android.shell.CommandResult;
import com.jaredrummler.android.shell.Shell;

import java.util.List;

public class RootShellRunner extends Runner {
    protected RootShellRunner(Context context) {
        super(context);
    }

    @SuppressLint("StaticFieldLeak")
    private static RootShellRunner rootShellRunner;
    public static RootShellRunner getInstance(Context context) {
        if (rootShellRunner == null) rootShellRunner = new RootShellRunner(context.getApplicationContext());
        return rootShellRunner;
    }

    public static Result run(Context context, String command) {
        return getInstance(context).run(command);
    }

    @Override
    public Result run() {
        CommandResult result = Shell.SU.run(TextUtils.join("; ", commands));
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
    }

    @Override
    protected Result run(String command) {
        clear();
        addCommand(command);
        return run();
    }
}
