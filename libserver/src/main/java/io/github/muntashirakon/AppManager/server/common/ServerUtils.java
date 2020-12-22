package io.github.muntashirakon.AppManager.server.common;

import android.content.ComponentName;
import android.content.Context;
import android.os.Looper;

import java.lang.reflect.Method;

// Must be accessed via reflection
public final class ServerUtils {
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

    // Convert to a valid service name
    public static String getServiceName(ComponentName name) {
        //noinspection RegExpRedundantEscape
        return name.flattenToString().replace("$", ".").replaceAll("[^a-zA-Z0-9\\/._\\-]", "_");
    }
}
