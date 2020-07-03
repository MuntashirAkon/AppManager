package io.github.muntashirakon.AppManager.runner;

import android.annotation.SuppressLint;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

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
    public static Runner getInstance(Context context) {
        // TODO: Determine class type based on preferences
        if (runner == null) runner = new RootShellRunner(context.getApplicationContext());
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
