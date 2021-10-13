// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

public class IntegerUtils {
    public static int getUInt8(@NonNull ByteBuffer buffer) {
        return buffer.get() & 0xff;
    }

    public static int getUInt16(@NonNull ByteBuffer buffer) {
        return buffer.getShort() & 0xffff;
    }

    public static long getUInt32(@NonNull ByteBuffer buffer) {
        return buffer.getInt() & 0xffffffffL;
    }

    public static long getUInt32(@NonNull ByteBuffer buffer, int position) {
        return buffer.getInt(position) & 0xffffffffL;
    }
}
