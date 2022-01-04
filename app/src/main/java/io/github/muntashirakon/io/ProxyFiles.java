// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;

import io.github.muntashirakon.AppManager.ipc.IPCUtils;

@WorkerThread
public final class ProxyFiles {
    public static final String TAG = ProxyFiles.class.getSimpleName();

    @NonNull
    public static FileStatus stat(@NonNull File path) throws ErrnoException, RemoteException {
        if (path instanceof ProxyFile && ((ProxyFile) path).isRemote()) {
            return IPCUtils.getAmService().stat(path.getAbsolutePath());
        }
        return new FileStatus(Os.stat(path.getAbsolutePath()));
    }

    @NonNull
    public static FileStatus lstat(@NonNull File path) throws ErrnoException, RemoteException {
        if (path instanceof ProxyFile && ((ProxyFile) path).isRemote()) {
            return IPCUtils.getAmService().lstat(path.getAbsolutePath());
        }
        return new FileStatus(Os.lstat(path.getAbsolutePath()));
    }

    public static void chmod(@NonNull File path, int mode) throws ErrnoException, RemoteException {
        if (path instanceof ProxyFile && ((ProxyFile) path).isRemote()) {
            IPCUtils.getAmService().chmod(path.getAbsolutePath(), mode);
        } else Os.chmod(path.getAbsolutePath(), mode);
    }

    public static void chown(@NonNull File path, int uid, int gid) throws ErrnoException, RemoteException {
        if (path instanceof ProxyFile && ((ProxyFile) path).isRemote()) {
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
    public static void setPermissions(@NonNull Path path, int mode, int uid, int gid)
            throws ErrnoException, RemoteException {
        File filePath = path.getFile();
        if (filePath == null) return;
        chmod(filePath, mode);
        if (uid >= 0 || gid >= 0) {
            chown(filePath, uid, gid);
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
