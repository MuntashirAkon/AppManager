// SPDX-License-Identifier: BSD-2-Clause

package net.dongliu.apk.parser.exception;

/**
 * thrown when parse failed.
 */
// Copyright 2014 Liu Dong
public class ParserException extends RuntimeException {
    public ParserException(String msg) {
        super(msg);
    }

    public ParserException(String message, Throwable cause) {
        super(message, cause);
    }

    public ParserException(Throwable cause) {
        super(cause);
    }

    public ParserException() {
    }
}
