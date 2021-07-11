// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.archivers.zip;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

/**
 * A ZipEncoding, which uses a java.nio {@link
 * java.nio.charset.Charset Charset} to encode names.
 * <p>The methods of this class are reentrant.</p>
 * @Immutable
 */
// Copyright 2009 Stefan Bodewig
class NioZipEncoding implements ZipEncoding, CharsetAccessor {

    private final Charset charset;
    private final boolean useReplacement;
    private static final char REPLACEMENT = '?';
    private static final byte[] REPLACEMENT_BYTES = { (byte) REPLACEMENT };
    private static final String REPLACEMENT_STRING = String.valueOf(REPLACEMENT);
    private static final char[] HEX_CHARS = new char[] {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };


    /**
     * Construct an NioZipEncoding using the given charset.
     * @param charset  The character set to use.
     * @param useReplacement should invalid characters be replaced, or reported.
     */
    NioZipEncoding(final Charset charset, final boolean useReplacement) {
        this.charset = charset;
        this.useReplacement = useReplacement;
    }

    @Override
    public Charset getCharset() {
        return charset;
    }

    /**
     * @see  ZipEncoding#canEncode(java.lang.String)
     */
    @Override
    public boolean canEncode(final String name) {
        final CharsetEncoder enc = newEncoder();

        return enc.canEncode(name);
    }

    /**
     * @see ZipEncoding#encode(java.lang.String)
     */
    @Override
    public ByteBuffer encode(final String name) {
        final CharsetEncoder enc = newEncoder();

        final CharBuffer cb = CharBuffer.wrap(name);
        CharBuffer tmp = null;
        ByteBuffer out = ByteBuffer.allocate(estimateInitialBufferSize(enc, cb.remaining()));

        while (cb.hasRemaining()) {
            final CoderResult res = enc.encode(cb, out, false);

            if (res.isUnmappable() || res.isMalformed()) {

                // write the unmappable characters in utf-16
                // pseudo-URL encoding style to ByteBuffer.

                final int spaceForSurrogate = estimateIncrementalEncodingSize(enc, 6 * res.length());
                if (spaceForSurrogate > out.remaining()) {
                    // if the destination buffer isn't over sized, assume that the presence of one
                    // unmappable character makes it likely that there will be more. Find all the
                    // un-encoded characters and allocate space based on those estimates.
                    int charCount = 0;
                    for (int i = cb.position() ; i < cb.limit(); i++) {
                        charCount += !enc.canEncode(cb.get(i)) ? 6 : 1;
                    }
                    final int totalExtraSpace = estimateIncrementalEncodingSize(enc, charCount);
                    out = ZipEncodingHelper.growBufferBy(out, totalExtraSpace - out.remaining());
                }
                if (tmp == null) {
                    tmp = CharBuffer.allocate(6);
                }
                for (int i = 0; i < res.length(); ++i) {
                    out = encodeFully(enc, encodeSurrogate(tmp, cb.get()), out);
                }

            } else if (res.isOverflow()) {
                final int increment = estimateIncrementalEncodingSize(enc, cb.remaining());
                out = ZipEncodingHelper.growBufferBy(out, increment);

            } else if (res.isUnderflow() || res.isError()) {
                break;
            }
        }
        // tell the encoder we are done
        enc.encode(cb, out, true);
        // may have caused underflow, but that's been ignored traditionally

        out.limit(out.position());
        out.rewind();
        return out;
    }

    /**
     * @see ZipEncoding#decode(byte[])
     */
    @Override
    public String decode(final byte[] data) throws IOException {
        return newDecoder()
            .decode(ByteBuffer.wrap(data)).toString();
    }

    private static ByteBuffer encodeFully(final CharsetEncoder enc, final CharBuffer cb, final ByteBuffer out) {
        ByteBuffer o = out;
        while (cb.hasRemaining()) {
            final CoderResult result = enc.encode(cb, o, false);
            if (result.isOverflow()) {
                final int increment = estimateIncrementalEncodingSize(enc, cb.remaining());
                o = ZipEncodingHelper.growBufferBy(o, increment);
            }
        }
        return o;
    }

    private static CharBuffer encodeSurrogate(final CharBuffer cb, final char c) {
        cb.position(0).limit(6);
        cb.put('%');
        cb.put('U');

        cb.put(HEX_CHARS[(c >> 12) & 0x0f]);
        cb.put(HEX_CHARS[(c >> 8) & 0x0f]);
        cb.put(HEX_CHARS[(c >> 4) & 0x0f]);
        cb.put(HEX_CHARS[c & 0x0f]);
        cb.flip();
        return cb;
    }

    private CharsetEncoder newEncoder() {
        if (useReplacement) {
            return charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith(REPLACEMENT_BYTES);
        }
        return charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    private CharsetDecoder newDecoder() {
        if (!useReplacement) {
            return this.charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
        return  charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .replaceWith(REPLACEMENT_STRING);
    }

    /**
     * Estimate the initial encoded size (in bytes) for a character buffer.
     * <p>
     * The estimate assumes that one character consumes uses the maximum length encoding,
     * whilst the rest use an average size encoding. This accounts for any BOM for UTF-16, at
     * the expense of a couple of extra bytes for UTF-8 encoded ASCII.
     * </p>
     *
     * @param enc        encoder to use for estimates
     * @param charChount number of characters in string
     * @return estimated size in bytes.
     */
    private static int estimateInitialBufferSize(final CharsetEncoder enc, final int charChount) {
        final float first = enc.maxBytesPerChar();
        final float rest = (charChount - 1) * enc.averageBytesPerChar();
        return (int) Math.ceil(first + rest);
    }

    /**
     * Estimate the size needed for remaining characters
     *
     * @param enc       encoder to use for estimates
     * @param charCount number of characters remaining
     * @return estimated size in bytes.
     */
    private static int estimateIncrementalEncodingSize(final CharsetEncoder enc, final int charCount) {
        return (int) Math.ceil(charCount * enc.averageBytesPerChar());
    }

}