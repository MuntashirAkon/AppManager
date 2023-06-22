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

    private static Method sAddService;
    private static Method sAttachBaseContext;
    private static Method sSetAppName;

    // Set this flag to silence AMS's complaints. Only exist on Android 8.0+
    public static final int FLAG_RECEIVER_FROM_SHELL = Build.VERSION.SDK_INT >= 26 ? 0x00400000 : 0;

    static {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            if (Build.VERSION.SDK_INT >= 28) {
                try {
                    sAddService = sm.getDeclaredMethod("addService",
                            String.class, IBinder.class, boolean.class, int.class);
                } catch (NoSuchMethodException ignored) {
                    // Fallback to the 2 argument version
                }
            }
            if (sAddService == null) {
                sAddService = sm.getDeclaredMethod("addService", String.class, IBinder.class);
            }

            sAttachBaseContext = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
            sAttachBaseContext.setAccessible(true);

            Class<?> ddm = Class.forName("android.ddm.DdmHandleAppName");
            sSetAppName = ddm.getDeclaredMethod("setAppName", String.class, int.class);
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    static void setAppName(String name) {
        try {
            sSetAppName.invoke(null, name, 0);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static void addService(String name, IBinder service) {
        try {
            if (sAddService.getParameterTypes().length == 4) {
                // Set dumpPriority to 0 so the service cannot be listed
                sAddService.invoke(null, name, service, false, 0);
            } else {
                sAddService.invoke(null, name, service);
            }
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    static void attachBaseContext(Object wrapper, Context context) {
        if (wrapper instanceof ContextWrapper) {
            try {
                sAttachBaseContext.invoke(wrapper, context);
            } catch (ReflectiveOperationException ignored) { /* Impossible */ }
        }
    }
}