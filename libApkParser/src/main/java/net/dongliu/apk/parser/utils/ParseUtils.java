package net.dongliu.apk.parser.utils;

import net.dongliu.apk.parser.exception.ParserException;
import net.dongliu.apk.parser.parser.StringPoolEntry;
import net.dongliu.apk.parser.struct.ResValue;
import net.dongliu.apk.parser.struct.ResourceValue;
import net.dongliu.apk.parser.struct.StringPool;
import net.dongliu.apk.parser.struct.StringPoolHeader;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import androidx.annotation.Nullable;

/**
 * @author dongliu
 */
public class ParseUtils {

    public static Charset charsetUTF8 = Charset.forName("UTF-8");

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
//        ResValue resValue = new ResValue();
        int size = Buffers.readUShort(buffer);
        short res0 = Buffers.readUByte(buffer);
        short dataType = Buffers.readUByte(buffer);

        switch (dataType) {
            case ResValue.ResType.INT_DEC:
                return ResourceValue.decimal(buffer.getInt());
            case ResValue.ResType.INT_HEX:
                return ResourceValue.hexadecimal(buffer.getInt());
            case ResValue.ResType.STRING:
                int strRef = buffer.getInt();
                if (strRef >= 0) {
                    return ResourceValue.string(strRef, stringPool);
                } else {
                    return null;
                }
            case ResValue.ResType.REFERENCE:
                return ResourceValue.reference(buffer.getInt());
            case ResValue.ResType.INT_BOOLEAN:
                return ResourceValue.bool(buffer.getInt());
            case ResValue.ResType.NULL:
                return ResourceValue.nullValue();
            case ResValue.ResType.INT_COLOR_RGB8:
            case ResValue.ResType.INT_COLOR_RGB4:
                return ResourceValue.rgb(buffer.getInt(), 6);
            case ResValue.ResType.INT_COLOR_ARGB8:
            case ResValue.ResType.INT_COLOR_ARGB4:
                return ResourceValue.rgb(buffer.getInt(), 8);
            case ResValue.ResType.DIMENSION:
                return ResourceValue.dimension(buffer.getInt());
            case ResValue.ResType.FRACTION:
                return ResourceValue.fraction(buffer.getInt());
            default:
                return ResourceValue.raw(buffer.getInt(), dataType);
        }
    }

    public static void checkChunkType(int expected, int real) {
        if (expected != real) {
            throw new ParserException("Expect chunk type:" + Integer.toHexString(expected)
                    + ", but got:" + Integer.toHexString(real));
        }
    }

}
