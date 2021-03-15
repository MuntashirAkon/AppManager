/*
 * Copyright (c) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
