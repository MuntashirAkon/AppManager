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
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import eu.chainfire.libsuperuser.Shell;

public class RootShellRunner extends Runner {
    @SuppressLint("StaticFieldLeak")
    private static RootShellRunner rootShellRunner;
    public static RootShellRunner getInstance() {
        if (rootShellRunner == null) rootShellRunner = new RootShellRunner();
        return rootShellRunner;
    }

    public static Result runCommand(String command) {
        return getInstance().run(command);
    }

    @Override
    public Result runCommand() {
        List<String> stdout = Collections.synchronizedList(new ArrayList<>());
        List<String> stderr = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger retVal = new AtomicInteger(FAILED_RET_VAL);
        try {
            retVal.set(Shell.Pool.SU.run(TextUtils.join("; ", commands), stdout, stderr, true));
            if (stderr.size() > 0) Log.e("RootShellRunner", TextUtils.join("\n", stderr));
        } catch (Shell.ShellDiedException e) {
            e.printStackTrace();
        }
        clear();
        lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return retVal.get() == 0;
            }

            @Override
            public List<String> getOutputAsList() {
                return stdout;
            }

            @Override
            public List<String> getOutputAsList(int first_index) {
                return stdout.subList(first_index, stdout.size());
            }

            @Override
            public List<String> getOutputAsList(int first_index, int length) {
                return stdout.subList(first_index, first_index + length);
            }

            @Override
            public String getOutput() {
                return TextUtils.join("\n", stdout);
            }
        };
        return lastResult;
    }

    @Override
    protected Result run(String command) {
        clear();
        addCommand(command);
        return runCommand();
    }
}
