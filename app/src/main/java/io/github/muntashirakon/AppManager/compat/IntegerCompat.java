// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import androidx.annotation.NonNull;

public class IntegerCompat {
    /**
     * Return a 0x prefixed signed hex.
     */
    @NonNull
    public static String toSignedHex(int signedInt) {
        StringBuilder sb = new StringBuilder();
        sb.append(Integer.toString(signedInt, 16));
        sb.insert(sb.charAt(0) == '-' ? 1 : 0, "0x");
        return sb.toString();
    }

    /**
     * Return a 0x prefixed unsigned hex.
     */
    @NonNull
    public static String toUnsignedHex(int signedInt) {
        return "0x" + Integer.toHexString(signedInt);
    }

    /**
     * Same as {@link Integer#decode(String)} except it allows decoding both signed and unsigned values
     */
    public static int decode(@NonNull String nm) throws NumberFormatException {
        int radix = 10;
        int index = 0;

        if (nm.length() == 0) {
            throw new NumberFormatException("Zero length string");
        }
        char firstChar = nm.charAt(0);
        // Handle sign, if present
        if (firstChar == '-') {
            // First character is a signed character, use regular decoding
            return Integer.decode(nm);
        } else if (firstChar == '+') {
            index++;
        }

        // Handle radix specifier, if present
        if (nm.startsWith("0x", index) || nm.startsWith("0X", index)) {
            index += 2;
            radix = 16;
        } else if (nm.startsWith("#", index)) {
            index++;
            radix = 16;
        } else if (nm.startsWith("0", index) && nm.length() > 1 + index) {
            index++;
            radix = 8;
        }

        if (nm.startsWith("-", index) || nm.startsWith("+", index)) {
            throw new NumberFormatException("Sign character in wrong position");
        }

        return Integer.parseUnsignedInt(nm.substring(index), radix);
    }
}
