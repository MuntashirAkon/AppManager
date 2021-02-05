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

package io.github.muntashirakon.AppManager.ipc;

import android.os.ParcelFileDescriptor;

import com.topjohnwu.superuser.Shell;

import java.util.List;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.IRemoteShell;
import io.github.muntashirakon.AppManager.IShellResult;

class RemoteShellImpl extends IRemoteShell.Stub {
    static {
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10));
    }

    private final Shell.Job job;

    public RemoteShellImpl(String[] cmd) {
        job = Shell.sh(cmd);
    }

    @Override
    public void addCommand(String[] commands) {
        job.add(commands);
    }

    @Override
    public void addInputStream(ParcelFileDescriptor inputStream) {
        job.add(new ParcelFileDescriptor.AutoCloseInputStream(inputStream));
    }

    @Override
    public IShellResult exec() {
        Shell.Result result = job.exec();
        return new IShellResult.Stub() {
            @Override
            public List<String> getStdout() {
                return result.getOut();
            }

            @Override
            public List<String> getStderr() {
                return result.getErr();
            }

            @Override
            public int getExitCode() {
                return result.getCode();
            }

            @Override
            public boolean isSuccessful() {
                return result.isSuccess();
            }
        };
    }
}
