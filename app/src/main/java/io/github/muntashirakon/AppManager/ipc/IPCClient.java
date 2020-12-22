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
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.server.common.IRootIPC;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.IOUtils;

import static io.github.muntashirakon.AppManager.ipc.RootService.TAG;
import static io.github.muntashirakon.AppManager.ipc.RootService.serialExecutor;
import static io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_STOP_SERVER;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.PACKAGE_STAGING_DIRECTORY;

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

    IPCClient(Intent intent) throws InterruptedException, RemoteException, IOException {
        name = intent.getComponent();
        startRootServer(Utils.getContext(), intent);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("SetWorldReadable")
    static File dumpMainJar(Context context) throws IOException {
        File mainJar = new File(context.getExternalFilesDir(null), "main.jar");
        if (!mainJar.exists()) {
            mainJar.createNewFile();
            mainJar.setReadable(true, false);
            mainJar.setExecutable(true, false);
        }
        try (InputStream in = context.getResources().getAssets().open("main.jar");
             OutputStream out = new FileOutputStream(mainJar)) {
            IOUtils.copy(in, out);
        }
        return mainJar;
    }

    static void stopRootServer(ComponentName name) throws IOException {
        String cmd = getRunnerScript(Utils.getContext(), name, CMDLINE_STOP_SERVER, "");
        if (AppPref.isRootEnabled()) Runner.runCommand(Runner.getRootInstance(), cmd);
        else if (AppPref.isAdbEnabled()) Runner.runCommand(Runner.getAdbInstance(), cmd);
    }

    private static String getBroadcastAction(ComponentName name) {
        return BROADCAST_ACTION + "/" + name.flattenToString();
    }

    private void startRootServer(Context context, Intent intent)
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

        synchronized (this) {
            Log.e(TAG, "Running service starter script...");
            String cmd = getRunnerScript(Utils.getContext(), name, IPCServer.class.getName(), debugParams);
            if (AppPref.isRootEnabled()) Runner.runCommand(Runner.getRootInstance(), cmd);
            else if (AppPref.isAdbEnabled()) Runner.runCommand(Runner.getAdbInstance(), cmd);
            // Wait for broadcast receiver
            wait();
        }
        server.asBinder().linkToDeath(this, 0);
        binder = server.bind(intent);
    }

    boolean isSameService(Intent intent) {
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

    private static String getRunnerScript(Context context, ComponentName serviceName, String serverClassName, String debugParams) throws IOException {
        File mainJar = dumpMainJar(context);
        File stagingJar = new File(PACKAGE_STAGING_DIRECTORY, "main.jar");
        return (String.format("cp %s %s; ", mainJar, PACKAGE_STAGING_DIRECTORY) +
                String.format("chmod 755 %s; chown shell:shell %s; ", stagingJar, mainJar) +
                String.format("(CLASSPATH=%s /system/bin/app_process %s /system/bin %s %s %s)&",
                        stagingJar, debugParams, IPCMAIN_CLASSNAME, serviceName.flattenToString(),
                        serverClassName)).replace("$", "\\$");
    }

    static Intent getBroadcastIntent(ComponentName name, IRootIPC.Stub ipc) {
        Bundle bundle = new Bundle();
        bundle.putBinder(BUNDLE_BINDER_KEY, ipc);
        return new Intent()
                .setPackage(name.getPackageName())
                .setAction(getBroadcastAction(name))
                .putExtra(INTENT_EXTRA_KEY, bundle);
    }

    class BinderReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            context.unregisterReceiver(this);
            Bundle bundle = intent.getBundleExtra(INTENT_EXTRA_KEY);
            IBinder binder = bundle.getBinder(BUNDLE_BINDER_KEY);
            synchronized (IPCClient.this) {
                server = IRootIPC.Stub.asInterface(binder);
                IPCClient.this.notifyAll();
            }
        }
    }
}
