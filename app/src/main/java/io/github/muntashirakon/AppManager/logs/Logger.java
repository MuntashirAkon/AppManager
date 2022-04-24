// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logs;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.AppManager;

public class Logger implements Closeable {
    public static File getLoggingDirectory() {
        File cacheDir = AppManager.getContext().getExternalCacheDir();
        if (cacheDir != null && cacheDir.canWrite()) {
            return cacheDir;
        }
        return AppManager.getContext().getCacheDir();
    }

    private final PrintWriter mWriter;
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(1);

    private boolean mIsClosed;

    protected Logger(File logFile, boolean append) throws IOException {
        mWriter = new PrintWriter(new BufferedWriter(new FileWriter(logFile, append)));
    }

    protected Logger(PrintWriter printWriter) throws IOException {
        mWriter = printWriter;
    }

    @CallSuper
    public void println(@Nullable Object message) {
        println(message, null);
    }

    @CallSuper
    public void println(@Nullable Object message, @Nullable Throwable tr) {
        mExecutor.submit(() -> {
            synchronized (mWriter) {
                mWriter.println(message);
                if (tr != null) {
                    tr.printStackTrace(mWriter);
                }
                mWriter.flush();
            }
        });
    }

    @CallSuper
    @Override
    public void close() {
        mWriter.close();
        mIsClosed = true;
    }

    @Override
    protected void finalize() throws Throwable {
        // Closing is mandatory in order to make sure the logs are written correctly
        if (!mIsClosed) {
            mWriter.close();
        }
    }
}
