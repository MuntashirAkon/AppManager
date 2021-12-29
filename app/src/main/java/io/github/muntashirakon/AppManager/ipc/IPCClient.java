// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.server.common.IRootIPC;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

import static io.github.muntashirakon.AppManager.ipc.RootService.TAG;
import static io.github.muntashirakon.AppManager.ipc.RootService.serialExecutor;
import static io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_STOP_SERVER;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.PACKAGE_STAGING_DIRECTORY;

// Copyright 2020 John "topjohnwu" Wu
class IPCClient implements IBinder.DeathRecipient, Closeable {
    static final String INTENT_DEBUG_KEY = "debug";
    static final String INTENT_EXTRA_KEY = "binder_bundle";
    static final String BUNDLE_BINDER_KEY = "binder";

    private static final String BROADCAST_ACTION = "com.topjohnwu.superuser.BROADCAST_IPC";
    private static final String IPCMAIN_CLASSNAME = "io.github.muntashirakon.AppManager.server.IPCMain";

    private final ComponentName name;
    private final Map<ServiceConnection, Executor> connections = new HashMap<>();

    private IRootIPC server = null;
    private IBinder binder = null;
    private CountDownLatch broadcastWatcher;

    IPCClient(@NonNull Intent intent) throws InterruptedException, RemoteException, IOException {
        name = intent.getComponent();
        startRootServer(AppManager.getContext(), intent);
    }

    @NonNull
    static File dumpMainJar(@NonNull Context context) throws IOException {
        Context contextDe = ContextUtils.getDeContext(context);
        File internalStorage = contextDe.getFilesDir().getParentFile();
        assert internalStorage != null;
        FileUtils.chmod711(internalStorage);
        File mainJar = new File(internalStorage, "main.jar");
        try (InputStream in = context.getResources().getAssets().open("main.jar");
             OutputStream out = new FileOutputStream(mainJar)) {
            FileUtils.copy(in, out);
        }
        FileUtils.chmod644(mainJar);
        return mainJar;
    }

    static void stopRootServer(ComponentName name) throws IOException, RemoteException {
        String cmd = getRunnerScript(AppManager.getContext(), name, CMDLINE_STOP_SERVER, "");
        if (AppPref.isRootEnabled()) {
            Runner.runCommand(Runner.getRootInstance(), cmd);
        } else if (AppPref.isAdbEnabled()) {
            LocalServer.getInstance().runCommand(cmd);
        }
    }

    @NonNull
    private static String getBroadcastAction(@NonNull ComponentName name) {
        return BROADCAST_ACTION + "/" + name.flattenToString();
    }

    private void startRootServer(@NonNull Context context, @NonNull Intent intent)
            throws IOException, InterruptedException, RemoteException {
        // Register BinderReceiver to receive binder from root process
        IntentFilter filter = new IntentFilter(getBroadcastAction(name));
        context.registerReceiver(new BinderReceiver(), filter);

        // Strip extra and add our own data
        intent = intent.cloneFilter();
        String debugParams;
        if (Debug.isDebuggerConnected()) {
            // Also debug the remote root server
            // Reference of the params to start jdwp:
            // https://developer.android.com/ndk/guides/wrap-script#debugging_when_using_wrapsh
            intent.putExtra(INTENT_DEBUG_KEY, true);
            if (Build.VERSION.SDK_INT < 28) {
                debugParams = "-Xrunjdwp:transport=dt_android_adb,suspend=n,server=y -Xcompiler-option --debuggable";
            } else if (Build.VERSION.SDK_INT == 28) {
                debugParams = "-XjdwpProvider:adbconnection -XjdwpOptions:suspend=n,server=y -Xcompiler-option --debuggable";
            } else {
                debugParams = "-XjdwpProvider:adbconnection";
            }
        } else debugParams = "";

        Bundle bundle = new Bundle();
        bundle.putBinder(BUNDLE_BINDER_KEY, new Binder());
        intent.putExtra(INTENT_EXTRA_KEY, bundle);

        Log.e(TAG, "Running service starter script...");
        broadcastWatcher = new CountDownLatch(1);
        String cmd = getRunnerScript(context, name, IPCServer.class.getName(), debugParams);
        if (AppPref.isRootEnabled()) {
            if (!Runner.runCommand(Runner.getRootInstance(), cmd).isSuccessful()) {
                Log.e(TAG, "Couldn't start service.");
                return;
            }
        } else if (AppPref.isAdbEnabled()) {
            if (LocalServer.getInstance().runCommand(cmd).getStatusCode() != 0) {
                Log.e(TAG, "Couldn't start service.");
                return;
            }
        }
        // Wait for broadcast receiver
        broadcastWatcher.await();
        // Broadcast is received
        server.asBinder().linkToDeath(this, 0);
        binder = server.bind(intent);
    }

