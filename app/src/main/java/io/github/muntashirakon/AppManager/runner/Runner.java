// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.internal.util.TextUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Ops;

public abstract class Runner {
    public static final String TAG = "Runner";

    public static class Result {
        private final List<String> stdout;
        private final List<String> stderr;
        private final int exitCode;

        public Result(@NonNull List<String> stdout, @NonNull List<String> stderr, int exitCode) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
            // Print stderr
            if (stderr.size() > 0) {
                Log.e("Runner", TextUtils.join("\n", stderr));
            }
        }

        public Result(int exitCode) {
            this(Collections.emptyList(), Collections.emptyList(), exitCode);
        }

        public Result() {
            this(1);
        }

        public boolean isSuccessful() {
            return exitCode == 0;
        }

        public int getExitCode() {
            return exitCode;
        }

        @NonNull
        public List<String> getOutputAsList() {
            return stdout;
        }

        @NonNull
        public List<String> getOutputAsList(int firstIndex) {
            if (firstIndex >= stdout.size()) {
                return Collections.emptyList();
            }
            return stdout.subList(firstIndex, stdout.size());
        }

        @NonNull
        public String getOutput() {
            return TextUtils.join("\n", stdout);
        }

        public List<String> getStderr() {
            return stderr;
        }
    }

    private static RootShell rootShell;
    private static AdbShell adbShell;
    private static LocalShell localShell;

    @NonNull
    public static Runner getInstance() {
        if (Ops.isRoot()) {
            return getRootInstance();
        } else if (Ops.isAdb()) {
            return getAdbInstance();
        } else {
            return getUserInstance();
        }
    }

    @NonNull
    public static Runner getRootInstance() {
        if (rootShell == null) {
            rootShell = new RootShell();
            Log.d(TAG, "RootShell");
        }
        return rootShell;
    }

    @NonNull
    private static Runner getAdbInstance() {
        if (adbShell == null) {
            adbShell = new AdbShell();
            Log.d(TAG, "AdbShell");
        }
        return adbShell;
    }

    private static Runner getUserInstance() {
        if (localShell == null) {
            localShell = new LocalShell();
            Log.d(TAG, "LocalShell");
        }
        return localShell;
    }

    @NonNull
    synchronized public static Result runCommand(@NonNull String command) {
        return runCommand(getInstance(), command, null);
    }

    @NonNull
    synchronized public static Result runCommand(@NonNull String[] command) {
        return runCommand(getInstance(), command, null);
    }

    @NonNull
    synchronized public static Result runCommand(@NonNull String command, @Nullable InputStream inputStream) {
        return runCommand(getInstance(), command, inputStream);
    }

    @NonNull
    synchronized public static Result runCommand(@NonNull String[] command, @Nullable InputStream inputStream) {
        return runCommand(getInstance(), command, inputStream);
    }

    @NonNull
    synchronized public static Result runCommand(@NonNull Runner runner, @NonNull String command) {
        return runner.run(command, null);
    }

    @NonNull
    synchronized public static Result runCommand(@NonNull Runner runner, @NonNull String[] command) {
        StringBuilder cmd = new StringBuilder();
        for (String part : command) {
            cmd.append(RunnerUtils.escape(part)).append(" ");
        }
        return runCommand(runner, cmd.toString(), null);
    }

    @NonNull
    synchronized public static Result runCommand(@NonNull Runner runner, @NonNull String command, @Nullable InputStream inputStream) {
        return runner.run(command, inputStream);
    }

    @NonNull
    synchronized public static Result runCommand(@NonNull Runner runner, @NonNull String[] command, @Nullable InputStream inputStream) {
        StringBuilder cmd = new StringBuilder();
        for (String part : command) {
            cmd.append(RunnerUtils.escape(part)).append(" ");
        }
        return runCommand(runner, cmd.toString(), inputStream);
    }

    protected final List<String> commands;
    protected final List<InputStream> inputStreams;

    protected Runner() {
        this.commands = new ArrayList<>();
        this.inputStreams = new ArrayList<>();
    }

    public void addCommand(@NonNull String command) {
        commands.add(command);
    }

    public void add(@NonNull InputStream inputStream) {
        inputStreams.add(inputStream);
    }

    public void clear() {
        commands.clear();
        inputStreams.clear();
    }

    @WorkerThread
    @NonNull
    protected abstract Result runCommand();

    @NonNull
    private Result run(@NonNull String command, @Nullable InputStream inputStream) {
        try {
            clear();
            addCommand(command);
            if (inputStream != null) {
                add(inputStream);
            }
            return runCommand();
        } finally {
            clear();
        }
    }
}
