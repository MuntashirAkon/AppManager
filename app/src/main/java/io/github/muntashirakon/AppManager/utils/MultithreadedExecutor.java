// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.muntashirakon.AppManager.logs.Log;

public class MultithreadedExecutor implements ExecutorService {
    private static final List<MultithreadedExecutor> sExecutorCache = new ArrayList<>();

    @AnyThread
    @NonNull
    public static MultithreadedExecutor getNewInstance() {
        if (sExecutorCache.size() > 0) {
            // Check if any executor has been shutdown
            for (MultithreadedExecutor executor : sExecutorCache) {
                if (executor.isTerminated()) {
                    executor.renew();
                    return executor;
                }
            }
        }
        MultithreadedExecutor executor = new MultithreadedExecutor();
        sExecutorCache.add(executor);
        return executor;
    }

    @NonNull
    private ExecutorService mExecutor;

    private MultithreadedExecutor() {
        mExecutor = Executors.newFixedThreadPool(getThreadCount());
    }

    private void renew() {
        if (mExecutor.isTerminated()) {
            // TODO: 26/5/21 Find a better way to recreate an executor
            mExecutor = Executors.newFixedThreadPool(getThreadCount());
        }
    }

    @Override
    public Future<?> submit(Runnable task) {
        if (mExecutor.isShutdown()) throw new UnsupportedOperationException("The executor was terminated");
        return mExecutor.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return mExecutor.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return mExecutor.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws ExecutionException, InterruptedException {
        return mExecutor.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        return mExecutor.invokeAny(tasks, timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (mExecutor.isShutdown()) throw new UnsupportedOperationException("The executor was terminated");
        return mExecutor.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (mExecutor.isShutdown()) throw new UnsupportedOperationException("The executor was terminated");
        return mExecutor.submit(task, result);
    }

    @WorkerThread
    public void awaitCompletion() {
        shutdown();
        while (!isTerminated()) {
            try {
                awaitTermination(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Log.e("MultithreadedExecutor", e);
            }
        }
    }

    @Override
    public void shutdown() {
        mExecutor.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return mExecutor.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return mExecutor.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return mExecutor.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return mExecutor.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        mExecutor.execute(command);
    }

    public static int getThreadCount() {
        int configuredCount = AppPref.getInt(AppPref.PrefKey.PREF_CONCURRENCY_THREAD_COUNT_INT);
        int totalCores = Utils.getTotalCores();
        if (configuredCount <= 0 || configuredCount > totalCores) return totalCores;
        return configuredCount;
    }

    /**
     *
     * @param threadCount 1 - total cores. 0 = Total cores.
     */
    public static void setThreadCount(int threadCount) {
        AppPref.set(AppPref.PrefKey.PREF_CONCURRENCY_THREAD_COUNT_INT, threadCount);
    }
}
