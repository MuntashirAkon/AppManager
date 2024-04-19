// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.ipc;

import static io.github.muntashirakon.AppManager.ipc.RootService.CATEGORY_DAEMON_MODE;
import static io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_START_DAEMON;
import static io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_START_SERVICE;
import static io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_STOP_SERVICE;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.PACKAGE_STAGING_DIRECTORY;

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
import androidx.core.content.ContextCompat;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;
import com.topjohnwu.superuser.internal.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.server.common.IRootServiceManager;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

/**
 * Runs in the non-root (client) process.
 * <p>
 * Starts the root process and manages connections with the remote process.
 */
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
    private static final String RECEIVER_BROADCAST = BuildConfig.APPLICATION_ID + ".RECEIVER_BROADCAST";
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
    private static final int RECEIVER_REGISTERED = 1 << 2;

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
        return new Intent(RECEIVER_BROADCAST)
                .setPackage(ContextUtils.rootContext.getPackageName())
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
    private static ServiceKey parseIntent(Intent intent) {
        ComponentName name = intent.getComponent();
        if (name == null) {
            throw new IllegalArgumentException("The intent does not have a component set");
        }
        if (!name.getPackageName().equals(ContextUtils.getContext().getPackageName())) {
            throw new IllegalArgumentException("RootServices outside of the app are not supported");
        }
        return new ServiceKey(name, intent.hasCategory(CATEGORY_DAEMON_MODE));
    }

    private RemoteProcess mRemote;
    private RemoteProcess mDaemon;

    private int mFlags = 0;

    private final List<BindTask> mPendingTasks = new ArrayList<>();
    private final Map<ServiceKey, RemoteServiceRecord> mServices = new ArrayMap<>();
    private final Map<ServiceConnection, ConnectionRecord> mConnections = new ArrayMap<>();

    private RootServiceManager() {
    }

    @SuppressLint("RestrictedApi")
    private Shell.Task startRootProcess(ComponentName name, String action) {
        Context context = ContextUtils.getContext();

        if (Utils.hasStartupAgents(context)) {
            Log.e(TAG, JVMTI_ERROR);
        }

        if ((mFlags & RECEIVER_REGISTERED) == 0) {
            // Register receiver to receive binder from root process
            IntentFilter filter = new IntentFilter(RECEIVER_BROADCAST);
            // Guard the receiver behind permission UPDATE_APP_OPS_STATS. This permission
            // is not obtainable by normal apps, making the receiver effectively non-exported,
            // but will allow any root/ADB/system process to send broadcast message.
            ContextCompat.registerReceiver(context, new ServiceReceiver(), filter,
                    ManifestCompat.permission.UPDATE_APP_OPS_STATS, null, ContextCompat.RECEIVER_EXPORTED);
            mFlags |= RECEIVER_REGISTERED;
        }

        return (stdin, stdout, stderr) -> {
            Context ctx = ContextUtils.getContext();
            Context de = ContextUtils.getDeContext(ctx);
            File mainJar;
            try {
                mainJar = new File(FileUtils.getExternalCachePath(de), "main.jar");
            } catch (IOException e) {
                throw new IllegalStateException("External directory unavailable.", e);
            }
            File stagingMainJar = new File(PACKAGE_STAGING_DIRECTORY, "main.jar");
            // Dump main.jar as trampoline
            try (InputStream in = context.getResources().getAssets().open("main.jar");
                 OutputStream out = new FileOutputStream(mainJar)) {
                Utils.pump(in, out);
            }
            FileUtils.chmod644(mainJar);

            StringBuilder env = new StringBuilder();
            String params = getParams(env);

            // Classpath
            env.append(CLASSPATH_ENV + "=").append(Ops.isSystem() ? mainJar : stagingMainJar).append(" ");

            String cmd = getRunnerScript(env.toString(), mainJar, stagingMainJar, name, action, params);
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

    @SuppressLint("RestrictedApi")
    @NonNull
    private static String getParams(StringBuilder env) {
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

        // Disable image dex2oat as it can be quite slow in some ROMs if triggered
        return params + " -Xnoimage-dex2oat";
    }

    @NonNull
    private String getRunnerScript(@NonNull String env,
                                   @NonNull File mainJar,
                                   @NonNull File stagingMainJar,
                                   @NonNull ComponentName serviceName,
                                   @NonNull String action,
                                   @NonNull String debugParams) {
        // We cannot readlink /proc/self/exe on old kernels
        @SuppressLint("RestrictedApi")
        String execFile = "/system/bin/app_process" + (Utils.isProcess64Bit() ? "64" : "32");
        String packageStagingCommand;
        if (!Ops.isSystem()) {
            // Use package staging directory
            packageStagingCommand = PackageUtils.ensurePackageStagingDirectoryCommand() +
                    // Copy to main.jar to package staging directory
                    String.format(Locale.ROOT, " && cp %s %s && ", mainJar, PACKAGE_STAGING_DIRECTORY) +
                    // Change permission of the main.jar
                    String.format(Locale.ROOT, "chmod 755 %s && chown shell:shell %s && ", stagingMainJar, stagingMainJar);
        } else {
            // System can't use package staging directory
            packageStagingCommand = "";
        }
        return (packageStagingCommand +
                String.format(Locale.ROOT, "(%s %s %s /system/bin %s %s '%s' %d %s 2>&1)&",
                        env,                            // Environments
                        execFile,                       // Executable
                        debugParams,                    // Debug parameters
                        getNiceNameArg(action),         // Process name
                        IPCMAIN_CLASSNAME,              // Java command
                        serviceName.flattenToString(),  // args[0]
                        Process.myUid(),                // args[1]
                        action));                       // args[2]
    }

    @NonNull
    private String getNiceNameArg(@NonNull String action) {
        switch (action) {
            case CMDLINE_START_SERVICE:
                return String.format(Locale.ROOT, "--nice-name=%s:priv:%d",
                        BuildConfig.APPLICATION_ID, Process.myUid() / 100000);
            case CMDLINE_START_DAEMON:
                return "--nice-name=" + BuildConfig.APPLICATION_ID + ":priv:daemon";
            default:
                return "";
        }
    }

    // Returns null if binding is done synchronously, or else return key
    private ServiceKey bindInternal(Intent intent, Executor executor, ServiceConnection conn) {
        enforceMainThread();

        // Local cache
        ServiceKey key = parseIntent(intent);
        RemoteServiceRecord s = mServices.get(key);
        if (s != null) {
            mConnections.put(conn, new ConnectionRecord(s, executor));
            s.refCount++;
            IBinder binder = s.binder;
            executor.execute(() -> conn.onServiceConnected(key.getName(), binder));
            return null;
        }

        RemoteProcess p = key.isDaemon() ? mDaemon : mRemote;
        if (p == null)
            return key;

        try {
            IBinder binder = p.mgr.bind(intent);
            if (binder != null) {
                s = new RemoteServiceRecord(key, binder, p);
                mConnections.put(conn, new ConnectionRecord(s, executor));
                mServices.put(key, s);
                executor.execute(() -> conn.onServiceConnected(key.getName(), binder));
            } else if (Build.VERSION.SDK_INT >= 28) {
                executor.execute(() -> conn.onNullBinding(key.getName()));
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.getMessage(), e);
            p.binderDied();
            return key;
        }

        return null;
    }

    public Shell.Task createBindTask(Intent intent, Executor executor, ServiceConnection conn) {
        ServiceKey key = bindInternal(intent, executor, conn);
        if (key != null) {
            mPendingTasks.add(() -> bindInternal(intent, executor, conn) == null);
            int mask = key.isDaemon() ? DAEMON_EN_ROUTE : REMOTE_EN_ROUTE;
            if ((mFlags & mask) == 0) {
                mFlags |= mask;
                String action = key.isDaemon() ? CMDLINE_START_DAEMON : CMDLINE_START_SERVICE;
                return startRootProcess(key.getName(), action);
            }
        }
        return null;
    }

    public void unbind(@NonNull ServiceConnection conn) {
        enforceMainThread();

        ConnectionRecord r = mConnections.remove(conn);
        if (r != null) {
            RemoteServiceRecord s = r.getService();
            s.refCount--;
            if (s.refCount == 0) {
                // Actually close the service
                mServices.remove(s.key);
                try {
                    s.host.mgr.unbind(s.key.getName());
                } catch (RemoteException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    }

    private void dropConnections(Predicate predicate) {
        Iterator<Map.Entry<ServiceConnection, ConnectionRecord>> it = mConnections.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ServiceConnection, ConnectionRecord> e = it.next();
            ConnectionRecord r = e.getValue();
            if (predicate.eval(r.getService())) {
                r.disconnect(e.getKey());
                it.remove();
            }
        }
    }

    private void onServiceStopped(ServiceKey key) {
        RemoteServiceRecord s = mServices.remove(key);
        if (s != null)
            dropConnections(s::equals);
    }

    public Shell.Task createStopTask(Intent intent) {
        enforceMainThread();

        ServiceKey key = parseIntent(intent);
        RemoteProcess p = key.isDaemon() ? mDaemon : mRemote;
        if (p == null) {
            if (key.isDaemon()) {
                // Start a new root process to stop daemon
                return startRootProcess(key.getName(), CMDLINE_STOP_SERVICE);
            }
            return null;
        }

        try {
            p.mgr.stop(key.getName(), -1);
        } catch (RemoteException e) {
            Log.e(TAG, e.getMessage(), e);
        }

        onServiceStopped(key);
        return null;
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        if (msg.what == MSG_STOP) {
            onServiceStopped(new ServiceKey((ComponentName) msg.obj, msg.arg1 != 0));
        }
        return false;
    }

    private static class ServiceKey extends Pair<ComponentName, Boolean> {
        ServiceKey(ComponentName name, boolean isDaemon) {
            super(name, isDaemon);
        }

        ComponentName getName() {
            return first;
        }

        boolean isDaemon() {
            return second;
        }
    }

    private static class ConnectionRecord extends Pair<RemoteServiceRecord, Executor> {
        ConnectionRecord(RemoteServiceRecord s, Executor e) {
            super(s, e);
        }

        RemoteServiceRecord getService() {
            return first;
        }

        void disconnect(ServiceConnection conn) {
            second.execute(() -> conn.onServiceDisconnected(first.key.getName()));
        }
    }

    private class RemoteProcess extends BinderHolder {

        final IRootServiceManager mgr;

        RemoteProcess(IRootServiceManager s) throws RemoteException {
            super(s.asBinder());
            mgr = s;
        }

        @Override
        protected void onBinderDied() {
            if (mRemote == this)
                mRemote = null;
            if (mDaemon == this)
                mDaemon = null;

            Iterator<RemoteServiceRecord> sit = mServices.values().iterator();
            while (sit.hasNext()) {
                if (sit.next().host == this) {
                    sit.remove();
                }
            }
            dropConnections(s -> s.host == this);
        }
    }

    private static class RemoteServiceRecord {
        final ServiceKey key;
        final IBinder binder;
        final RemoteProcess host;
        int refCount = 1;

        RemoteServiceRecord(ServiceKey key, IBinder binder, RemoteProcess host) {
            this.key = key;
            this.binder = binder;
            this.host = host;
        }
    }

    private class ServiceReceiver extends BroadcastReceiver {

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

            IRootServiceManager mgr = IRootServiceManager.Stub.asInterface(binder);
            try {
                mgr.connect(m.getBinder());
                RemoteProcess p = new RemoteProcess(mgr);
                if (intent.getBooleanExtra(INTENT_DAEMON_KEY, false)) {
                    mDaemon = p;
                    mFlags &= ~DAEMON_EN_ROUTE;
                } else {
                    mRemote = p;
                    mFlags &= ~REMOTE_EN_ROUTE;
                }
                for (int i = mPendingTasks.size() - 1; i >= 0; --i) {
                    if (mPendingTasks.get(i).run()) {
                        mPendingTasks.remove(i);
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    private interface BindTask {
        boolean run();
    }

    private interface Predicate {
        boolean eval(RemoteServiceRecord s);
    }
}