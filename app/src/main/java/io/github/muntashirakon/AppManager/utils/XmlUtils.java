// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;

public class XmlUtils {
    public static boolean isAbx(@NonNull InputStream is) throws IOException {
        if (!is.markSupported()) throw new IOException("InputStream must support mark.");
        int header;
        byte[] headerBytes = new byte[4];
        is.mark(4);
        is.read(headerBytes);
        is.reset();
        header = new BigInteger(headerBytes).intValue();
        return header == 0x41425800; // BinaryXmlSerializer.PROTOCOL_MAGIC_VERSION_0 = ABX\0
    }
}
