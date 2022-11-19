// SPDX-License-Identifier: MIT

package org.slf4j;

public final class LoggerFactory {
    public static Logger getLogger(String name) {
        return new LoggerImpl(name);
    }

    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }
}
