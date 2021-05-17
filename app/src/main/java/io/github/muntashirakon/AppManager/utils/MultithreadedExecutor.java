// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.logs.Log;

public class MultithreadedExecutor {
    @WorkerThread
    @NonNull
    public static MultithreadedExecutor getNewInstance() {
        return new MultithreadedExecutor();
    }

    private final ExecutorService executor;
    private boolean isShutdown = false;
    private MultithreadedExecutor() {
        executor = Executors.newFixedThreadPool(Utils.getTotalCores());
    }

    public Future<?> submit(Runnable task) {
        if (isShutdown) throw new UnsupportedOperationException("The executor was terminated");
        return executor.submit(task);
    }

    public  <T> Future<T> submit(Callable<T> task) {
        if (isShutdown) throw new UnsupportedOperationException("The executor was terminated");
        return executor.submit(task);
    }

    public  <T> Future<T> submit(Runnable task, T result) {
        if (isShutdown) throw new UnsupportedOperationException("The executor was terminated");
        return executor.submit(task, result);
    }

    @WorkerThread
    public void awaitCompletion() {
        isShutdown = true;
        executor.shutdown();
        while (!executor.isTerminated()) {
            try {
                executor.awaitTermination(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Log.e("MultithreadedExecutor", e);
            }
        }
    }

    public void shutdown() {
        isShutdown = true;
        executor.shutdown();
    }
}
