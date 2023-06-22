// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.os.ParcelFileDescriptor;

import com.topjohnwu.superuser.Shell;

import aosp.android.content.pm.StringParceledListSlice;
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

    private final Shell.Job mJob;

    public RemoteShellImpl(String[] cmd) {
        mJob = Shell.cmd(cmd);
    }

    @Override
    public void addCommand(String[] commands) {
        mJob.add(commands);
    }

    @Override
    public void addInputStream(ParcelFileDescriptor inputStream) {
        mJob.add(new ParcelFileDescriptor.AutoCloseInputStream(inputStream));
    }

    @Override
    public IShellResult exec() {
        Shell.Result result = mJob.exec();
        return new IShellResult.Stub() {
            @Override
            public StringParceledListSlice getStdout() {
                return new StringParceledListSlice(result.getOut());
            }

            @Override
            public StringParceledListSlice getStderr() {
                return new StringParceledListSlice(result.getErr());
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
