// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import io.github.muntashirakon.AppManager.server.common.IRootServiceManager;

import static io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_START_DAEMON;
import static io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_START_SERVICE;
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
 * args[2]: CMDLINE_START_SERVICE, CMDLINE_START_DAEMON, or CMDLINE_STOP_SERVICE
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
    private static Context getSystemContext() {
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

    @SuppressWarnings({"DataFlowIssue", "JavaReflectionMemberAccess"})
    private static Context createPackageContextAsUser(String packageName, int userId, int flags)
            throws PackageManager.NameNotFoundException {
        Context systemContext = getSystemContext();
        try {
            UserHandle userHandle = (UserHandle) UserHandle.class
                    .getDeclaredMethod("of", int.class).invoke(null, userId);
            return (Context) systemContext.getClass()
                    .getDeclaredMethod("createPackageContextAsUser",
                            String.class, int.class, UserHandle.class)
                    .invoke(systemContext, packageName, flags, userHandle);
        } catch (Throwable e) {
            Log.w("IPC", "Failed to create package context as user: " + userId, e);
            return systemContext.createPackageContext(packageName, flags);
        }
    }

    private static boolean allowBinderCommunication() {
        try {
            Class<?> SELinuxClass = Class.forName("android.os.SELinux");
            Method getContext = SELinuxClass.getMethod("getContext");
            String context = (String) getContext.invoke(null);
            Method checkSELinuxAccess = SELinuxClass.getMethod("checkSELinuxAccess", String.class, String.class, String.class, String.class);
            return Boolean.TRUE.equals(checkSELinuxAccess.invoke(null, "u:r:untrusted_app:s0", context, "binder", "call"))
                    && Boolean.TRUE.equals(checkSELinuxAccess.invoke(null, "u:r:untrusted_app:s0", context, "binder", "transfer"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        // Close STDOUT/STDERR since it belongs to the parent shell
        System.out.close();
        System.err.close();
        if (args.length < 3) {
            System.exit(1);
        }

        Looper.prepareMainLooper();

        try {
            new RootServiceMain(args);
        } catch (Throwable e) {
            Log.e("IPC", "Error in IPCMain", e);
            System.exit(1);
        }

        // Main thread event loop
        Looper.loop();
        System.exit(1);
    }

    private final int uid;
    private final boolean isDaemon;

    @Override
    public Object[] call() {
        Object[] objs = new Object[2];
        objs[0] = uid;
        objs[1] = isDaemon;
        return objs;
    }

    @SuppressLint("DiscouragedPrivateApi")
    public RootServiceMain(String[] args) throws Exception {
        super(null);

        if (Process.myUid() == 0 && !allowBinderCommunication()) {
            throw new IOException("Current su does not allow Binder communication.");
        }

        ComponentName name = ComponentName.unflattenFromString(args[0]);
        uid = Integer.parseInt(args[1]);
        String action = args[2];
        boolean stop = false;

        switch (action) {
            case CMDLINE_STOP_SERVICE:
                stop = true;
                // fallthrough
            case CMDLINE_START_DAEMON:
                isDaemon = true;
                break;
            case CMDLINE_START_SERVICE:
                isDaemon = false;
                break;
            default:
                throw new IllegalArgumentException("Unknown action: " + action);
        }

        if (isDaemon) daemon: try {
            // Get existing daemon process
            Object binder = getService.invoke(null, getServiceName(name.getPackageName()));
            IRootServiceManager m = IRootServiceManager.Stub.asInterface((IBinder) binder);
            if (m == null)
                break daemon;

            if (stop) {
                m.stop(name, uid);
            } else {
                m.broadcast(uid);
                // Terminate process if broadcast went through without exception
                System.exit(0);
            }
        } catch (RemoteException ignored) {
        } finally {
            if (stop)
                System.exit(0);
        }

        // Calling createPackageContext crashes on LG ROM
        // Override the system resources object to prevent crashing
        Resources systemRes = Resources.getSystem();
        Field systemResField = null;
        try {
            // This class only exists on LG ROMs with broken implementations
            Class.forName("com.lge.systemservice.core.integrity.IntegrityManager");
            // If control flow goes here, we need the resource hack
            Resources wrapper = new ResourcesWrapper(systemRes);
            systemResField = Resources.class.getDeclaredField("mSystem");
            systemResField.setAccessible(true);
            systemResField.set(null, wrapper);
        } catch (ReflectiveOperationException ignored) {}
        int userId = uid / 100_000;
        int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
        Context context = createPackageContextAsUser(name.getPackageName(), userId, flags);
        attachBaseContext(context);

        // Use classloader from the package context to run everything
        ClassLoader cl = context.getClassLoader();

        // Restore the system resources object after classloader is available
        if (systemResField != null) {
            try {
                systemResField.set(null, systemRes);
            } catch (ReflectiveOperationException ignored) {}
        }

        Class<?> clz = cl.loadClass(name.getClassName());
        Constructor<?> ctor = clz.getDeclaredConstructor();
        ctor.setAccessible(true);
        attachBaseContext.invoke(ctor.newInstance(), this);
    }

    private static class ResourcesWrapper extends Resources {
        @SuppressWarnings({"JavaReflectionMemberAccess", "deprecation"})
        @SuppressLint("DiscouragedPrivateApi")
        public ResourcesWrapper(Resources res) throws ReflectiveOperationException {
            super(res.getAssets(), res.getDisplayMetrics(), res.getConfiguration());
            Method getImpl = Resources.class.getDeclaredMethod("getImpl");
            getImpl.setAccessible(true);
            Method setImpl = Resources.class.getDeclaredMethod("setImpl", getImpl.getReturnType());
            setImpl.setAccessible(true);
            Object impl = getImpl.invoke(res);
            setImpl.invoke(this, impl);
        }

        @Override
        public boolean getBoolean(int id) {
            try {
                return super.getBoolean(id);
            } catch (NotFoundException e) {
                return false;
            }
        }
    }
}
