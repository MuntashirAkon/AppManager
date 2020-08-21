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

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import io.github.muntashirakon.AppManager.adb.AdbShell;

public class AdbShellRunner extends Runner {
    @Override
    public Result runCommand() {
        try {
            AdbShell.CommandResult result = AdbShell.run(TextUtils.join("; ", commands));
            clear();
            lastResult = new Result() {
                @Override
                public boolean isSuccessful() {
                    return result.isSuccessful();
                }

                @Override
                public List<String> getOutputAsList() {
                    return result.stdout;
                }

                @Override
                public List<String> getOutputAsList(int first_index) {
                    return result.stdout.subList(first_index, result.stdout.size());
                }

                @Override
                public List<String> getOutputAsList(int first_index, int length) {
                    return result.stdout.subList(first_index, first_index + length);
                }

                @Override
                public String getOutput() {
                    return result.getStdout();
                }
            };
            return lastResult;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected Result run(String command) {
        clear();
        addCommand(command);
        return runCommand();
    }
}
