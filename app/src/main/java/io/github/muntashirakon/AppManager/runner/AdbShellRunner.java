// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import android.os.RemoteException;

import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.IRemoteShell;
import io.github.muntashirakon.AppManager.IShellResult;
import io.github.muntashirakon.AppManager.ipc.IPCUtils;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.utils.ParcelFileDescriptorUtil;

class AdbShellRunner extends Runner {
    @WorkerThread
    @NonNull
    @Override
    synchronized public Result runCommand() {
        try {
            IAMService amService = IPCUtils.getServiceSafe();
            IRemoteShell shell = amService.getShell(commands.toArray(new String[0]));
            for (InputStream is : inputStreams) {
                shell.addInputStream(ParcelFileDescriptorUtil.pipeFrom(is));
            }
            IShellResult result = shell.exec();
            clear();
            return new Result(result.getStdout(), result.getStderr(), result.getExitCode());
        } catch (RemoteException | IOException e) {
            Log.e("AdbShellRunner", e);
            return new Result();
        }
    }
}
