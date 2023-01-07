// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat;

import java.util.Objects;

public class ObjectsCompat {
    public static <T> T requireNonNullElse(T obj, T defaultObj) {
        return (obj != null) ? obj : Objects.requireNonNull(defaultObj, "defaultObj");
    }
}
