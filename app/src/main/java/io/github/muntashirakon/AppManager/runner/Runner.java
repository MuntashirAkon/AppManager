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
import io.github.muntashirakon.AppManager.utils.AppPref;

public abstract class Runner {
    public static final int FAILED_RET_VAL = -500;  // An impossible value
    public static final String TOYBOX;

    static final String TOYBOX_SO_NAME = "toybox.so";
    static final String TOYBOX_BIN_NAME = "toybox";
    static final boolean toyboxInJNIDir;

    static {
        File jniToybox = new File(AppManager.getContext().getApplicationInfo().nativeLibraryDir, TOYBOX_SO_NAME);
        if (!jniToybox.exists()) {
            jniToybox = new File(AppManager.getContext().getFilesDir(), TOYBOX_BIN_NAME);
            toyboxInJNIDir = false;
        } else toyboxInJNIDir = true;
        TOYBOX = jniToybox.getAbsolutePath();
    }

    public interface Result {
        boolean isSuccessful();

        List<String> getOutputAsList();

        List<String> getOutputAsList(int first_index);

        List<String> getOutputAsList(int first_index, int length);

        String getOutput();
    }

    private static Runner runner;
    private static boolean isAdb = false;
    private static boolean isRoot = false;

    public static Runner getInstance() {
        if (runner == null || isAdb != AppPref.isAdbEnabled() || isRoot != AppPref.isRootEnabled()) {
            isAdb = false;
            isRoot = false;
            if (AppPref.isRootEnabled()) {
                runner = new RootShellRunner();
                isRoot = true;
            } else if (AppPref.isAdbEnabled()) {
                runner = new AdbShellRunner();
                isAdb = true;
            } else {
                runner = new UserShellRunner();
            }
        }
        return runner;
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
        if (!toyboxInJNIDir) {
            // Check if toybox is copied already
            File toybox = new File(TOYBOX);
            if (!toybox.exists()) {
                // Need to copy toybox
                RunnerUtils.copyToybox(new File(AppManager.getContext().getApplicationInfo().publicSourceDir), toybox);
            }
        }
        this.commands = new ArrayList<>();
    }

    @NonNull
    protected abstract Result run(@NonNull String command);
}
