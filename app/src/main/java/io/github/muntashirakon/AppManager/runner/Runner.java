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

import android.annotation.SuppressLint;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.WorkerThread;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class Runner {
    public static final int FAILED_RET_VAL = -500;  // An impossible value

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
    public static Runner getInstance() {
        if (runner == null || (isAdb && !AppPref.isAdbEnabled()) || (!isAdb && AppPref.isAdbEnabled())) {
            if (AppPref.isRootEnabled()) {
                runner = new RootShellRunner();
                isAdb = false;
            } else if (AppPref.isAdbEnabled()) {
                runner = new AdbShellRunner();
                isAdb = true;
            } else {
                runner = new UserShellRunner();
                isAdb = false;
            }
        }
        return runner;
    }

    synchronized public static Result runCommand(String command) {
        return getInstance().run(command);
    }

    protected List<String> commands;
    public void addCommand(String command) {
        commands.add(command);
    }

    public void clear() {
        commands.clear();
    }

    @WorkerThread
    synchronized public Result runCommand() {
        return null;
    }

    protected static Result lastResult;
    public static Result getLastResult() {
        return lastResult;
    }

    protected Context context;
    protected Runner() {
        this.context = AppManager.getContext();
        this.commands = new ArrayList<>();
    }

    protected Result run(String command) {
        return null;
    }

}
