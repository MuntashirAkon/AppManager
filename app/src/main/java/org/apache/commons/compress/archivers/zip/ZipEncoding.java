/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.commons.compress.archivers.zip;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * An interface for encoders that do a pretty encoding of ZIP
 * file names.
 *
 * <p>There are mostly two implementations, one that uses java.nio
 * {@link java.nio.charset.Charset Charset} and one implementation,
 * which copes with simple 8 bit charsets, because java-1.4 did not
 * support Cp437 in java.nio.</p>
 *
 * <p>The main reason for defining an own encoding layer comes from
 * the problems with {@link java.lang.String#getBytes(String)
 * String.getBytes}, which encodes unknown characters as ASCII
 * quotation marks ('?'). Quotation marks are per definition an
 * invalid file name on some operating systems  like Windows, which
 * leads to ignored ZIP entries.</p>
 *
 * <p>All implementations should implement this interface in a
 * reentrant way.</p>
 */
public interface ZipEncoding {
    /**
     * Check, whether the given string may be losslessly encoded using this
     * encoding.
     *
     * @param name A file name or ZIP comment.
     * @return Whether the given name may be encoded with out any losses.
     */
    boolean canEncode(String name);

    /**
     * Encode a file name or a comment to a byte array suitable for
     * storing it to a serialized zip entry.
     *
     * <p>Examples for CP 437 (in pseudo-notation, right hand side is
     * C-style notation):</p>
     * <pre>
     *  encode("\u20AC_for_Dollar.txt") = "%U20AC_for_Dollar.txt"
     *  encode("\u00D6lf\u00E4sser.txt") = "\231lf\204sser.txt"
     * </pre>
     *
     * @param name A file name or ZIP comment.
     * @return A byte buffer with a backing array containing the
     *         encoded name.  Unmappable characters or malformed
     *         character sequences are mapped to a sequence of utf-16
     *         words encoded in the format <code>%Uxxxx</code>.  It is
     *         assumed, that the byte buffer is positioned at the
     *         beginning of the encoded result, the byte buffer has a
     *         backing array and the limit of the byte buffer points
     *         to the end of the encoded result.
     * @throws IOException on error
     */
    ByteBuffer encode(String name) throws IOException;

    /**
     * @param data The byte values to decode.
     * @return The decoded string.
     * @throws IOException on error
     */
    String decode(byte [] data) throws IOException;
}