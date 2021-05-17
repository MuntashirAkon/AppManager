// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.utils;

import android.os.Handler;
import android.os.Looper;

import com.topjohnwu.superuser.ShellUtils;

import java.util.concurrent.Executor;

// Copyright 2020 John "topjohnwu" Wu
public final class UiThreadHandler {
    public static final Handler handler = new Handler(Looper.getMainLooper());
    public static final Executor executor = UiThreadHandler::run;

    public static void run(Runnable r) {
        if (ShellUtils.onMainThread()) {
            r.run();
        } else {
            handler.post(r);
        }
    }

    public static void runAndWait(Runnable r) {
        if (ShellUtils.onMainThread()) {
            r.run();
        } else {
            WaitRunnable wr = new WaitRunnable(r);
            handler.post(wr);
            wr.waitUntilDone();
        }
    }
}
