// SPDX-License-Identifier: BSD-2-Clause AND GPL-3.0-or-later

package net.dongliu.apk.parser.utils;

import android.util.Log;
import android.util.TypedValue;

import androidx.annotation.Nullable;

import net.dongliu.apk.parser.parser.StringPoolEntry;
import net.dongliu.apk.parser.struct.ResourceValue;
import net.dongliu.apk.parser.struct.StringPool;
import net.dongliu.apk.parser.struct.StringPoolHeader;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

// Copyright 2018 Liu Dong
public class ParseUtils {
    public static final String TAG = ParseUtils.class.getSimpleName();

    public static Charset charsetUTF8 = StandardCharsets.UTF_8;

    /**
     * read string from input buffer. if get EOF before read enough data, throw IOException.
     */
    public static String readString(ByteBuffer buffer, boolean utf8) {
        if (utf8) {
            //  The lengths are encoded in the same way as for the 16-bit format
            // but using 8-bit rather than 16-bit integers.
            int strLen = readLen(buffer);
            int bytesLen = readLen(buffer);
            byte[] bytes = Buffers.readBytes(buffer, bytesLen);
            String str = new String(bytes, charsetUTF8);
            // zero
            int trailling = Buffers.readUByte(buffer);
            return str;
        } else {
            // The length is encoded as either one or two 16-bit integers as per the commentRef...
            int strLen = readLen16(buffer);
            String str = Buffers.readString(buffer, strLen);
            // zero
            int trailling = Buffers.readUShort(buffer);
            return str;
        }
    }

    /**
     * read utf-16 encoding str, use zero char to end str.
     */
    public static String readStringUTF16(ByteBuffer buffer, int strLen) {
        String str = Buffers.readString(buffer, strLen);
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == 0) {
                return str.substring(0, i);
            }
        }
        return str;
    }

    /**
     * read encoding len.
     * see StringPool.cpp ENCODE_LENGTH
     */
    private static int readLen(ByteBuffer buffer) {
        int len = 0;
        int i = Buffers.readUByte(buffer);
        if ((i & 0x80) != 0) {
            //read one more byte.
            len |= (i & 0x7f) << 8;
            len += Buffers.readUByte(buffer);
        } else {
            len = i;
        }
        return len;
    }

    /**
     * read encoding len.
     * see Stringpool.cpp ENCODE_LENGTH
     */
    private static int readLen16(ByteBuffer buffer) {
        int len = 0;
        int i = Buffers.readUShort(buffer);
        if ((i & 0x8000) != 0) {
            len |= (i & 0x7fff) << 16;
            len += Buffers.readUShort(buffer);
        } else {
            len = i;
        }
        return len;
    }


    /**
     * read String pool, for apk binary xml file and resource table.
     */
    public static StringPool readStringPool(ByteBuffer buffer, StringPoolHeader stringPoolHeader) {

        long beginPos = buffer.position();
        int[] offsets = new int[stringPoolHeader.getStringCount()];
        // read strings offset
        if (stringPoolHeader.getStringCount() > 0) {
            for (int idx = 0; idx < stringPoolHeader.getStringCount(); idx++) {
                offsets[idx] = Unsigned.toUInt(Buffers.readUInt(buffer));
            }
        }
        // read flag
        // the string index is sorted by the string values if true
        boolean sorted = (stringPoolHeader.getFlags() & StringPoolHeader.SORTED_FLAG) != 0;
        // string use utf-8 format if true, otherwise utf-16
        boolean utf8 = (stringPoolHeader.getFlags() & StringPoolHeader.UTF8_FLAG) != 0;

        // read strings. the head and metas have 28 bytes
        long stringPos = beginPos + stringPoolHeader.getStringsStart() - stringPoolHeader.getHeaderSize();
        Buffers.position(buffer, stringPos);

        StringPoolEntry[] entries = new StringPoolEntry[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            entries[i] = new StringPoolEntry(i, stringPos + Unsigned.toLong(offsets[i]));
        }

        String lastStr = null;
        long lastOffset = -1;
        StringPool stringPool = new StringPool(stringPoolHeader.getStringCount());
        for (StringPoolEntry entry : entries) {
            if (entry.getOffset() == lastOffset) {
                stringPool.set(entry.getIdx(), lastStr);
                continue;
            }

            Buffers.position(buffer, entry.getOffset());
            lastOffset = entry.getOffset();
            String str = ParseUtils.readString(buffer, utf8);
            lastStr = str;
            stringPool.set(entry.getIdx(), str);
        }

        // read styles
        if (stringPoolHeader.getStyleCount() > 0) {
            // now we just skip it
        }

        Buffers.position(buffer, beginPos + stringPoolHeader.getBodySize());

        return stringPool;
    }

    /**
     * read res value, convert from different types to string.
     */
    @Nullable
    public static ResourceValue readResValue(ByteBuffer buffer, StringPool stringPool) {
        int size = Buffers.readUShort(buffer);
        short res0 = Buffers.readUByte(buffer);
        short dataType = Buffers.readUByte(buffer);

        switch (dataType) {
            case TypedValue.TYPE_NULL:
                return ResourceValue.nullValue();
            case TypedValue.TYPE_STRING:
                int strRef = buffer.getInt();
                if (strRef >= 0) {
                    return new ResourceValue(dataType, stringPool.get(strRef));
                } else {
                    Log.w(TAG, "Invalid string index = " + strRef);
                    return null;
                }
            case TypedValue.TYPE_REFERENCE:
            case TypedValue.TYPE_ATTRIBUTE:
            case TypedValue.TYPE_FLOAT:
            case TypedValue.TYPE_DIMENSION:
            case TypedValue.TYPE_FRACTION:
            case TypedValue.TYPE_INT_DEC:
            case TypedValue.TYPE_INT_HEX:
            case TypedValue.TYPE_INT_BOOLEAN:
            case TypedValue.TYPE_INT_COLOR_RGB8:
            case TypedValue.TYPE_INT_COLOR_RGB4:
            case TypedValue.TYPE_INT_COLOR_ARGB8:
            case TypedValue.TYPE_INT_COLOR_ARGB4:
                return new ResourceValue(dataType, buffer.getInt());
            default:
                Log.w(TAG, String.format("Invalid resource, type 0x%x and data 0x%x", dataType, buffer.getInt()));
                return null;
        }
    }

}
