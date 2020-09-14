package net.dongliu.apk.parser.exception;

/**
 * throwed when parse failed.
 *
 * @author dongliu
 */
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

    public ParserException(String message, Throwable cause, boolean enableSuppression,
                           boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public ParserException() {
    }
}
