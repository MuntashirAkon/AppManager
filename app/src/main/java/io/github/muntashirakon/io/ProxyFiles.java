/*
 * Copyright (c) 2021 Muntashir Al-Islam
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

package io.github.muntashirakon.io;

import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;

import io.github.muntashirakon.AppManager.ipc.IPCUtils;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;

@WorkerThread
public final class ProxyFiles {
    @NonNull
    public static FileStatus stat(@NonNull File path) throws ErrnoException, RemoteException {
        if (path instanceof ProxyFile && LocalServer.isAMServiceAlive()) {
            return IPCUtils.getAmService().stat(path.getAbsolutePath());
        }
        return new FileStatus(Os.stat(path.getAbsolutePath()));
    }

    public static void chmod(@NonNull File path, int mode) throws ErrnoException, RemoteException {
        if (path instanceof ProxyFile && LocalServer.isAMServiceAlive()) {
            IPCUtils.getAmService().chmod(path.getAbsolutePath(), mode);
        } else Os.chmod(path.getAbsolutePath(), mode);
    }

    public static void chown(@NonNull File path, int uid, int gid) throws ErrnoException, RemoteException {
        if (path instanceof ProxyFile && LocalServer.isAMServiceAlive()) {
            IPCUtils.getAmService().chown(path.getAbsolutePath(), uid, gid);
        } else Os.chown(path.getAbsolutePath(), uid, gid);
    }
}
