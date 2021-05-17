// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.archivers.zip;

import java.nio.charset.Charset;

/**
 * An interface added to allow access to the character set associated with an {@link NioZipEncoding},
 * without requiring a new method to be added to {@link ZipEncoding}.
 * <p>
 * This avoids introducing a
 * potentially breaking change, or making {@link NioZipEncoding} a public class.
 * </p>
 * @since 1.15
 */
// Copyright 2017 Gary Gregory
public interface CharsetAccessor {

    /**
     * Provides access to the character set associated with an object.
     * <p>
     *     This allows nio oriented code to use more natural character encoding/decoding methods,
     *     whilst allowing existing code to continue to rely on special-case error handling for UTF-8.
     * </p>
     * @return the character set associated with this object
     */
    Charset getCharset();
}