// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.utils;

/**
 * Character encoding names required of every implementation of the Java platform.
 *
 * From the Java documentation <a href="https://download.oracle.com/javase/6/docs/api/java/nio/charset/Charset.html">Standard
 * charsets</a>:
 * <p>
 * <cite>Every implementation of the Java platform is required to support the following character encodings. Consult the
 * release documentation for your implementation to see if any other encodings are supported. Consult the release
 * documentation for your implementation to see if any other encodings are supported. </cite>
 * </p>
 *
 * <dl>
 * <dt><code>US-ASCII</code></dt>
 * <dd>Seven-bit ASCII, a.k.a. ISO646-US, a.k.a. the Basic Latin block of the Unicode character set.</dd>
 * <dt><code>ISO-8859-1</code></dt>
 * <dd>ISO Latin Alphabet No. 1, a.k.a. ISO-LATIN-1.</dd>
 * <dt><code>UTF-8</code></dt>
 * <dd>Eight-bit Unicode Transformation Format.</dd>
 * <dt><code>UTF-16BE</code></dt>
 * <dd>Sixteen-bit Unicode Transformation Format, big-endian byte order.</dd>
 * <dt><code>UTF-16LE</code></dt>
 * <dd>Sixteen-bit Unicode Transformation Format, little-endian byte order.</dd>
 * <dt><code>UTF-16</code></dt>
 * <dd>Sixteen-bit Unicode Transformation Format, byte order specified by a mandatory initial byte-order mark (either order
 * accepted on input, big-endian used on output.)</dd>
 * </dl>
 *
 * <p>This perhaps would best belong in the [lang] project. Even if a similar interface is defined in [lang], it is not
 * foreseen that [compress] would be made to depend on [lang].</p>
 *
 * @see <a href="https://download.oracle.com/javase/6/docs/api/java/nio/charset/Charset.html">Standard charsets</a>
 * @since 1.4
 */
// Copyright 2012 Gary Gregory
public class CharsetNames {
    /**
     * CharEncodingISO Latin Alphabet No. 1, a.k.a. ISO-LATIN-1.
     * <p>
     * Every implementation of the Java platform is required to support this character encoding.
     * </p>
     *
     * @see <a href="https://download.oracle.com/javase/6/docs/api/java/nio/charset/Charset.html">Standard charsets</a>
     */
    public static final String ISO_8859_1 = "ISO-8859-1";

    /**
     * <p>
     * Seven-bit ASCII, also known as ISO646-US, also known as the Basic Latin block of the Unicode character set.
     * </p>
     * <p>
     * Every implementation of the Java platform is required to support this character encoding.
     * </p>
     *
     * @see <a href="https://download.oracle.com/javase/6/docs/api/java/nio/charset/Charset.html">Standard charsets</a>
     */
    public static final String US_ASCII = "US-ASCII";

    /**
     * <p>
     * Sixteen-bit Unicode Transformation Format, The byte order specified by a mandatory initial byte-order mark
     * (either order accepted on input, big-endian used on output)
     * </p>
     * <p>
     * Every implementation of the Java platform is required to support this character encoding.
     * </p>
     *
     * @see <a href="https://download.oracle.com/javase/6/docs/api/java/nio/charset/Charset.html">Standard charsets</a>
     */
    public static final String UTF_16 = "UTF-16";

    /**
     * <p>
     * Sixteen-bit Unicode Transformation Format, big-endian byte order.
     * </p>
     * <p>
     * Every implementation of the Java platform is required to support this character encoding.
     * </p>
     *
     * @see <a href="https://download.oracle.com/javase/6/docs/api/java/nio/charset/Charset.html">Standard charsets</a>
     */
    public static final String UTF_16BE = "UTF-16BE";

    /**
     * <p>
     * Sixteen-bit Unicode Transformation Format, little-endian byte order.
     * </p>
     * <p>
     * Every implementation of the Java platform is required to support this character encoding.
     * </p>
     *
     * @see <a href="https://download.oracle.com/javase/6/docs/api/java/nio/charset/Charset.html">Standard charsets</a>
     */
    public static final String UTF_16LE = "UTF-16LE";

    /**
     * <p>
     * Eight-bit Unicode Transformation Format.
     * </p>
     * <p>
     * Every implementation of the Java platform is required to support this character encoding.
     * </p>
     *
     * @see <a href="https://download.oracle.com/javase/6/docs/api/java/nio/charset/Charset.html">Standard charsets</a>
     */
    public static final String UTF_8 = "UTF-8";
}