// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import io.github.muntashirakon.AppManager.server.common.IRootServiceManager;

import static io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_START_DAEMON;
import static io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_STOP_SERVICE;
import static io.github.muntashirakon.AppManager.server.common.ServerUtils.getServiceName;

/**
 * Trampoline to start a root service.
 * <p>
 * This is the only class included in main.jar as raw resources.
 * The client code will execute this main method in a root shell.
 * <p>
 * This class will get the system context by calling into Android private APIs with reflection, and
 * uses that to create our client package context. The client context will have the full APK loaded,
 * just like it was launched in a non-root environment.
 * <p>
 * Expected command-line args:
 * args[0]: client service component name
 * args[1]: client UID
 * args[2]: client broadcast receiver intent filter
 * args[3]: CMDLINE_START_SERVICE, CMDLINE_START_DAEMON, or CMDLINE_STOP_SERVICE
 * <p>
 * <b>Note:</b> This class is hardcoded in {@code IPCClient#IPCMAIN_CLASSNAME}. Don't change the class name or package
 * path without changing them there.
 */
// Copyright 2020 John "topjohnwu" Wu
public class RootServiceMain extends ContextWrapper implements Callable<Object[]> {
    private static final Method getService;
    private static final Method attachBaseContext;

    static {
        try {
            @SuppressLint("PrivateApi")
            Class<?> sm = Class.forName("android.os.ServiceManager");
            getService = sm.getDeclaredMethod("getService", String.class);
            attachBaseContext = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
            attachBaseContext.setAccessible(true);
        } catch (Exception e) {
            // Shall not happen!
            throw new RuntimeException(e);
        }
    }

    @SuppressLint("PrivateApi")
    static Context getSystemContext() {
        try {
            Class<?> atClazz = Class.forName("android.app.ActivityThread");
            Method systemMain = atClazz.getMethod("systemMain");
            Object activityThread = systemMain.invoke(null);
            Method getSystemContext = atClazz.getMethod("getSystemContext");
            return (Context) getSystemContext.invoke(activityThread);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        // Close STDOUT/STDERR since it belongs to the parent shell
        System.out.close();
        System.err.close();
        if (args.length < 4)
            System.exit(0);

        Looper.prepareMainLooper();

        try {
            new RootServiceMain(args);
        } catch (Exception e) {
            Log.e("IPC", "Error in IPCMain", e);
            System.exit(1);
        }

        // Main thread event loop
        Looper.loop();
        System.exit(0);
    }

    private final int uid;
    private final String filter;
    private final boolean isDaemon;

    @Override
    public Object[] call() {
        Object[] objs = new Object[3];
        objs[0] = uid;
        objs[1] = filter;
        objs[2] = isDaemon;
        return objs;
    }

    public RootServiceMain(String[] args) throws Exception {
        super(null);

        ComponentName name = ComponentName.unflattenFromString(args[0]);
        uid = Integer.parseInt(args[1]);
        filter = args[2];
        String action = args[3];
        boolean stop = false;

        switch (action) {
            case CMDLINE_STOP_SERVICE:
                stop = true;
                // fallthrough
            case CMDLINE_START_DAEMON:
                isDaemon = true;
                break;
            default:
                isDaemon = false;
                break;
        }

        if (isDaemon) daemon: try {
            // Get existing daemon process
            Object binder = getService.invoke(null, getServiceName(name.getPackageName()));
            IRootServiceManager m = IRootServiceManager.Stub.asInterface((IBinder) binder);
            if (m == null)
                break daemon;

            if (stop) {
                m.stop(name, uid, filter);
            } else {
                m.broadcast(uid, filter);
                // Terminate process if broadcast went through without exception
                System.exit(0);
            }
        } catch (RemoteException ignored) {
        } finally {
            if (stop)
                System.exit(0);
        }

        Context systemContext = getSystemContext();
        Context context = systemContext.createPackageContext(name.getPackageName(),
                Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        attachBaseContext(context);

        // Use classloader from the package context to run everything
        ClassLoader cl = context.getClassLoader();
        Class<?> clz = cl.loadClass(name.getClassName());
        Constructor<?> ctor = clz.getDeclaredConstructor();
        ctor.setAccessible(true);
        attachBaseContext.invoke(ctor.newInstance(), this);
    }
}
