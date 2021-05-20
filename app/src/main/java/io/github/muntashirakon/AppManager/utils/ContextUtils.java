// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.annotation.SuppressLint;
import android.app.ActivityThread;
import android.content.Context;
import android.os.Build;

import com.topjohnwu.superuser.internal.UiThreadHandler;

import io.github.muntashirakon.AppManager.logs.Log;

// Copyright 2020 John "topjohnwu" Wu
public final class ContextUtils {

    @SuppressLint("StaticFieldLeak")
    public static Context context;
    private static final String TAG = "IPC";

    public static synchronized Context getContext() {
        if (context == null) {
            UiThreadHandler.runAndWait(() -> {
                try {
                    context = ActivityThread.currentApplication();
                } catch (Exception e) {
                    // Shall never happen
                    Log.e(TAG, e);
                }
            });
        }
        return context;
    }

    public static Context getDeContext(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ?
                context.createDeviceProtectedStorageContext() : context;
    }
}
