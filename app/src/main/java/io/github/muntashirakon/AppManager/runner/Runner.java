package io.github.muntashirakon.AppManager.runner;

import android.annotation.SuppressLint;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.utils.AppPref;

public class Runner {

    public interface Result {
        boolean isSuccessful();
        List<String> getOutputAsList();
        List<String> getOutputAsList(int first_index);
        List<String> getOutputAsList(int first_index, int length);
        String getOutput();
    }

    @SuppressLint("StaticFieldLeak")
    private static Runner runner;
    private static boolean isAdb = false;
    public static Runner getInstance(Context context) {
        if (runner == null || (isAdb && !AppPref.isAdbEnabled()) || (!isAdb && AppPref.isAdbEnabled())) {
            if (AppPref.isRootEnabled()) {
                runner = new RootShellRunner(context.getApplicationContext());
                isAdb = false;
            } else {
                runner = new AdbShellRunner(context.getApplicationContext());
                isAdb = true;
            }
        }
        return runner;
    }

    public static Result run(Context context, String command) {
        return getInstance(context).run(command);
    }

    protected List<String> commands;
    public void addCommand(String command) {
        commands.add(command);
    }

    public void clear() {
        commands.clear();
    }

    public Result run() {
        return null;
    }

    protected static Result lastResult;
    public static Result getLastResult() {
        return lastResult;
    }

    protected Context context;
    protected Runner(Context context) {
        this.context = context;
        this.commands = new ArrayList<>();
    }

    protected Result run(String command) {
        return null;
    }

}
