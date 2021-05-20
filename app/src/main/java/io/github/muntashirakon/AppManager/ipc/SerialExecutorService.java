// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.topjohnwu.superuser.Shell.EXECUTOR;

// Copyright 2020 John "topjohnwu" Wu
public class SerialExecutorService extends AbstractExecutorService implements Callable<Void> {
    private boolean isShutdown = false;
    private final ArrayDeque<Runnable> mTasks = new ArrayDeque<>();
    private FutureTask<Void> scheduleTask = null;

    @Override
    public Void call() {
        for (; ; ) {
            Runnable task;
            synchronized (this) {
                if ((task = mTasks.poll()) == null) {
                    scheduleTask = null;
                    return null;
                }
            }
            task.run();
        }
    }

    @Override
    public synchronized void execute(Runnable r) {
        if (isShutdown) {
            throw new RejectedExecutionException(
                    "Task " + r.toString() + " rejected from " + this);
        }
        mTasks.offer(r);
        if (scheduleTask == null) {
            scheduleTask = new FutureTask<>(this);
            EXECUTOR.execute(scheduleTask);
        }
    }

    @Override
    public synchronized void shutdown() {
        isShutdown = true;
        mTasks.clear();
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        isShutdown = true;
        if (scheduleTask != null)
            scheduleTask.cancel(true);
        try {
            return new ArrayList<>(mTasks);
        } finally {
            mTasks.clear();
        }
    }

    @Override
    public synchronized boolean isShutdown() {
        return isShutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return isShutdown && scheduleTask == null;
    }

    @Override
    public synchronized boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (scheduleTask == null)
            return true;
        try {
            scheduleTask.get(timeout, unit);
        } catch (TimeoutException e) {
            return false;
        } catch (ExecutionException ignored) {
        }
        return true;
    }
}
