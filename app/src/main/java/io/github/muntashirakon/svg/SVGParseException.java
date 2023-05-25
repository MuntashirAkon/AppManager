// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.svg;

/**
 * Runtime exception thrown when there is a problem parsing an SVG.
 */
// Copyright 2011 Larva Labs, LLC
public class SVGParseException extends RuntimeException {
    public SVGParseException(String s) {
        super(s);
    }

    public SVGParseException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public SVGParseException(Throwable throwable) {
        super(throwable);
    }
}