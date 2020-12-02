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

package io.github.muntashirakon.AppManager.servermanager.remote;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.server.common.ClassCallerProcessor;
import io.github.muntashirakon.AppManager.server.common.Shell;

public class ShellCommandHandler extends ClassCallerProcessor {
    public ShellCommandHandler(Context mPackageContext, Context mSystemContext, byte[] bytes) {
        super(mPackageContext, mSystemContext, bytes);
    }

    @NonNull
    @Override
    public Bundle proxyInvoke(@NonNull Bundle args) throws Throwable {
        String command = args.getString("command");
        String path = args.getString("path");
        args.clear();
        Shell shell = Shell.getShell(path);
        Shell.Result result = shell.exec(command);
        args.putParcelable("return", result);
        return args;
    }
}
