/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;

import java.io.IOException;

public class ExUtils {
    public static <T> T rethrowAsIOException(@NonNull Throwable e) throws IOException {
        IOException ioException = new IOException(e.getMessage());
        //noinspection UnnecessaryInitCause
        ioException.initCause(e);
        throw ioException;
    }
}
