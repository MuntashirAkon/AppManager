// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.os.Build;
import android.os.DeadObjectException;
import android.os.DeadSystemException;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

import java.io.IOException;
import java.util.Optional;

import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.compat.ObjectsCompat;

public class ExUtils {
    public interface ThrowingRunnable<T> {
        T run() throws Throwable;
    }

    public interface ThrowingRunnableNoReturn {
        void run() throws Throwable;
    }

    @Contract("_ -> fail")
    public static <T> T rethrowAsIOException(@NonNull Throwable e) throws IOException {
        IOException ioException = new IOException(e.getMessage());
        //noinspection UnnecessaryInitCause
        ioException.initCause(e);
        throw ioException;
    }

    @Contract("_, _ -> fail")
    public static <T> T rethrowAsBackupException(@NonNull String message, @NonNull Throwable e) throws BackupException {
        BackupException backupException = new BackupException(message);
        //noinspection UnnecessaryInitCause
        backupException.initCause(e);
        throw backupException;
    }

    @Contract("_ -> fail")
    public static  <T> T rethrowAsRuntimeException(@NonNull Throwable th) {
        throw new RuntimeException(th);
    }

    /**
     * Rethrow this exception when we know it came from the system server. This
     * gives us an opportunity to throw a nice clean
     * {@link DeadSystemException} signal to avoid spamming logs with
     * misleading stack traces.
     * <p>
     * Apps making calls into the system server may end up persisting internal
     * state or making security decisions based on the perceived success or
     * failure of a call, or any default values returned. For this reason, we
     * want to strongly throw when there was trouble with the transaction.
     */
    @Contract("_ -> fail")
    public static  <T> T rethrowFromSystemServer(@NonNull RemoteException e) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            throw e.rethrowFromSystemServer();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && e instanceof DeadObjectException) {
                throw new RuntimeException(new DeadSystemException());
        } else {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    public static <T> T exceptionAsNull(ThrowingRunnable<T> r) {
        try {
            return r.run();
        } catch (Throwable th) {
            Log.w("ExUtils", "(Suppressed error)", th);
            return null;
        }
    }

    @NonNull
    public static <T> T requireNonNullElse(ThrowingRunnable<T> r, T defaultObj) {
        return ObjectsCompat.requireNonNullElse(exceptionAsNull(r), defaultObj);
    }

    @Nullable
    public static <T> T asRuntimeException(ThrowingRunnable<T> r) {
        try {
            return r.run();
        } catch (Throwable th) {
            throw new RuntimeException(th);
        }
    }

    public static void exceptionAsIgnored(ThrowingRunnableNoReturn r) {
        try {
            r.run();
        } catch (Throwable th) {
            Log.w("ExUtils", "(Suppressed error)", th);
        }
    }
}
