// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

import java.util.Objects;

// Copyright 2020 John "topjohnwu" Wu
public final class ContextUtils {
    public static final String TAG = ContextUtils.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    public static Context rootContext;
    @SuppressLint("StaticFieldLeak")
    private static Context sContext;

    @SuppressLint({"PrivateApi", "RestrictedApi"})
    @NonNull
    public static Context getContext() {
        if (sContext == null) {
            // Fetching ActivityThread on the main thread is no longer required on API 18+
            // See: https://cs.android.com/android/platform/frameworks/base/+/66a017b63461a22842b3678c9520f803d5ddadfc
            try {
                Context c = (Context) Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication")
                        .invoke(null);
                sContext = getContextImpl(Objects.requireNonNull(c));
            } catch (Exception e) {
                // Shall never happen
                throw new RuntimeException(e);
            }
        }
        return sContext;
    }

    @Contract("!null -> !null")
    public static Context getDeContext(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ?
                context.createDeviceProtectedStorageContext() : context;
    }

    @Contract("!null -> !null")
    @Nullable
    public static Context getContextImpl(@Nullable Context context) {
        while (context instanceof ContextWrapper) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        return context;
    }

    public static void unregisterReceiver(@NonNull Context context, @NonNull BroadcastReceiver receiver) {
        ExUtils.exceptionAsIgnored(() -> context.unregisterReceiver(receiver));
    }
}
