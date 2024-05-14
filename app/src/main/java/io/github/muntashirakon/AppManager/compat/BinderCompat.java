// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.os.Build;
import android.os.IBinder;
import android.os.IBinderHidden;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.FileDescriptor;

import dev.rikka.tools.refine.Refine;

public final class BinderCompat {

    /**
     * Execute a shell command on this object.  This may be performed asynchrously from the caller;
     * the implementation must always call resultReceiver when finished.
     *
     * @param in             The raw file descriptor that an input data stream can be read from.
     * @param out            The raw file descriptor that normal command messages should be written to.
     * @param err            The raw file descriptor that command error messages should be written to.
     * @param args           Command-line arguments.
     * @param shellCallback  Optional callback to the caller's shell to perform operations in it.
     * @param resultReceiver Called when the command has finished executing, with the result code.
     */
    @SuppressWarnings("deprecation")
    @RequiresApi(Build.VERSION_CODES.N)
    public static void shellCommand(@NonNull IBinder binder,
                                    @NonNull FileDescriptor in, @NonNull FileDescriptor out,
                                    @NonNull FileDescriptor err,
                                    @NonNull String[] args, @Nullable ShellCallback shellCallback,
                                    @NonNull ResultReceiver resultReceiver) throws RemoteException {
        IBinderHidden binderHidden = Refine.unsafeCast(binder);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binderHidden.shellCommand(in, out, err, args, shellCallback, resultReceiver);
        } else binderHidden.shellCommand(in, out, err, args, resultReceiver);
    }
}
