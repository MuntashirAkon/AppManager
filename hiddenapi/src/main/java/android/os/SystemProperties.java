// SPDX-License-Identifier: Apache-2.0

package android.os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import misc.utils.HiddenUtil;

public class SystemProperties {
    /**
     * Get the String value for the given {@code key}.
     *
     * @param key the key to lookup
     * @return an empty string if the {@code key} isn't found
     */
    public static String get(@NonNull String key) {
        return HiddenUtil.throwUOE(key);
    }

    /**
     * Get the String value for the given {@code key}.
     *
     * @param key the key to lookup
     * @param def the default value in case the property is not set or empty
     * @return if the {@code key} isn't found, return {@code def} if it isn't null, or an empty
     * string otherwise
     */
    @NonNull
    public static String get(@NonNull String key, @Nullable String def) {
        return HiddenUtil.throwUOE(key, def);
    }

    /**
     * Get the value for the given key, and return as an integer.
     *
     * @param key the key to lookup
     * @param def a default value to return
     * @return the key parsed as an integer, or def if the key isn't found or
     * cannot be parsed
     * @throws IllegalArgumentException if the key exceeds 32 characters
     */
    public static int getInt(String key, int def) {
        return HiddenUtil.throwUOE(key, def);
    }

    /**
     * Get the value for the given key, and return as a long.
     *
     * @param key the key to lookup
     * @param def a default value to return
     * @return the key parsed as a long, or def if the key isn't found or
     * cannot be parsed
     * @throws IllegalArgumentException if the key exceeds 32 characters
     */
    public static long getLong(String key, long def) {
        return HiddenUtil.throwUOE(key, def);
    }

    /**
     * Get the value for the given key, returned as a boolean.
     * Values 'n', 'no', '0', 'false' or 'off' are considered false.
     * Values 'y', 'yes', '1', 'true' or 'on' are considered true.
     * (case sensitive).
     * If the key does not exist, or has any other value, then the default
     * result is returned.
     *
     * @param key the key to lookup
     * @param def a default value to return
     * @return the key parsed as a boolean, or def if the key isn't found or is
     * not able to be parsed as a boolean.
     * @throws IllegalArgumentException if the key exceeds 32 characters
     */
    public static boolean getBoolean(String key, boolean def) {
        return HiddenUtil.throwUOE(key, def);
    }
}