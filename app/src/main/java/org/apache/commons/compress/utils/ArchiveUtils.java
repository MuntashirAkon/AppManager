// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.utils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.commons.compress.archivers.ArchiveEntry;

/**
 * Generic Archive utilities
 */
// Copyright 2009 sebbASF
public class ArchiveUtils {

    private static final int MAX_SANITIZED_NAME_LENGTH = 255;

    /** Private constructor to prevent instantiation of this utility class. */
    private ArchiveUtils(){
    }

    /**
     * Generates a string containing the name, isDirectory setting and size of an entry.
     * <p>
     * For example:
     * <pre>
     * -    2000 main.c
     * d     100 testfiles
     * </pre>
     *
     * @param entry the entry
     * @return the representation of the entry
     */
    public static String toString(final ArchiveEntry entry){
        final StringBuilder sb = new StringBuilder();
        sb.append(entry.isDirectory()? 'd' : '-');// c.f. "ls -l" output
        final String size = Long.toString(entry.getSize());
        sb.append(' ');
        // Pad output to 7 places, leading spaces
        for(int i=7; i > size.length(); i--){
            sb.append(' ');
        }
        sb.append(size);
        sb.append(' ').append(entry.getName());
        return sb.toString();
    }

    /**
     * Check if buffer contents matches Ascii String.
     *
     * @param expected expected string
     * @param buffer the buffer
     * @param offset offset to read from
     * @param length length of the buffer
     * @return {@code true} if buffer is the same as the expected string
     */
    public static boolean matchAsciiBuffer(
            final String expected, final byte[] buffer, final int offset, final int length){
        final byte[] buffer1;
        buffer1 = expected.getBytes(StandardCharsets.US_ASCII);
        return isEqual(buffer1, 0, buffer1.length, buffer, offset, length, false);
    }

    /**
     * Check if buffer contents matches Ascii String.
     *
     * @param expected the expected strin
     * @param buffer the buffer
     * @return {@code true} if buffer is the same as the expected string
     */
    public static boolean matchAsciiBuffer(final String expected, final byte[] buffer){
        return matchAsciiBuffer(expected, buffer, 0, buffer.length);
    }

