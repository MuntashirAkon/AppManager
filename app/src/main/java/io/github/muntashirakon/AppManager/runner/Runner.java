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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.AppPref;

public abstract class Runner {
    public static final String TAG = "Runner";
    public static final String TOYBOX;

    static final String TOYBOX_SO_NAME = "libtoybox.so";
    static final File TOYBOX_SO_PATH;

    static {
        TOYBOX_SO_PATH = new File(AppManager.getContext().getApplicationInfo().nativeLibraryDir, TOYBOX_SO_NAME);
        TOYBOX = TOYBOX_SO_PATH.getAbsolutePath();
    }

    public interface Result {
        boolean isSuccessful();

        @NonNull
        List<String> getOutputAsList();

        @NonNull
        List<String> getOutputAsList(int first_index);

        @NonNull
        List<String> getOutputAsList(int first_index, int length);

        @NonNull
        String getOutput();
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
        return runCommand(getInstance(), command);
    }

    @NonNull
    synchronized public static Result runCommand(@NonNull String[] command) {
        return runCommand(getInstance(), command);
    }

    @NonNull
    synchronized public static Result runCommand(@NonNull Runner runner, @NonNull String command) {
        return runner.run(command);
    }

    @NonNull
    synchronized public static Result runCommand(@NonNull Runner runner, @NonNull String[] command) {
        StringBuilder cmd = new StringBuilder();
        for (String part: command) {
            cmd.append(RunnerUtils.escape(part)).append(" ");
        }
        return runCommand(runner, cmd.toString());
    }

    protected List<String> commands;

    public void addCommand(String command) {
        commands.add(command);
    }

    public void clear() {
        commands.clear();
    }

    @WorkerThread
    @NonNull
    public abstract Result runCommand();

    protected static Result lastResult;

    public static Result getLastResult() {
        return lastResult;
    }

    protected Runner() {
        this.commands = new ArrayList<>();
    }

    @NonNull
    protected abstract Result run(@NonNull String command);
}
