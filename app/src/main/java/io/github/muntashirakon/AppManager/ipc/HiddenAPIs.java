// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.ipc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * All hidden Android framework APIs used here are very stable.
 * <p>
 * These methods should only be accessed in the root process, since under normal circumstances
 * accessing these internal APIs through reflection will be blocked.
 */
// Copyright 2020 John "topjohnwu" Wu
@SuppressLint({"PrivateApi,DiscouragedPrivateApi,SoonBlockedPrivateApi", "RestrictedApi"})
class HiddenAPIs {
    public static final String TAG = HiddenAPIs.class.getSimpleName();

    private static Method addService;
    private static Method attachBaseContext;
    private static Method setAppName;

    // Set this flag to silence AMS's complaints. Only exist on Android 8.0+
    public static final int FLAG_RECEIVER_FROM_SHELL = Build.VERSION.SDK_INT >= 26 ? 0x00400000 : 0;

    static {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            if (Build.VERSION.SDK_INT >= 28) {
                try {
                    addService = sm.getDeclaredMethod("addService",
                            String.class, IBinder.class, boolean.class, int.class);
                } catch (NoSuchMethodException ignored) {
                    // Fallback to the 2 argument version
                }
            }
            if (addService == null) {
                addService = sm.getDeclaredMethod("addService", String.class, IBinder.class);
            }

            attachBaseContext = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
            attachBaseContext.setAccessible(true);

            Class<?> ddm = Class.forName("android.ddm.DdmHandleAppName");
            setAppName = ddm.getDeclaredMethod("setAppName", String.class, int.class);
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    static void setAppName(String name) {
        try {
            setAppName.invoke(null, name, 0);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static void addService(String name, IBinder service) {
        try {
            if (addService.getParameterTypes().length == 4) {
                // Set dumpPriority to 0 so the service cannot be listed
                addService.invoke(null, name, service, false, 0);
            } else {
                addService.invoke(null, name, service);
            }
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    static void attachBaseContext(Object wrapper, Context context) {
        if (wrapper instanceof ContextWrapper) {
            try {
                attachBaseContext.invoke(wrapper, context);
            } catch (ReflectiveOperationException ignored) { /* Impossible */ }
        }
    }
}