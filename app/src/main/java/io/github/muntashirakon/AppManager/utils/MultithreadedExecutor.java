// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

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
    private static final List<MultithreadedExecutor> executorCache = new ArrayList<>();

    @WorkerThread
    @NonNull
    public static MultithreadedExecutor getNewInstance() {
        if (executorCache.size() > 0) {
            // Check if any executor has been shutdown
            for (MultithreadedExecutor executor : executorCache) {
                if (executor.isTerminated()) {
                    executor.renew();
                    return executor;
                }
            }
        }
        MultithreadedExecutor executor = new MultithreadedExecutor();
        executorCache.add(executor);
        return executor;
    }

    @NonNull
    private ExecutorService executor;

    private MultithreadedExecutor() {
        executor = Executors.newFixedThreadPool(getThreadCount());
    }

    private void renew() {
        if (executor.isTerminated()) {
            // TODO: 26/5/21 Find a better way to recreate an executor
            executor = Executors.newFixedThreadPool(getThreadCount());
        }
    }

    @Override
    public Future<?> submit(Runnable task) {
        if (executor.isShutdown()) throw new UnsupportedOperationException("The executor was terminated");
        return executor.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return executor.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return executor.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws ExecutionException, InterruptedException {
        return executor.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        return executor.invokeAny(tasks, timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (executor.isShutdown()) throw new UnsupportedOperationException("The executor was terminated");
        return executor.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (executor.isShutdown()) throw new UnsupportedOperationException("The executor was terminated");
        return executor.submit(task, result);
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
        executor.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return executor.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return executor.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }

    public static int getThreadCount() {
        int configuredCount = AppPref.getInt(AppPref.PrefKey.PREF_CONCURRENCY_THREAD_COUNT_INT);
        int totalCores = Utils.getTotalCores();
        if (configuredCount <= 0 || configuredCount > totalCores) return totalCores;
        return configuredCount;
    }
}
