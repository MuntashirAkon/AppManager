/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;

import java.io.IOException;

import io.github.muntashirakon.AppManager.backup.BackupException;

public class ExUtils {
    public static <T> T rethrowAsIOException(@NonNull Throwable e) throws IOException {
        IOException ioException = new IOException(e.getMessage());
        //noinspection UnnecessaryInitCause
        ioException.initCause(e);
        throw ioException;
    }

    public static <T> T rethrowAsBackupException(@NonNull String message, @NonNull Throwable e) throws BackupException {
        BackupException backupException = new BackupException(message);
        //noinspection UnnecessaryInitCause
        backupException.initCause(e);
        throw backupException;
    }
}
