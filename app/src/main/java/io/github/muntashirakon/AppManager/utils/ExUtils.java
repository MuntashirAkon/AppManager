// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

import java.io.IOException;

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
