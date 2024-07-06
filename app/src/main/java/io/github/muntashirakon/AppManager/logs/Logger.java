// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logs;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.utils.FileUtils;

public class Logger implements Closeable {
    @NonNull
    public static File getLoggingDirectory() {
        return FileUtils.getCachePath();
    }

    @Nullable
    private final PrintWriter mWriter;

    private boolean mIsClosed;

    protected Logger(@Nullable File logFile, boolean append) throws IOException {
        if (logFile != null) {
            mWriter = new PrintWriter(new BufferedWriter(new FileWriter(logFile, append)));
        } else mWriter = null;
    }

    protected Logger(@Nullable PrintWriter printWriter) throws IOException {
        mWriter = printWriter;
    }

    @CallSuper
    public void println(@Nullable Object message) {
        println(message, null);
    }

    @CallSuper
    public void println(@Nullable Object message, @Nullable Throwable tr) {
        if (mWriter == null) {
            // Do nothing
            return;
        }
        synchronized (mWriter) {
            mWriter.println(message);
            if (tr != null) {
                tr.printStackTrace(mWriter);
            }
            if (BuildConfig.DEBUG) {
                mWriter.flush();
            }
        }
    }

    @CallSuper
    @Override
    public void close() {
        if (mWriter != null) {
            mWriter.flush();
            mWriter.close();
        }
        mIsClosed = true;
    }

    @Override
    protected void finalize() {
        // Closing is mandatory in order to make sure the logs are written correctly
        if (!mIsClosed && mWriter != null) {
            mWriter.flush();
            mWriter.close();
        }
    }
}
