// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.topjohnwu.superuser.Shell;

import java.io.InputStream;

class NormalShell extends Runner {
    private final Shell shell;

    public NormalShell(boolean isRoot) {
        if (isRoot == Shell.getShell().isRoot()) {
            shell = Shell.getShell();
            return;
        }
        int flags = isRoot ? Shell.FLAG_MOUNT_MASTER : Shell.FLAG_NON_ROOT_SHELL;
        shell = Shell.Builder.create().setFlags(flags).setTimeout(10).build();
    }

    @Override
    public boolean isRoot() {
        return shell.isRoot();
    }

    @WorkerThread
    @NonNull
    @Override
    protected synchronized Result runCommand() {
        Shell.Job job = shell.newJob().add(commands.toArray(new String[0]));
        for (InputStream is : inputStreams) {
            job.add(is);
        }
        Shell.Result result = job.exec();
        return new Result(result.getOut(), result.getErr(), result.getCode());
    }
}
