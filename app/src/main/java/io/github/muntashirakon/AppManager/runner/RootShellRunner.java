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

import com.topjohnwu.superuser.Shell;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

class RootShellRunner extends Runner {
    @WorkerThread
    @NonNull
    @Override
    synchronized public Result runCommand() {
        Shell.Result result = Shell.su(commands.toArray(new String[0])).exec();
        clear();
        return lastResult = new Result(result.getOut(), result.getErr(), result.getCode());
    }

    @NonNull
    @Override
    protected Result run(@NonNull String command) {
        clear();
        addCommand(command);
        return runCommand();
    }
}
