// SPDX-License-Identifier: GPL-3.0-or-later

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
