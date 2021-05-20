// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

public class CryptoException extends Throwable {
    public CryptoException() {
        super();
    }

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }

    public CryptoException(Throwable cause) {
        super(cause);
    }
}
