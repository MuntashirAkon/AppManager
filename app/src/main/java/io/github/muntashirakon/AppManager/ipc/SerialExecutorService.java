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
    private boolean mIsShutdown = false;
    private final ArrayDeque<Runnable> mTasks = new ArrayDeque<>();
    private FutureTask<Void> mScheduleTask = null;

    @Override
    public Void call() {
        for (; ; ) {
            Runnable task;
            synchronized (this) {
                if ((task = mTasks.poll()) == null) {
                    mScheduleTask = null;
                    return null;
                }
            }
            task.run();
        }
    }

    @Override
    public synchronized void execute(Runnable r) {
        if (mIsShutdown) {
            throw new RejectedExecutionException(
                    "Task " + r.toString() + " rejected from " + this);
        }
        mTasks.offer(r);
        if (mScheduleTask == null) {
            mScheduleTask = new FutureTask<>(this);
            EXECUTOR.execute(mScheduleTask);
        }
    }

    @Override
    public synchronized void shutdown() {
        mIsShutdown = true;
        mTasks.clear();
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        mIsShutdown = true;
        if (mScheduleTask != null)
            mScheduleTask.cancel(true);
        try {
            return new ArrayList<>(mTasks);
        } finally {
            mTasks.clear();
        }
    }

    @Override
    public synchronized boolean isShutdown() {
        return mIsShutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return mIsShutdown && mScheduleTask == null;
    }

    @Override
    public synchronized boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (mScheduleTask == null)
            return true;
        try {
            mScheduleTask.get(timeout, unit);
        } catch (TimeoutException e) {
            return false;
        } catch (ExecutionException ignored) {
        }
        return true;
    }
}
