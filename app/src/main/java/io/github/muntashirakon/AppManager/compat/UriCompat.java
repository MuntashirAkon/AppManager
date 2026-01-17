// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.net.Uri;

import androidx.annotation.Nullable;

public final class UriCompat {
    /**
     * Index of a component which was not found.
     */
    private final static int NOT_FOUND = -1;

    /**
     * Encodes a value it wasn't already encoded.
     *
     * @param value string to encode
     * @param allow characters to allow
     * @return encoded value
     */
    @Nullable
    public static String encodeIfNotEncoded(@Nullable String value, @Nullable String allow) {
        if (value == null) return null;
        if (isEncoded(value, allow)) return value;
        return Uri.encode(value, allow);
    }

    /**
     * Returns true if the given string is already encoded to safe characters.
     *
     * @param value string to check
     * @param allow characters to allow
     * @return true if the string is already encoded or false if it should be encoded
     */
    private static boolean isEncoded(@Nullable String value, @Nullable String allow) {
        if (value == null) return true;
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);

            // Allow % because that's the prefix for an encoded character. This method will fail
            // for decoded strings whose onlyinvalid character is %, but it's assumed that % alone
            // cannot cause malicious behavior in the framework.
            if (!isAllowed(c, allow) && c != '%') {
                return false;
            }
        }
        return true;
    }


    /**
     * Returns true if the given character is allowed.
     *
     * @param c     character to check
     * @param allow characters to allow
     * @return true if the character is allowed or false if it should be
     * encoded
     */
    private static boolean isAllowed(char c, @Nullable String allow) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || "_-!.~'()*".indexOf(c) != NOT_FOUND
                || (allow != null && allow.indexOf(c) != NOT_FOUND);
    }
}
