// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.net.Uri;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

import io.github.muntashirakon.AppManager.AppManager;

public final class Paths {
    public static final String TAG = Paths.class.getSimpleName();

    @NonNull
    public static Path getUnprivileged(@NonNull File pathName) {
        Path path = null;
        try {
            path = new Path(AppManager.getContext(), pathName.getAbsolutePath(), false);
        } catch (RemoteException ignore) {
            // This exception is never called in unprivileged mode.
        }
        // Path is never null because RE is never called.
        return Objects.requireNonNull(path);
    }

    @NonNull
    public static Path getUnprivileged(@NonNull String pathName) {
        Path path = null;
        try {
            path = new Path(AppManager.getContext(), pathName, false);
        } catch (RemoteException ignore) {
            // This exception is never called in unprivileged mode.
        }
        // Path is never null because RE is never called.
        return Objects.requireNonNull(path);
    }

    @NonNull
    public static Path get(String pathName) {
        return new Path(AppManager.getContext(), pathName);
    }

    @NonNull
    public static Path get(File pathName) {
        return new Path(AppManager.getContext(), pathName);
    }

    @NonNull
    public static Path get(Uri pathUri) {
        return new Path(AppManager.getContext(), pathUri);
    }

    @NonNull
    public static Path[] build(@NonNull Path[] base, String... segments) {
        Path[] result = new Path[base.length];
        for (int i = 0; i < base.length; i++) {
            result[i] = build(base[i], segments);
        }
        return result;
    }

    @Nullable
    public static Path build(@NonNull File base, @NonNull String ...segments) {
        return build(get(base), segments);
    }

    @Nullable
    public static Path build(@NonNull Path base, @NonNull String ...segments) {
        Path cur = base;
        boolean isLfs = cur.getFile() != null;
        try {
            for (String segment : segments) {
                if (isLfs) {
                    cur = get(new File(cur.getFilePath(), segment));
                } else {
                    cur = cur.findFile(segment);
                }
            }
            return cur;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static long size(@Nullable Path root) {
        if (root == null) {
            return 0;
        }
        if (root.isFile()) {
            return root.length();
        }
        try {
            if (root.isSymbolicLink()) {
                return 0;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
        long length = 0;
        Path[] files = root.listFiles();
        for (Path file : files) {
            length += size(file);
        }
        return length;
    }

    public static void chmod(@NonNull Path path, int mode) throws ErrnoException {
        ExtendedFile file = path.getFile();
        if (file == null) {
            throw new ErrnoException("Supplied path is not a Linux path.", OsConstants.EBADF);
        }
        file.setMode(mode);
    }

    public static void chown(@NonNull Path path, int uid, int gid) throws ErrnoException {
        ExtendedFile file = path.getFile();
        if (file == null) {
            throw new ErrnoException("Supplied path is not a Linux path.", OsConstants.EBADF);
        }
        file.setUidGid(uid, gid);
    }

    /**
     * Set owner and mode of given path.
     *
     * @param mode to apply through {@code chmod}
     * @param uid  to apply through {@code chown}, or -1 to leave unchanged
     * @param gid  to apply through {@code chown}, or -1 to leave unchanged
     */
    public static void setPermissions(@NonNull Path path, int mode, int uid, int gid) throws ErrnoException {
        chmod(path, mode);
        if (uid >= 0 || gid >= 0) {
            chown(path, uid, gid);
        }
    }
}
