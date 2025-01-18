// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.ipc.RemoteProcess;
import io.github.muntashirakon.AppManager.ipc.RemoteProcessImpl;

public final class ProcessCompat {
    /**
     * Defines the start of a range of UIDs (and GIDs), going from this
     * number to {@link #LAST_APPLICATION_UID} that are reserved for assigning
     * to applications.
     */
    public static final int FIRST_APPLICATION_UID = android.os.Process.FIRST_APPLICATION_UID;

    /**
     * Last of application-specific UIDs starting at
     * {@link #FIRST_APPLICATION_UID}.
     */
    public static final int LAST_APPLICATION_UID = android.os.Process.LAST_APPLICATION_UID;

    /**
     * First uid used for fully isolated sandboxed processes spawned from an app zygote
     */
    public static final int FIRST_APP_ZYGOTE_ISOLATED_UID = 90000;

    /**
     * Last uid used for fully isolated sandboxed processes spawned from an app zygote
     */
    public static final int LAST_APP_ZYGOTE_ISOLATED_UID = 98999;

    /**
     * First uid used for fully isolated sandboxed processes (with no permissions of their own)
     */
    public static final int FIRST_ISOLATED_UID = 99000;

    /**
     * Last uid used for fully isolated sandboxed processes (with no permissions of their own)
     */
    public static final int LAST_ISOLATED_UID = 99999;

    public static Process exec(@Nullable String[] cmd, @Nullable String[] env, @Nullable File dir) throws IOException {
        if (LocalServices.alive()) {
            try {
                return new RemoteProcess(LocalServices.getAmService().newProcess(cmd, env, dir == null ? null :
                        dir.getAbsolutePath()));
            } catch (RemoteException e) {
                throw new IOException(e);
            }
        }
        return new RemoteProcess(new RemoteProcessImpl(Runtime.getRuntime().exec(cmd, env, dir)));
    }

    public static Process exec(String[] cmd, String[] env) throws IOException {
        return exec(cmd, env, null);
    }

    public static Process exec(String[] cmd) throws IOException {
        return exec(cmd, null, null);
    }

    public static boolean isAlive(@NonNull Process process) {
        try {
            process.exitValue();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}
