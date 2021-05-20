// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.utils;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Re-implements {@link FilterInputStream#close()} to do nothing.
 * @since 1.14
 */
// Copyright 2017 Stefan Bodewig
public class CloseShieldFilterInputStream extends FilterInputStream {

    public CloseShieldFilterInputStream(final InputStream in) {
        super(in);
    }

    @Override
    public void close() throws IOException {
        // NO IMPLEMENTATION.
    }

}