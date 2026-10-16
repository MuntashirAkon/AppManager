// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logs;

import java.io.IOException;

/**
 * Logger implementation that intentionally discards all messages.
 */
public final class EmptyLogger extends Logger {
    public EmptyLogger() throws IOException {
        super(null);
    }
}
