// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.topjohnwu.superuser.Shell;

import java.io.InputStream;

class UserShellRunner extends Runner {
    @NonNull
    @WorkerThread
    @Override
    protected synchronized Result runCommand() {
        Shell.Job shell = Shell.sh(commands.toArray(new String[0]));
        for (InputStream is : inputStreams) {
            shell.add(is);
        }
        Shell.Result result = shell.exec();
        return new Result(result.getOut(), result.getErr(), result.getCode());
    }
}
