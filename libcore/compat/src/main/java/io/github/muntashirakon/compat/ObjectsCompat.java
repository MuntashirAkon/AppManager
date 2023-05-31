// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

public class ObjectsCompat {
    @NonNull
    public static <T> T requireNonNullElse(@Nullable T obj, @NonNull T defaultObj) {
        return (obj != null) ? obj : Objects.requireNonNull(defaultObj, "defaultObj");
    }
}
