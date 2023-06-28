// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat.system;

import android.system.ErrnoException;
import android.system.StructPasswd;

import androidx.annotation.Keep;

@Keep
public class OsCompat {
    // Lists the syscalls unavailable in Os

    static {
        System.loadLibrary("am");
    }

    public static long UTIME_NOW;
    public static long UTIME_OMIT;
    public static int AT_FDCWD;
    public static int AT_SYMLINK_NOFOLLOW;

    static {
        setNativeConstants();
    }

    private static native void setNativeConstants();

    public static native void setgrent() throws ErrnoException;

    public static native void setpwent() throws ErrnoException;

    public static native StructGroup getgrent() throws ErrnoException;

    public static native StructPasswd getpwent() throws ErrnoException;

    public static native void endgrent() throws ErrnoException;

    public static native void endpwent() throws ErrnoException;

    public static native void utimensat(int dirfd, String pathname, StructTimespec atime, StructTimespec mtime,
                                        int flags) throws ErrnoException;
}
