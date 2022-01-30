// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.topjohnwu.superuser.Shell;

import java.io.InputStream;

class RootShellRunner extends Runner {
    @WorkerThread
    @NonNull
    @Override
    protected synchronized Result runCommand() {
        Shell.Job shell = Shell.su(commands.toArray(new String[0]));
        for (InputStream is : inputStreams) {
            shell.add(is);
        }
        Shell.Result result = shell.exec();
        return new Result(result.getOut(), result.getErr(), result.getCode());
    }
}
