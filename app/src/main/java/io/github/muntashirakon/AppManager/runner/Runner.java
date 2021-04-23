/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.runner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;
import com.android.internal.util.TextUtils;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.AppPref;

import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Runner {
    public static final String TAG = "Runner";

    @StringDef({MODE_AUTO, MODE_ROOT, MODE_ADB, MODE_NO_ROOT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {
    }

    public static final String MODE_AUTO = "auto";
    public static final String MODE_ROOT = "root";
    public static final String MODE_ADB = "adb";
    public static final String MODE_NO_ROOT = "no-root";

    public static class Result {
        private final List<String> stdout;
        private final List<String> stderr;
        private final int exitCode;

        public Result(@NonNull List<String> stdout, @NonNull List<String> stderr, int exitCode) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
            // Print stderr
            if (stderr.size() > 0) Log.e("Runner", android.text.TextUtils.join("\n", stderr));
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

    private static RootShellRunner rootShellRunner;
    private static AdbShellRunner adbShellRunner;
    private static UserShellRunner userShellRunner;

    @NonNull
    public static Runner getInstance() {
        if (AppPref.isRootEnabled()) {
            return getRootInstance();
        } else if (AppPref.isAdbEnabled()) {
            return getAdbInstance();
        } else {
            return getUserInstance();
        }
    }

    @NonNull
    public static Runner getRootInstance() {
        if (rootShellRunner == null) {
            rootShellRunner = new RootShellRunner();
            Log.d(TAG, "RootShellRunner");
        }
        return rootShellRunner;
    }

    @NonNull
    public static Runner getAdbInstance() {
        if (adbShellRunner == null) {
            adbShellRunner = new AdbShellRunner();
            Log.d(TAG, "AdbShellRunner");
        }
        return adbShellRunner;
    }

    public static Runner getUserInstance() {
        if (userShellRunner == null) {
            userShellRunner = new UserShellRunner();
            Log.d(TAG, "UserShellRunner");
        }
        return userShellRunner;
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
    public abstract Result runCommand();

    @NonNull
    private Result run(@NonNull String command, @Nullable InputStream inputStream) {
        clear();
        addCommand(command);
        if (inputStream != null) add(inputStream);
        return runCommand();
    }
}
