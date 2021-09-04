// SPDX-License-Identifier: GPL-3.0-or-later

package misc.utils;

import org.jetbrains.annotations.Contract;

// This isn't part of Hidden API
public class HiddenUtil {
    @Contract("_ -> _")
    @SuppressWarnings({"unused", "Contract"})
    public static <T> T throwUOE(Object... sink) {
        throw new UnsupportedOperationException();
    }
}
