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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Debug;
import android.os.FileObserver;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Constructor;

import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.server.common.IRootIPC;

import static io.github.muntashirakon.AppManager.ipc.IPCClient.BUNDLE_BINDER_KEY;
import static io.github.muntashirakon.AppManager.ipc.IPCClient.INTENT_DEBUG_KEY;
import static io.github.muntashirakon.AppManager.ipc.IPCClient.INTENT_EXTRA_KEY;
import static io.github.muntashirakon.AppManager.ipc.RootService.TAG;
import static io.github.muntashirakon.AppManager.server.common.ServerUtils.getServiceName;

class IPCServer extends IRootIPC.Stub implements IBinder.DeathRecipient {

    private final ComponentName mName;
    private final RootService service;

    @SuppressWarnings("FieldCanBeLocal")
    private final FileObserver observer;  /* A strong reference is required */

    private IBinder mClient;
    private Intent mIntent;

    @SuppressWarnings("unchecked")
    IPCServer(Context context, ComponentName name) throws Exception {
        Utils.context = context;
        IBinder binder = ServiceManager.getService(getServiceName(name));
        if (binder != null) {
            // There was already a root service running
            IRootIPC ipc = IRootIPC.Stub.asInterface(binder);
            try {
                // Trigger re-broadcast
                ipc.broadcast();

                // Our work is done!
                System.exit(0);
            } catch (RemoteException e) {
                // Daemon dead, continue
            }
        }

        mName = name;
        Class<RootService> clz = (Class<RootService>) Class.forName(name.getClassName());
        Constructor<RootService> constructor = clz.getDeclaredConstructor();
        constructor.setAccessible(true);
        service = constructor.newInstance();
        service.attach(context, this);
        service.onCreate();
        observer = new AppObserver(new File(context.getPackageCodePath()));
        observer.startWatching();

        broadcast();

        // Start main thread looper
        Looper.loop();
    }

    class AppObserver extends FileObserver {
        private final String name;

        AppObserver(File path) {
            super(path.getParentFile(), CREATE | DELETE | DELETE_SELF | MOVED_TO | MOVED_FROM);
            Log.d(TAG, "Start monitoring: " + path.getParent());
            name = path.getName();
        }

        @Override
        public void onEvent(int event, @Nullable String path) {
            if (event == DELETE_SELF || name.equals(path)) {
                Log.d(TAG, "Stopping server due to change in code path");
                UiThreadHandler.run(IPCServer.this::stop);
            }
        }
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        // Small trick for stopping the service without going through AIDL
        if (code == LAST_CALL_TRANSACTION - 1) {
            stop();
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }

    @Override
    public void broadcast() {
        Intent broadcast = IPCClient.getBroadcastIntent(mName, this);
        broadcast.addFlags(HiddenAPIs.FLAG_RECEIVER_FROM_SHELL());
        service.sendBroadcast(broadcast);
    }

    @Override
    public synchronized IBinder bind(Intent intent) {
        // ComponentName doesn't match, abort
        if (!mName.equals(intent.getComponent()))
            System.exit(1);

        if (intent.getBooleanExtra(INTENT_DEBUG_KEY, false)) {
            // ActivityThread.attach(true, 0) will set this to system_process
            HiddenAPIs.setAppName(service.getPackageName() + ":root");
            // For some reason Debug.waitForDebugger() won't work, manual spin lock...
            while (!Debug.isDebuggerConnected()) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
            }
        }

        try {
            Bundle bundle = intent.getBundleExtra(INTENT_EXTRA_KEY);
            mClient = bundle.getBinder(BUNDLE_BINDER_KEY);
            mClient.linkToDeath(this, 0);

            Container<IBinder> c = new Container<>();
            UiThreadHandler.runAndWait(() -> {
                if (mIntent != null) {
                    Log.d(TAG, mName + " rebind");
                    service.onRebind(intent);
                } else {
                    Log.d(TAG, mName + " bind");
                    mIntent = intent.cloneFilter();
                }
                c.obj = service.onBind(intent);
            });
            return c.obj;
        } catch (Exception e) {
            Log.e(TAG, null, e);
            return null;
        }
    }

    @Override
    public synchronized void unbind() {
        Log.d(TAG, mName + " unbind");
        mClient.unlinkToDeath(this, 0);
        mClient = null;
        UiThreadHandler.run(() -> {
            if (!service.onUnbind(mIntent)) {
                service.onDestroy();
                System.exit(0);
            } else {
                // Register ourselves as system service
                ServiceManager.addService(getServiceName(mName), this);
            }
        });
    }

    @Override
    public synchronized void stop() {
        Log.d(TAG, mName + " stop");
        if (mClient != null) {
            mClient.unlinkToDeath(this, 0);
            mClient = null;
        }
        UiThreadHandler.run(() -> {
            service.onDestroy();
            System.exit(0);
        });
    }

    @Override
    public void binderDied() {
        Log.d(TAG, "client binderDied");
        unbind();
    }
}
