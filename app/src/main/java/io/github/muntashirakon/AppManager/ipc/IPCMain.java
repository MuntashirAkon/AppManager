/*
 * Copyright 2020 John "topjohnwu" Wu
 * Copyright 2020 Muntashir Al-Islam
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.muntashirakon.AppManager.ipc;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static android.os.IBinder.LAST_CALL_TRANSACTION;

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
 * args[1]: {@link #CMDLINE_STOP_SERVER} or class name of IPCServer
 */
public class IPCMain {

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

    private static void stopRemoteService(ComponentName name) throws Exception {
        @SuppressLint("PrivateApi")
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Method getService = sm.getDeclaredMethod("getService", String.class);
        IBinder binder = (IBinder) getService.invoke(null, getServiceName(name));
        if (binder != null) {
            Parcel p = Parcel.obtain();
            try {
                // IPCServer should be able to handle this correctly
                binder.transact(LAST_CALL_TRANSACTION - 1, p, null, 0);
            } finally {
                p.recycle();
            }
        }
        System.exit(0);
    }

    public static void main(String[] args) {
        // Close STDOUT/STDERR since it belongs to the parent shell
        System.out.close();
        System.err.close();
        if (args.length < 2)
            System.exit(0);

        try {
            ComponentName component = ComponentName.unflattenFromString(args[0]);

            if (args[1].equals(CMDLINE_STOP_SERVER))
                stopRemoteService(component);

            Context systemContext = getSystemContext();
            Context context = systemContext.createPackageContext(component.getPackageName(),
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);

            // Use classloader from the package context to run everything
            ClassLoader cl = context.getClassLoader();
            Class<?> clz = cl.loadClass(args[1]);
            Constructor<?> con = clz.getDeclaredConstructor(Context.class, ComponentName.class);
            con.setAccessible(true);
            con.newInstance(context, component);

            // Shall never return
            System.exit(0);
        } catch (Exception e) {
            Log.e("IPC", "Error in IPCMain", e);
            System.exit(1);
        }
    }
}
