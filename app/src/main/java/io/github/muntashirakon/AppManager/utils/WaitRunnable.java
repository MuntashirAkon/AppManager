// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;

// Copyright 2020 John "topjohnwu" Wu
public final class WaitRunnable implements Runnable {

    private Runnable r;

    public WaitRunnable(@NonNull Runnable run) {
        r = run;
    }

    public synchronized void waitUntilDone() {
        while (r != null) {
            try {
                wait();
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    public synchronized void run() {
        r.run();
        r = null;
        notifyAll();
    }
}
