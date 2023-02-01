// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.system.ErrnoException;
import android.system.StructPasswd;

import androidx.annotation.Keep;

@Keep
public class OsCompat {
    // Lists the syscalls unavailable in Os

    static {
        System.loadLibrary("am");
    }

    public static native void setgrent() throws ErrnoException;

    public static native void setpwent() throws ErrnoException;

    public static native StructGroup getgrent() throws ErrnoException;

    public static native StructPasswd getpwent() throws ErrnoException;

    public static native void endgrent() throws ErrnoException;

    public static native void endpwent() throws ErrnoException;
}
