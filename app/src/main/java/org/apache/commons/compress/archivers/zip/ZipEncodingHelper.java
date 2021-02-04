/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.commons.compress.archivers.zip;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

/**
 * Static helper functions for robustly encoding file names in zip files.
 */
public abstract class ZipEncodingHelper {


    /**
     * name of the encoding UTF-8
     */
    static final String UTF8 = "UTF8";

    /**
     * the encoding UTF-8
     */
    static final ZipEncoding UTF8_ZIP_ENCODING = getZipEncoding(UTF8);

    /**
     * Instantiates a zip encoding. An NIO based character set encoder/decoder will be returned.
     * As a special case, if the character set is UTF-8, the nio encoder will be configured  replace malformed and
     * unmappable characters with '?'. This matches existing behavior from the older fallback encoder.
     * <p>
     *     If the requested character set cannot be found, the platform default will
     *     be used instead.
     * </p>
     * @param name The name of the zip encoding. Specify {@code null} for
     *             the platform's default encoding.
     * @return A zip encoding for the given encoding name.
     */
    public static ZipEncoding getZipEncoding(final String name) {
        Charset cs = Charset.defaultCharset();
        if (name != null) {
            try {
                cs = Charset.forName(name);
            } catch (final UnsupportedCharsetException e) { // NOSONAR we use the default encoding instead
            }
        }
        final boolean useReplacement = isUTF8(cs.name());
        return new NioZipEncoding(cs, useReplacement);
    }

    /**
     * Returns whether a given encoding is UTF-8. If the given name is null, then check the platform's default encoding.
     *
     * @param charsetName If the given name is null, then check the platform's default encoding.
     */
    static boolean isUTF8(String charsetName) {
        if (charsetName == null) {
            // check platform's default encoding
            charsetName = Charset.defaultCharset().name();
        }
        if (StandardCharsets.UTF_8.name().equalsIgnoreCase(charsetName)) {
            return true;
        }
        for (final String alias : StandardCharsets.UTF_8.aliases()) {
            if (alias.equalsIgnoreCase(charsetName)) {
                return true;
            }
        }
        return false;
    }

    static ByteBuffer growBufferBy(final ByteBuffer buffer, final int increment) {
        buffer.limit(buffer.position());
        buffer.rewind();

        final ByteBuffer on = ByteBuffer.allocate(buffer.capacity() + increment);

        on.put(buffer);
        return on;
    }
}