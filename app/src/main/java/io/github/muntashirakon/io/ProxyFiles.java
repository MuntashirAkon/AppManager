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
    public static final String TAG = ProxyFiles.class.getSimpleName();

    @NonNull
    public static FileStatus stat(@NonNull File path) throws ErrnoException, RemoteException {
        if (path instanceof ProxyFile && LocalServer.isAMServiceAlive()) {
            return IPCUtils.getAmService().stat(path.getAbsolutePath());
        }
        return new FileStatus(Os.stat(path.getAbsolutePath()));
    }

    @NonNull
    public static FileStatus lstat(@NonNull File path) throws ErrnoException, RemoteException {
        if (path instanceof ProxyFile && LocalServer.isAMServiceAlive()) {
            return IPCUtils.getAmService().lstat(path.getAbsolutePath());
        }
        return new FileStatus(Os.lstat(path.getAbsolutePath()));
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

    /**
     * Set owner and mode of of given path.
     *
     * @param mode to apply through {@code chmod}
     * @param uid  to apply through {@code chown}, or -1 to leave unchanged
     * @param gid  to apply through {@code chown}, or -1 to leave unchanged
     */
    public static void setPermissions(File path, int mode, int uid, int gid) throws ErrnoException, RemoteException {
        chmod(path, mode);
        if (uid >= 0 || gid >= 0) {
            chown(path, uid, gid);
        }
    }

    /**
     * Copy the owner UID, owner GID, and mode bits from one file to another.
     *
     * @param from File where attributes should be copied from.
     * @param to   File where attributes should be copied to.
     */
    public static void copyPermissions(@NonNull File from, @NonNull File to) throws ErrnoException, RemoteException {
        final FileStatus stat = stat(from);
        chmod(to, stat.st_mode);
        chown(to, stat.st_uid, stat.st_gid);
    }
}
