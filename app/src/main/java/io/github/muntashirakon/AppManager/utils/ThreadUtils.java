// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.logs.Log;

// Copyright 2016 The Android Open Source Project
public class ThreadUtils {
    private static volatile Thread sMainThread;
    private static volatile Handler sMainThreadHandler;
    private static volatile ExecutorService sThreadExecutor;

    /**
     * Returns true if the current thread is the UI thread.
     */
    public static boolean isMainThread() {
        if (sMainThread == null) {
            sMainThread = Looper.getMainLooper().getThread();
        }
        return Thread.currentThread() == sMainThread;
    }

    /**
     * Returns a shared UI thread handler.
     */
    public static Handler getUiThreadHandler() {
        if (sMainThreadHandler == null) {
            sMainThreadHandler = new Handler(Looper.getMainLooper());
        }

        return sMainThreadHandler;
    }

    /**
     * Checks that the current thread is the UI thread. Otherwise, throws an exception.
     */
    public static void ensureMainThread() {
        if (!isMainThread()) {
            throw new RuntimeException("Must be called on the UI thread");
        }
    }

    /**
     * Checks that the current thread is a worker thread. Otherwise, throws an exception.
     */
    public static void ensureWorkerThread() {
        if (isMainThread()) {
            throw new RuntimeException("Must be called on a worker thread");
        }
    }

    /**
     * Tests whether this thread has been interrupted. The <i>interrupted status</i> of the thread is unaffected by this
     * method.
     *
     * <p>A thread interruption ignored because a thread was not alive at the time of the interrupt will be reflected by
     * this method returning false.
     *
     * @return {@code true} if this thread has been interrupted; {@code false} otherwise.
     */
    public static boolean isInterrupted() {
        boolean interrupted = Thread.currentThread().isInterrupted();
        if (interrupted) {
            Log.d("ThreadUtils", "Thread interrupted.");
        }
        return interrupted;
    }

    /**
     * Posts runnable in background using shared background thread pool.
     *
     * @return A future of the task that can be monitored for updates or cancelled.
     */
    public static Future<?> postOnBackgroundThread(Runnable runnable) {
        return getBackgroundThreadExecutor().submit(runnable);
    }

    /**
     * Posts callable in background using shared background thread pool.
     *
     * @return A future of the task that can be monitored for updates or cancelled.
     */
    public static <T> Future<T> postOnBackgroundThread(Callable<T> callable) {
        return getBackgroundThreadExecutor().submit(callable);
    }

    /**
     * Posts the runnable on the main thread.
     */
    public static void postOnMainThread(Runnable runnable) {
        getUiThreadHandler().post(runnable);
    }

    /**
     * Posts the runnable on the main thread with a delay.
     */
    public static void postOnMainThreadDelayed(Runnable runnable, long delayMillis) {
        getUiThreadHandler().postDelayed(runnable, delayMillis);
    }

    public static synchronized ExecutorService getBackgroundThreadExecutor() {
        if (sThreadExecutor == null) {
            sThreadExecutor = MultithreadedExecutor.getNewInstance();
        }
        return sThreadExecutor;
    }
}