    boolean isSameService(@NonNull Intent intent) {
        return name.equals(intent.getComponent());
    }

    void newConnection(ServiceConnection conn, Executor executor) {
        connections.put(conn, executor);
        if (binder != null)
            executor.execute(() -> conn.onServiceConnected(name, binder));
        else if (Build.VERSION.SDK_INT >= 28)
            executor.execute(() -> conn.onNullBinding(name));
    }

    boolean unbind(ServiceConnection conn) {
        Executor executor = connections.remove(conn);
        if (executor != null) {
            executor.execute(() -> conn.onServiceDisconnected(name));
            if (connections.isEmpty()) {
                server.asBinder().unlinkToDeath(this, 0);
                try {
                    server.unbind();
                } catch (RemoteException ignored) {
                }
                server = null;
                binder = null;
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        server.asBinder().unlinkToDeath(this, 0);
        server = null;
        binder = null;
        for (Map.Entry<ServiceConnection, Executor> entry : connections.entrySet()) {
            ServiceConnection conn = entry.getKey();
            entry.getValue().execute(() -> conn.onServiceDisconnected(name));
        }
        connections.clear();
    }

    void stopService() {
        try {
            server.stop();
        } catch (RemoteException ignored) {
        }
        close();
    }

    @Override
    public void binderDied() {
        serialExecutor.execute(() -> RootService.bound.remove(this));
        close();
    }

    @NonNull
    private static String getRunnerScript(@NonNull Context context,
                                          @NonNull ComponentName serviceName,
                                          @NonNull String serverClassName,
                                          @NonNull String debugParams)
            throws IOException {
        File mainJar = dumpMainJar(context);
        File stagingJar = new File(PACKAGE_STAGING_DIRECTORY, "main.jar");
        return (PackageUtils.ensurePackageStagingDirectoryCommand() +
                String.format(" && cp %s %s && ", mainJar, PACKAGE_STAGING_DIRECTORY) +
                String.format("chmod 755 %s && chown shell:shell %s && ", stagingJar, stagingJar) +
                String.format("(CLASSPATH=%s /system/bin/app_process %s /system/bin %s %s %s)&",
                        stagingJar, debugParams, IPCMAIN_CLASSNAME, serviceName.flattenToString(),
                        serverClassName)).replace("$", "\\$");
    }

    static Intent getBroadcastIntent(@NonNull ComponentName name, @NonNull IRootIPC.Stub ipc) {
        Bundle bundle = new Bundle();
        bundle.putBinder(BUNDLE_BINDER_KEY, ipc);
        return new Intent()
                .setPackage(name.getPackageName())
                .setAction(getBroadcastAction(name))
                .putExtra(INTENT_EXTRA_KEY, bundle);
    }

    class BinderReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(@NonNull Context context, @NonNull Intent intent) {
            context.unregisterReceiver(this);
            Bundle bundle = intent.getBundleExtra(INTENT_EXTRA_KEY);
            IBinder binder = bundle.getBinder(BUNDLE_BINDER_KEY);
            server = IRootIPC.Stub.asInterface(binder);
            broadcastWatcher.countDown();
        }
    }
}
