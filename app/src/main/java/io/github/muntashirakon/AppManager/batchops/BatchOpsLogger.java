// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

import io.github.muntashirakon.AppManager.logs.Logger;
import io.github.muntashirakon.io.Paths;

public class BatchOpsLogger extends Logger {
    private static final File LOG_FILE = new File(getLoggingDirectory(), "batch_ops.log");

    protected BatchOpsLogger() throws IOException {
        super(LOG_FILE, false);
    }

    @NonNull
    public static String getAllLogs() {
        return Paths.get(LOG_FILE).getContentAsString();
    }

    public static void clearLogs() {
        LOG_FILE.delete();
    }
}
