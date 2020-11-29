/*
 * Copyright (C) 2020 Muntashir Al-Islam
 * Copyright (C) 2014 The Android Open Source Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package android.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.os.ParcelableException;

import java.io.IOException;
import java.util.Objects;

/**
 * Utility methods for proxying richer exceptions across Binder calls.
 */
public class ExceptionUtils {
    public static RuntimeException wrap(IOException e) {
        throw new ParcelableException(e);
    }

    public static void maybeUnwrapIOException(RuntimeException e) throws IOException {
        if (e instanceof ParcelableException) {
            ((ParcelableException) e).maybeRethrow(IOException.class);
        }
    }

    public static String getCompleteMessage(String msg, Throwable t) {
        final StringBuilder builder = new StringBuilder();
        if (msg != null) {
            builder.append(msg).append(": ");
        }
        builder.append(t.getMessage());
        while ((t = t.getCause()) != null) {
            builder.append(": ").append(t.getMessage());
        }
        return builder.toString();
    }

    public static String getCompleteMessage(Throwable t) {
        return getCompleteMessage(null, t);
    }

    public static <E extends Throwable> void propagateIfInstanceOf(
            @Nullable Throwable t, Class<E> c) throws E {
        if (t != null && c.isInstance(t)) {
            throw c.cast(t);
        }
    }

    /**
     * @param <E> a checked exception that is ok to throw without wrapping
     */
    public static <E extends Exception> RuntimeException propagate(@NonNull Throwable t, Class<E> c)
            throws E {
        propagateIfInstanceOf(t, c);
        return propagate(t);
    }

    public static RuntimeException propagate(@NonNull Throwable t) {
        Objects.requireNonNull(t);
        propagateIfInstanceOf(t, Error.class);
        propagateIfInstanceOf(t, RuntimeException.class);
        throw new RuntimeException(t);
    }

    /**
     * Gets the root {@link Throwable#getCause() cause} of {@code t}
     */
    public static @NonNull Throwable getRootCause(@NonNull Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t;
    }

    /**
     * Appends {@code cause} at the end of the causal chain of {@code t}
     *
     * @return {@code t} for convenience
     */
    public static @NonNull Throwable appendCause(@NonNull Throwable t, @Nullable Throwable cause) {
        if (cause != null) {
            getRootCause(t).initCause(cause);
        }
        return t;
    }
}