    /**
     * Convert a string to Ascii bytes.
     * Used for comparing "magic" strings which need to be independent of the default Locale.
     *
     * @param inputString string to convert
     * @return the bytes
     */
    public static byte[] toAsciiBytes(final String inputString){
        return inputString.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Convert an input byte array to a String using the ASCII character set.
     *
     * @param inputBytes bytes to convert
     * @return the bytes, interpreted as an Ascii string
     */
    public static String toAsciiString(final byte[] inputBytes){
        return new String(inputBytes, StandardCharsets.US_ASCII);
    }

    /**
     * Convert an input byte array to a String using the ASCII character set.
     *
     * @param inputBytes input byte array
     * @param offset offset within array
     * @param length length of array
     * @return the bytes, interpreted as an Ascii string
     */
    public static String toAsciiString(final byte[] inputBytes, final int offset, final int length){
        return new String(inputBytes, offset, length, StandardCharsets.US_ASCII);
    }

    /**
     * Compare byte buffers, optionally ignoring trailing nulls
     *
     * @param buffer1 first buffer
     * @param offset1 first offset
     * @param length1 first length
     * @param buffer2 second buffer
     * @param offset2 second offset
     * @param length2 second length
     * @param ignoreTrailingNulls whether to ignore trailing nulls
     * @return {@code true} if buffer1 and buffer2 have same contents, having regard to trailing nulls
     */
    public static boolean isEqual(
            final byte[] buffer1, final int offset1, final int length1,
            final byte[] buffer2, final int offset2, final int length2,
            final boolean ignoreTrailingNulls){
        final int minLen=length1 < length2 ? length1 : length2;
        for (int i=0; i < minLen; i++){
            if (buffer1[offset1+i] != buffer2[offset2+i]){
                return false;
            }
        }
        if (length1 == length2){
            return true;
        }
        if (ignoreTrailingNulls){
            if (length1 > length2){
                for(int i = length2; i < length1; i++){
                    if (buffer1[offset1+i] != 0){
                        return false;
                    }
                }
            } else {
                for(int i = length1; i < length2; i++){
                    if (buffer2[offset2+i] != 0){
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Compare byte buffers
     *
     * @param buffer1 the first buffer
     * @param offset1 the first offset
     * @param length1 the first length
     * @param buffer2 the second buffer
     * @param offset2 the second offset
     * @param length2 the second length
     * @return {@code true} if buffer1 and buffer2 have same contents
     */
    public static boolean isEqual(
            final byte[] buffer1, final int offset1, final int length1,
            final byte[] buffer2, final int offset2, final int length2){
        return isEqual(buffer1, offset1, length1, buffer2, offset2, length2, false);
    }

    /**
     * Compare byte buffers
     *
     * @param buffer1 the first buffer
     * @param buffer2 the second buffer
     * @return {@code true} if buffer1 and buffer2 have same contents
     */
    public static boolean isEqual(final byte[] buffer1, final byte[] buffer2 ){
        return isEqual(buffer1, 0, buffer1.length, buffer2, 0, buffer2.length, false);
    }

    /**
     * Compare byte buffers, optionally ignoring trailing nulls
     *
     * @param buffer1 the first buffer
     * @param buffer2 the second buffer
     * @param ignoreTrailingNulls whether to ignore trailing nulls
     * @return {@code true} if buffer1 and buffer2 have same contents
     */
    public static boolean isEqual(final byte[] buffer1, final byte[] buffer2, final boolean ignoreTrailingNulls){
        return isEqual(buffer1, 0, buffer1.length, buffer2, 0, buffer2.length, ignoreTrailingNulls);
    }

    /**
     * Compare byte buffers, ignoring trailing nulls
     *
     * @param buffer1 the first buffer
     * @param offset1 the first offset
     * @param length1 the first length
     * @param buffer2 the second buffer
     * @param offset2 the second offset
     * @param length2 the second length
     * @return {@code true} if buffer1 and buffer2 have same contents, having regard to trailing nulls
     */
    public static boolean isEqualWithNull(
            final byte[] buffer1, final int offset1, final int length1,
            final byte[] buffer2, final int offset2, final int length2){
        return isEqual(buffer1, offset1, length1, buffer2, offset2, length2, true);
    }

    /**
     * Returns true if the first N bytes of an array are all zero
     *
     * @param a
     *            The array to check
     * @param size
     *            The number of characters to check (not the size of the array)
     * @return true if the first N bytes are zero
     */
    public static boolean isArrayZero(final byte[] a, final int size) {
        for (int i = 0; i < size; i++) {
            if (a[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a "sanitized" version of the string given as arguments,
     * where sanitized means non-printable characters have been
     * replaced with a question mark and the outcome is not longer
     * than 255 chars.
     *
     * <p>This method is used to clean up file names when they are
     * used in exception messages as they may end up in log files or
     * as console output and may have been read from a corrupted
     * input.</p>
     *
     * @param s the string to sanitize
     * @return a sanitized version of the argument
     * @since Compress 1.12
     */
    public static String sanitize(final String s) {
        final char[] cs = s.toCharArray();
        final char[] chars = cs.length <= MAX_SANITIZED_NAME_LENGTH ? cs : Arrays.copyOf(cs, MAX_SANITIZED_NAME_LENGTH);
        if (cs.length > MAX_SANITIZED_NAME_LENGTH) {
            for (int i = MAX_SANITIZED_NAME_LENGTH - 3; i < MAX_SANITIZED_NAME_LENGTH; i++) {
                chars[i] = '.';
            }
        }
        final StringBuilder sb = new StringBuilder();
        for (final char c : chars) {
            if (!Character.isISOControl(c)) {
                final Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
                if (block != null && block != Character.UnicodeBlock.SPECIALS) {
                    sb.append(c);
                    continue;
                }
            }
            sb.append('?');
        }
        return sb.toString();
    }

}