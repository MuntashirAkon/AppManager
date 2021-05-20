// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.os.RemoteException;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

import io.github.muntashirakon.AppManager.ipc.IPCUtils;
import io.github.muntashirakon.AppManager.ipc.RemoteProcess;

public class ProcessCompat {
    public static Process exec(@Nullable String[] cmd, @Nullable String[] env, @Nullable File dir) throws IOException {
        if (LocalServer.isAMServiceAlive()) {
            try {
                return new RemoteProcess(IPCUtils.getAmService().newProcess(cmd, env, dir == null ? null :
                        dir.getAbsolutePath()));
            } catch (RemoteException e) {
                throw new IOException(e);
            }
        }
        return Runtime.getRuntime().exec(cmd, env, dir);
    }

    public static Process exec(String[] cmd, String[] env) throws IOException {
        return exec(cmd, env, null);
    }

    public static Process exec(String[] cmd) throws IOException {
        return exec(cmd, null, null);
    }
}
