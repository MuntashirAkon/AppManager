package net.dongliu.apk.parser.utils;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * utils method for byte buffer
 *
 * @author Liu Dong dongliu@live.cn
 */
public class Buffers {

    /**
     * get one unsigned byte as short type
     */
    public static short readUByte(ByteBuffer buffer) {
        byte b = buffer.get();
        return (short) (b & 0xff);
    }

    /**
     * get one unsigned short as int type
     */
    public static int readUShort(ByteBuffer buffer) {
        short s = buffer.getShort();
        return s & 0xffff;
    }

    /**
     * get one unsigned int as long type
     */
    public static long readUInt(ByteBuffer buffer) {
        int i = buffer.getInt();
        return i & 0xffffffffL;
    }

    /**
     * get bytes
     */
    public static byte[] readBytes(ByteBuffer buffer, int size) {
        byte[] bytes = new byte[size];
        buffer.get(bytes);
        return bytes;
    }

    /**
     * get all bytes remains
     */
    public static byte[] readBytes(ByteBuffer buffer) {
        return readBytes(buffer, buffer.remaining());
    }

    /**
     * Read ascii string ,by len
     */
    public static String readAsciiString(ByteBuffer buffer, int strLen) {
        byte[] bytes = new byte[strLen];
        buffer.get(bytes);
        return new String(bytes);
    }

    /**
     * read utf16 strings, use strLen, not ending 0 char.
     */
    public static String readString(ByteBuffer buffer, int strLen) {
        StringBuilder sb = new StringBuilder(strLen);
        for (int i = 0; i < strLen; i++) {
            sb.append(buffer.getChar());
        }
        return sb.toString();
    }

    /**
     * read utf16 strings, ending with 0 char.
     */
    public static String readZeroTerminatedString(ByteBuffer buffer, int strLen) {
        StringBuilder sb = new StringBuilder(strLen);
        for (int i = 0; i < strLen; i++) {
            char c = buffer.getChar();
            if (c == '\0') {
                skip(buffer, (strLen - i - 1) * 2);
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * skip count bytes
     */
    public static void skip(ByteBuffer buffer, int count) {
        position(buffer, buffer.position() + count);
    }

    // Cast java.nio.ByteBuffer instances where necessary to java.nio.Buffer to avoid NoSuchMethodError
    // when running on Java 6 to Java 8.
    // The Java 9 ByteBuffer classes introduces overloaded methods with covariant return types the following methods:
    // position, limit, flip, clear, mark, reset, rewind, etc.


    /**
     * set position
     */
    public static void position(ByteBuffer buffer, int position) {
        ((Buffer) buffer).position(position);
    }

    /**
     * set position
     */
    public static void position(ByteBuffer buffer, long position) {
        position(buffer, Unsigned.ensureUInt(position));
    }


    /**
     * Return one new ByteBuffer from current position, with size, the byte order of new buffer will be set to little endian;
     * And advance the original buffer with size.
     */
    public static ByteBuffer sliceAndSkip(ByteBuffer buffer, int size) {
        ByteBuffer buf = buffer.slice().order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer slice = (ByteBuffer) ((Buffer) buf).limit(buf.position() + size);
        skip(buffer, size);
        return slice;
    }
}
