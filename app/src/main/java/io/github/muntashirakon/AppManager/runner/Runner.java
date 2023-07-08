// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Ops;

public abstract class Runner {
    public static final String TAG = Runner.class.getSimpleName();

    public static class Result {
        private final List<String> mStdout;
        private final List<String> mStderr;
        private final int mExitCode;

        Result(@NonNull List<String> stdout, @NonNull List<String> stderr, int exitCode) {
            mStdout = stdout;
            mStderr = stderr;
            mExitCode = exitCode;
            // Print stderr
            if (stderr.size() > 0) {
                Log.e(TAG, TextUtils.join("\n", stderr));
            }
        }

        public Result(int exitCode) {
            this(Collections.emptyList(), Collections.emptyList(), exitCode);
        }

        public Result() {
            this(1);
        }

        public boolean isSuccessful() {
            return mExitCode == 0;
        }

        public int getExitCode() {
            return mExitCode;
        }

        @NonNull
        public List<String> getOutputAsList() {
            return mStdout;
        }

        @NonNull
        public List<String> getOutputAsList(int firstIndex) {
            if (firstIndex >= mStdout.size()) {
                return Collections.emptyList();
            }
            return mStdout.subList(firstIndex, mStdout.size());
        }

        @NonNull
        public String getOutput() {
            return TextUtils.join("\n", mStdout);
        }

        public List<String> getStderr() {
            return mStderr;
        }
    }

    private static NormalShell sRootShell;
    private static PrivilegedShell sPrivilegedShell;
    private static NormalShell sNoRootShell;

    @NonNull
    private static Runner getInstance() {
        if (Ops.isRoot()) {
            return getRootInstance();
        } else if (LocalServices.alive()) {
            return getPrivilegedInstance();
        } else {
            return getNoRootInstance();
        }
    }

    @NonNull
    static Runner getRootInstance() {
        if (sRootShell == null) {
            sRootShell = new NormalShell(true);
            Log.d(TAG, "RootShell");
        }
        return sRootShell;
    }

    @NonNull
    private static Runner getPrivilegedInstance() {
        if (sPrivilegedShell == null) {
            sPrivilegedShell = new PrivilegedShell();
            Log.d(TAG, "PrivilegedShell");
        }
        return sPrivilegedShell;
    }

    private static Runner getNoRootInstance() {
        if (sNoRootShell == null) {
            sNoRootShell = new NormalShell(false);
            Log.d(TAG, "NoRootShell");
        }
        return sNoRootShell;
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
    synchronized private static Result runCommand(@NonNull Runner runner, @NonNull String command,
                                                  @Nullable InputStream inputStream) {
        return runner.run(command, inputStream);
    }

    @NonNull
    synchronized private static Result runCommand(@NonNull Runner runner, @NonNull String[] command,
                                                  @Nullable InputStream inputStream) {
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

    public abstract boolean isRoot();

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
