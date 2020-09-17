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

import android.text.TextUtils;
import android.util.Log;

import com.topjohnwu.superuser.Shell;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

public class UserShellRunner extends Runner {
    private static UserShellRunner rootShellRunner;

    public static UserShellRunner getInstance() {
        if (rootShellRunner == null) rootShellRunner = new UserShellRunner();
        return rootShellRunner;
    }

    @NonNull
    public static Result runCommand(@NonNull String command) {
        return getInstance().run(command);
    }

    @NonNull
    @WorkerThread
    @Override
    synchronized public Result runCommand() {
        Shell.Result result = Shell.sh(commands.toArray(new String[0])).exec();
        List<String> stdout = result.getOut();
        List<String> stderr = result.getErr();
        if (stderr.size() > 0) Log.e("UserShellRunner", TextUtils.join("\n", stderr));
        clear();
        return lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return result.isSuccess();
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
    }

    @NonNull
    @Override
    protected Result run(@NonNull String command) {
        clear();
        addCommand(command);
        return runCommand();
    }
}
