// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;

import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.IRemoteShell;
import io.github.muntashirakon.AppManager.IShellResult;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ParcelFileDescriptorUtil;

class PrivilegedShell extends Runner {
    @Override
    public boolean isRoot() {
        // ADB shell in App Manager always runs in no-root
        return false;
    }

    @WorkerThread
    @NonNull
    @Override
    protected synchronized Result runCommand() {
        try {
            IAMService amService = LocalServices.getAmService();
            IRemoteShell shell = amService.getShell(commands.toArray(new String[0]));
            for (InputStream is : inputStreams) {
                shell.addInputStream(ParcelFileDescriptorUtil.pipeFrom(is));
            }
            IShellResult result = shell.exec();
            return new Result(result.getStdout().getList(), result.getStderr().getList(), result.getExitCode());
        } catch (RemoteException | IOException e) {
            Log.e(PrivilegedShell.class.getSimpleName(), e);
            return new Result();
        }
    }
}
