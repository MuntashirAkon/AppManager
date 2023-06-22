// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.topjohnwu.superuser.Shell;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

class NormalShell extends Runner {
    private final Shell mShell;

    public NormalShell(boolean isRoot) {
        if (isRoot == Shell.getShell().isRoot()) {
            mShell = Shell.getShell();
            return;
        }
        int flags = isRoot ? Shell.FLAG_MOUNT_MASTER : Shell.FLAG_NON_ROOT_SHELL;
        mShell = Shell.Builder.create().setFlags(flags).setTimeout(10).build();
    }

    @Override
    public boolean isRoot() {
        return mShell.isRoot();
    }

    @WorkerThread
    @NonNull
    @Override
    protected synchronized Result runCommand() {
        List<String> stdout = new ArrayList<>();
        List<String> stderr = new ArrayList<>();
        Shell.Job job = mShell.newJob().add(commands.toArray(new String[0])).to(stdout, stderr);
        for (InputStream is : inputStreams) {
            job.add(is);
        }
        Shell.Result result = job.exec();
        return new Result(stdout, stderr, result.getCode());
    }
}
