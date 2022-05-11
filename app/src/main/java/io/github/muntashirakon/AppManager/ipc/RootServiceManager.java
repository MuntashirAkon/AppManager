// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.ipc;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;
import com.topjohnwu.superuser.internal.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import io.github.muntashirakon.AppManager.server.common.IRootServiceManager;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

import static io.github.muntashirakon.AppManager.ipc.RootService.CATEGORY_DAEMON_MODE;
import static io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_START_DAEMON;
import static io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_START_SERVICE;
import static io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_STOP_SERVICE;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.PACKAGE_STAGING_DIRECTORY;

// Copyright 2021 John "topjohnwu" Wu
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class RootServiceManager implements Handler.Callback {
    static final String TAG = RootServiceManager.class.getSimpleName();
    static final String LOGGING_ENV = "AM_VERBOSE_LOGGING";
    static final String DEBUG_ENV = "AM_DEBUGGER";
    static final String CLASSPATH_ENV = "CLASSPATH";

    static final int MSG_STOP = 1;

    private static final String BUNDLE_BINDER_KEY = "binder";
    private static final String INTENT_BUNDLE_KEY = "extra.bundle";
    private static final String INTENT_DAEMON_KEY = "extra.daemon";
    private static final String API_27_DEBUG =
            "-Xrunjdwp:transport=dt_android_adb,suspend=n,server=y " +
                    "-Xcompiler-option --debuggable";
    private static final String API_28_DEBUG =
            "-XjdwpProvider:adbconnection -XjdwpOptions:suspend=n,server=y " +
                    "-Xcompiler-option --debuggable";
    private static final String JVMTI_ERROR = " \n" +
            "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
            "! Warning: JVMTI agent is enabled. Please enable the !\n" +
            "! 'Always install with package manager' option in    !\n" +
            "! Android Studio. For more details and information,  !\n" +
            "! check out RootService's Javadoc.                   !\n" +
            "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n";

    private static final int REMOTE_EN_ROUTE = 1 << 0;
    private static final int DAEMON_EN_ROUTE = 1 << 1;

    private static final String IPCMAIN_CLASSNAME = "io.github.muntashirakon.AppManager.server.RootServiceMain";

    private static RootServiceManager sInstance;

    public static RootServiceManager getInstance() {
        if (sInstance == null) {
            sInstance = new RootServiceManager();
        }
        return sInstance;
    }

    @SuppressLint("WrongConstant")
    static Intent getBroadcastIntent(IBinder binder, boolean isDaemon) {
        Bundle bundle = new Bundle();
        bundle.putBinder(BUNDLE_BINDER_KEY, binder);
        return new Intent()
                .setPackage(ContextUtils.context.getPackageName())
                .addFlags(HiddenAPIs.FLAG_RECEIVER_FROM_SHELL)
                .putExtra(INTENT_DAEMON_KEY, isDaemon)
                .putExtra(INTENT_BUNDLE_KEY, bundle);
    }

    private static void enforceMainThread() {
        if (!ShellUtils.onMainThread()) {
            throw new IllegalStateException("This method can only be called on the main thread");
        }
    }

    @NonNull
    private static Pair<ComponentName, Boolean> enforceIntent(Intent intent) {
        ComponentName name = intent.getComponent();
        if (name == null) {
            throw new IllegalArgumentException("The intent does not have a component set");
        }
        if (!name.getPackageName().equals(ContextUtils.getContext().getPackageName())) {
            throw new IllegalArgumentException("RootServices outside of the app are not supported");
        }
        return new Pair<>(name, intent.hasCategory(CATEGORY_DAEMON_MODE));
    }

    private static void notifyDisconnection(Map.Entry<ServiceConnection, Pair<RemoteService, Executor>> e) {
        ServiceConnection c = e.getKey();
        ComponentName name = e.getValue().first.key.first;
        e.getValue().second.execute(() -> c.onServiceDisconnected(name));
    }

    private RemoteProcess mRemote;
    private RemoteProcess mDaemon;

    private String filterAction;
    private int flags = 0;

    private final List<BindTask> pendingTasks = new ArrayList<>();
    private final Map<Pair<ComponentName, Boolean>, RemoteService> services = new ArrayMap<>();
    private final Map<ServiceConnection, Pair<RemoteService, Executor>> connections = new ArrayMap<>();

    private RootServiceManager() {
    }

    @SuppressLint("RestrictedApi")
    private Shell.Task startRootProcess(ComponentName name, String action) {
        Context context = ContextUtils.getContext();

        if (Utils.hasStartupAgents(context)) {
            Log.e(TAG, JVMTI_ERROR);
        }

        if (filterAction == null) {
            filterAction = UUID.randomUUID().toString();
            // Register receiver to receive binder from root process
            IntentFilter filter = new IntentFilter(filterAction);
            context.registerReceiver(new ServiceReceiver(), filter);
        }

        Context de = ContextUtils.getDeContext(context);
        File mainJar = new File(de.getCacheDir(), "main.jar");
        File stagingMainJar = new File(PACKAGE_STAGING_DIRECTORY, "main.jar");

        StringBuilder env = new StringBuilder();
        String params = "";

        if (Utils.vLog()) {
            env.append(LOGGING_ENV + "=1 ");
        }

        // Only support debugging on SDK >= 27
        if (Build.VERSION.SDK_INT >= 27 && Debug.isDebuggerConnected()) {
            env.append(DEBUG_ENV + "=1 ");
            // Reference of the params to start jdwp:
            // https://developer.android.com/ndk/guides/wrap-script#debugging_when_using_wrapsh
            if (Build.VERSION.SDK_INT == 27) {
                params = API_27_DEBUG;
            } else {
                params = API_28_DEBUG;
            }
        }

        // Classpath
        env.append(CLASSPATH_ENV + "=").append(stagingMainJar).append(" ");

        String cmd = getRunnerScript(env.toString(), mainJar, stagingMainJar, name, action, params);

        return (stdin, stdout, stderr) -> {
            // Dump main.jar as trampoline
            try (InputStream in = context.getResources().getAssets().open("main.jar");
                 OutputStream out = new FileOutputStream(mainJar)) {
                Utils.pump(in, out);
            }
            FileUtils.chmod644(mainJar);
            Log.d(TAG, cmd);
            // Write command to stdin
            byte[] bytes = cmd.getBytes(StandardCharsets.UTF_8);
            stdin.write(bytes);
            stdin.write('\n');
            stdin.flush();
            // Since all output for the command is redirected to /dev/null and
            // the command runs in the background, we don't need to wait and
            // can just return.
        };
    }

    @NonNull
    private String getRunnerScript(@NonNull String env,
                                   @NonNull File mainJar,
                                   @NonNull File stagingMainJar,
                                   @NonNull ComponentName serviceName,
                                   @NonNull String action,
                                   @NonNull String debugParams) {
        // ADB cannot access `exe` of another process
        String execFile = Ops.isAdb() ? "/system/bin/app_process" : ("/proc/" + Process.myPid() + "/exe");
        return (PackageUtils.ensurePackageStagingDirectoryCommand() +
                String.format(Locale.ROOT, " && cp %s %s && ", mainJar, PACKAGE_STAGING_DIRECTORY) +
                String.format(Locale.ROOT, "chmod 755 %s && chown shell:shell %s && ", stagingMainJar, stagingMainJar) +
                String.format(Locale.ROOT, "(%s %s %s /system/bin --nice-name=%s:root %s %s %d %s %s)&",
                        env,                                        // Environments
                        execFile,                                   // Executable
                        debugParams,                                // Debug parameters
                        ContextUtils.getContext().getPackageName(), // Process name
                        IPCMAIN_CLASSNAME,                          // Java command
                        serviceName.flattenToString().replace("$", "\\$"), // args[0]
                        Process.myUid(),                            // args[1]
                        filterAction,                               // args[2]
                        action));                                   // args[3]
    }

    // Returns null if binding is done synchronously, or else return key
    private Pair<ComponentName, Boolean> bindInternal(
            Intent intent, Executor executor, ServiceConnection conn) {
        enforceMainThread();

        // Local cache
        Pair<ComponentName, Boolean> key = enforceIntent(intent);
        RemoteService s = services.get(key);
        if (s != null) {
            connections.put(conn, new Pair<>(s, executor));
            s.refCount++;
            executor.execute(() -> conn.onServiceConnected(key.first, s.binder));
            return null;
        }

        RemoteProcess p = key.second ? mDaemon : mRemote;
        if (p == null)
            return key;

        try {
            IBinder binder = p.sm.bind(intent);
            if (binder != null) {
                RemoteService r = new RemoteService(key, binder, p);
                connections.put(conn, new Pair<>(r, executor));
                services.put(key, r);
                executor.execute(() -> conn.onServiceConnected(key.first, binder));
            } else if (Build.VERSION.SDK_INT >= 28) {
                executor.execute(() -> conn.onNullBinding(key.first));
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.getMessage(), e);
            p.binderDied();
            return key;
        }

        return null;
    }

    public Shell.Task createBindTask(Intent intent, Executor executor, ServiceConnection conn) {
        Pair<ComponentName, Boolean> key = bindInternal(intent, executor, conn);
        if (key != null) {
            pendingTasks.add(() -> bindInternal(intent, executor, conn) == null);
            int mask = key.second ? DAEMON_EN_ROUTE : REMOTE_EN_ROUTE;
            String action = key.second ? CMDLINE_START_DAEMON : CMDLINE_START_SERVICE;
            if ((flags & mask) == 0) {
                flags |= mask;
                return startRootProcess(key.first, action);
            }
        }
        return null;
    }

    public void unbind(@NonNull ServiceConnection conn) {
        enforceMainThread();

        Pair<RemoteService, Executor> p = connections.remove(conn);
        if (p != null) {
            p.first.refCount--;
            p.second.execute(() -> conn.onServiceDisconnected(p.first.key.first));
            if (p.first.refCount == 0) {
                // Actually close the service
                services.remove(p.first.key);
                try {
                    p.first.host.sm.unbind(p.first.key.first);
                } catch (RemoteException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    }

    private void stopInternal(Pair<ComponentName, Boolean> key) {
        RemoteService s = services.remove(key);
        if (s == null)
            return;

        // Notify all connections
        Iterator<Map.Entry<ServiceConnection, Pair<RemoteService, Executor>>> it =
                connections.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ServiceConnection, Pair<RemoteService, Executor>> e = it.next();
            if (e.getValue().first.equals(s)) {
                notifyDisconnection(e);
                it.remove();
            }
        }
    }

    public Shell.Task createStopTask(Intent intent) {
        enforceMainThread();

        Pair<ComponentName, Boolean> key = enforceIntent(intent);
        RemoteProcess p = key.second ? mDaemon : mRemote;
        if (p == null) {
            if (key.second) {
                // Start a new root process to stop daemon
                return startRootProcess(key.first, CMDLINE_STOP_SERVICE);
            }
            return null;
        }

        stopInternal(key);
        try {
            p.sm.stop(key.first, -1, null);
        } catch (RemoteException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return null;
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        if (msg.what == MSG_STOP) {
            stopInternal(new Pair<>((ComponentName) msg.obj, msg.arg1 != 0));
        }
        return false;
    }

    class RemoteProcess extends BinderHolder {

        final IRootServiceManager sm;

        RemoteProcess(IRootServiceManager s) throws RemoteException {
            super(s.asBinder());
            sm = s;
        }

        @Override
        protected void onBinderDied() {
            if (mRemote == this)
                mRemote = null;
            if (mDaemon == this)
                mDaemon = null;

            Iterator<RemoteService> sit = services.values().iterator();
            while (sit.hasNext()) {
                if (sit.next().host == this) {
                    sit.remove();
                }
            }

            Iterator<Map.Entry<ServiceConnection, Pair<RemoteService, Executor>>> it =
                    connections.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ServiceConnection, Pair<RemoteService, Executor>> e = it.next();
                if (e.getValue().first.host == this) {
                    notifyDisconnection(e);
                    it.remove();
                }
            }
        }
    }

    class ServiceReceiver extends BroadcastReceiver {

        private final Messenger m;

        ServiceReceiver() {
            // Create messenger to receive service stop notification
            Handler h = new Handler(Looper.getMainLooper(), RootServiceManager.this);
            m = new Messenger(h);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getBundleExtra(INTENT_BUNDLE_KEY);
            if (bundle == null)
                return;
            IBinder binder = bundle.getBinder(BUNDLE_BINDER_KEY);
            if (binder == null)
                return;

            IRootServiceManager sm = IRootServiceManager.Stub.asInterface(binder);
            try {
                sm.connect(m.getBinder());
                RemoteProcess p = new RemoteProcess(sm);
                if (intent.getBooleanExtra(INTENT_DAEMON_KEY, false)) {
                    mDaemon = p;
                    flags &= ~DAEMON_EN_ROUTE;
                } else {
                    mRemote = p;
                    flags &= ~REMOTE_EN_ROUTE;
                }
                for (int i = pendingTasks.size() - 1; i >= 0; --i) {
                    if (pendingTasks.get(i).run()) {
                        pendingTasks.remove(i);
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    static class RemoteService {
        final Pair<ComponentName, Boolean> key;
        final IBinder binder;
        final RemoteProcess host;
        int refCount = 1;

        RemoteService(Pair<ComponentName, Boolean> key, IBinder binder, RemoteProcess host) {
            this.key = key;
            this.binder = binder;
            this.host = host;
        }
    }

    interface BindTask {
        boolean run();
    }
}