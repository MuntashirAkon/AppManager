package net.dongliu.apk.parser.utils;

/**
 * Unsigned utils, for compatible with java6/java7.
 */
public class Unsigned {
    public static long toLong(int value) {
        return value & 0xffffffffL;
    }

    public static int toUInt(long value) {
        return (int) value;
    }

    public static int toInt(short value) {
        return value & 0xffff;
    }

    public static short toUShort(int value) {
        return (short) value;
    }

    public static int ensureUInt(long value) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new ArithmeticException("unsigned integer overflow");
        }
        return (int) value;
    }


    public static long ensureULong(long value) {
        if (value < 0) {
            throw new ArithmeticException("unsigned long overflow");
        }
        return value;
    }

    public static short toShort(byte value) {
        return (short) (value & 0xff);
    }

    public static byte toUByte(short value) {
        return (byte) value;
    }
}
