// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later
package io.github.muntashirakon.AppManager.server.common;

import android.content.Context;
import android.os.Looper;

import java.lang.reflect.Method;

// Copyright 2020 John "topjohnwu" Wu
// Must be accessed via reflection
public final class ServerUtils {
    public static final String CMDLINE_START_SERVICE = "start";
    public static final String CMDLINE_START_DAEMON = "daemon";
    public static final String CMDLINE_STOP_SERVICE = "stop";

    public static final String CMDLINE_STOP_SERVER = "stopServer";

    public static Context getSystemContext() {
        try {
            synchronized (Looper.class) {
                if (Looper.getMainLooper() == null)
                    Looper.prepareMainLooper();
            }

            Class<?> atClazz = Class.forName("android.app.ActivityThread");
            Method systemMain = atClazz.getMethod("systemMain");
            Object activityThread = systemMain.invoke(null);
            Method getSystemContext = atClazz.getMethod("getSystemContext");
            return (Context) getSystemContext.invoke(activityThread);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Put "app-manager-" in front of the service name to prevent possible conflicts
    public static String getServiceName(String pkg) {
        return "app-manager-" + pkg;
    }
}
