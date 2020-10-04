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
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public abstract class Runner {
    public static final String TAG = "Runner";
    public static final String TOYBOX;

    static final String TOYBOX_SO_NAME = "libtoybox.so";
    static final File TOYBOX_SO_PATH;
    static final File DEFAULT_TOYBOX_BIN_PATH;

    static {
        TOYBOX_SO_PATH = new File(AppManager.getContext().getApplicationInfo().nativeLibraryDir, TOYBOX_SO_NAME);
        TOYBOX = TOYBOX_SO_PATH.getAbsolutePath();
        // FIXME(21/9/20): We no longer need this unless Google removes executable permission from JNI directory as well
        DEFAULT_TOYBOX_BIN_PATH = new File(PackageUtils.PACKAGE_STAGING_DIRECTORY, TOYBOX_SO_NAME);
    }

    public interface Result {
        boolean isSuccessful();

        List<String> getOutputAsList();

        List<String> getOutputAsList(int first_index);

        List<String> getOutputAsList(int first_index, int length);

        String getOutput();
    }

    private static RootShellRunner rootShellRunner;
    private static AdbShellRunner adbShellRunner;
    private static UserShellRunner userShellRunner;

    public static Runner getInstance() {
        if (AppPref.isRootEnabled()) {
            if (rootShellRunner == null) {
                rootShellRunner = new RootShellRunner();
                Log.d(TAG, "RootShellRunner");
            }
            return rootShellRunner;
        } else if (AppPref.isAdbEnabled()) {
            if (adbShellRunner == null) {
                adbShellRunner = new AdbShellRunner();
                Log.d(TAG, "AdbShellRunner");
            }
            return adbShellRunner;
        } else {
            if (userShellRunner == null) {
                userShellRunner = new UserShellRunner();
                Log.d(TAG, "UserShellRunner");
            }
            return userShellRunner;
        }
    }

    @NonNull
    synchronized public static Result runCommand(@NonNull String command) {
        return getInstance().run(command);
    }

    @NonNull
    synchronized public static Result runCommand(@NonNull String[] command) {
        StringBuilder cmd = new StringBuilder();
        for (String part: command) {
            cmd.append(RunnerUtils.escape(part)).append(" ");
        }
        return getInstance().run(cmd.toString());
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
