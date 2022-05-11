// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.util.Log;

// Copyright 2020 John "topjohnwu" Wu
public final class ContextUtils {
    public static final String TAG = ContextUtils.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    public static Context context;

    @SuppressLint({"PrivateApi", "RestrictedApi"})
    public static Context getContext() {
        if (context == null) {
            // Fetching ActivityThread on the main thread is no longer required on API 18+
            // See: https://cs.android.com/android/platform/frameworks/base/+/66a017b63461a22842b3678c9520f803d5ddadfc
            try {
                Context c = (Context) Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication")
                        .invoke(null);
                context = getContextImpl(c);
            } catch (Exception e) {
                // Shall never happen
                Log.e(TAG, e.getMessage(), e);
            }
        }
        return context;
    }

    public static Context getDeContext(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ?
                context.createDeviceProtectedStorageContext() : context;
    }

    public static Context getContextImpl(Context context) {
        while (context instanceof ContextWrapper) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        return context;
    }
}
