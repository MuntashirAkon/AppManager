// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Messenger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.UiThreadHandler;

/**
 * A remote root service using native Android Binder IPC.
 * <p>
 * This class is almost a complete recreation of a bound service running in a root process.
 * Instead of using the original {@code Context.bindService(...)} methods to start and bind
 * to a service, use the provided static methods {@code RootService.bind(...)}.
 * Because the service will not run in the same process as your application, you have to use either
 * {@link Messenger} or AIDL to define the IPC interface for communication. Please read the
 * official documentations for more details.
 * <p>
 * Even though a {@code RootService} is a {@link Context} of the app package, since we are running
 * in a root environment and the ContextImpl is not constructed in the "normal" way, the
 * functionality of this context is much more limited compared to normal non-root cases. Be aware
 * of this and do not assume all context methods will work, many will result in Exceptions.
 * <p>
 * <strong>Daemon mode:</strong><br>
 * In normal circumstances, the root service process will be destroyed when no components are bound
 * to it (including when the non-root app process is terminated). However, if you'd like to have
 * the root service run independently of the app's lifecycle (aka "Daemon Mode"), override the
 * method {@link #onUnbind(Intent)} and return {@code true}. Similar to normal bound services,
 * subsequent bindings will call the {@link #onRebind(Intent)} method.
 * <p>
 * Unlike normal services, RootService does not have an API similar to
 * {@link Context#startService(Intent)} because root services are strictly bound only.
 * A root service process will be terminated in the following conditions:
 * <ul>
 *     <li>(For non-daemon services) All clients had unbound or terminated</li>
 *     <li>Client called {@link #stop(Intent)}</li>
 *     <li>Root service called {@link #stopSelf()}</li>
 *     <li>The source application is updated/deleted</li>
 * </ul>
 * When the remote root process is killed (could be unexpectedly), or the client explicitly called
 * {@link #unbind(ServiceConnection)}, {@link ServiceConnection#onServiceDisconnected(ComponentName)}
 * will be called, and the library will NOT attempt to automatically restart and bind to the service.
 *
 * @see <a href="https://developer.android.com/guide/components/bound-services">Bound services</a>
 * @see <a href="https://developer.android.com/guide/components/aidl">Android Interface Definition Language (AIDL)</a>
 */
// Copyright 2020 John "topjohnwu" Wu
public abstract class RootService extends ContextWrapper {

    static final String TAG = "IPC";
    static final List<IPCClient> bound = new ArrayList<>();
    static final ExecutorService serialExecutor = new SerialExecutorService();

    /**
     * Connect to a root service, creating if needed.
     *
     * @param intent   identifies the service to connect to.
     * @param executor callbacks on ServiceConnection will be called on this executor.
     * @param conn     receives information as the service is started and stopped.
     * @see Context#bindService(Intent, int, Executor, ServiceConnection)
     */
    public static void bind(
            @NonNull Intent intent,
            @NonNull Executor executor,
            @NonNull ServiceConnection conn) {
        serialExecutor.execute(() -> {
            // If no root/ADB access, don't even bother
            if (!AppPref.isRootOrAdbEnabled())
                return;

            Log.d(TAG, "Total bound services: " + bound.size());
            for (IPCClient client : bound) {
                if (client.isSameService(intent)) {
                    client.newConnection(conn, executor);
                    return;
                }
            }

            Log.d(TAG, "Need to start a new connection.");
            try {
                IPCClient client = new IPCClient(intent);
                client.newConnection(conn, executor);
                bound.add(client);
            } catch (Exception e) {
                Log.e(TAG, e);
            }
        });
    }

    /**
     * Connect to a root service, creating if needed.
     *
     * @param intent identifies the service to connect to.
     * @param conn   receives information as the service is started and stopped.
     * @see Context#bindService(Intent, ServiceConnection, int)
     */
    public static void bind(@NonNull Intent intent, @NonNull ServiceConnection conn) {
        bind(intent, UiThreadHandler.executor, conn);
    }

    /**
     * Disconnect from a root service.
     *
     * @param conn the connection interface previously supplied to {@link #bind(Intent, ServiceConnection)}
     * @see Context#unbindService(ServiceConnection)
     */
    public static void unbind(@NonNull ServiceConnection conn) {
        serialExecutor.execute(() -> {
            Iterator<IPCClient> it = bound.iterator();
            while (it.hasNext()) {
                IPCClient client = it.next();
                if (client.unbind(conn)) {
                    it.remove();
                    break;
                }
            }
        });
    }

    /**
     * Force stop a root service process.
     * <p>
     * Since root services are bound only, unlike {@link Context#stopService(Intent)}, this
     * method is used to immediately stop a root service regardless of its state.
     * Only use this method to stop a daemon root service, for normal root services please use
     * {@link #unbind(ServiceConnection)} instead as this method could potentially end up starting
     * an unnecessary root process to make sure all daemons are cleared up.
     *
     * @param intent identifies the service to stop.
     */
    public static void stop(@NonNull Intent intent) {
        serialExecutor.execute(() -> {
            Iterator<IPCClient> it = bound.iterator();
            while (it.hasNext()) {
                IPCClient client = it.next();
                if (client.isSameService(intent)) {
                    client.stopService();
                    it.remove();
                    return;
                }
            }
            if (!AppPref.isRootOrAdbEnabled())
                return;
            // No bound service of the same component, go through another root process to
            // make sure all daemon is killed
            try {
                IPCClient.stopRootServer(intent.getComponent());
            } catch (IOException e) {
                Log.e(TAG, e);
            }
        });
    }

    private IPCServer mServer;

    public RootService() {
        super(null);
    }

    void attach(Context base, IPCServer server) {
        mServer = server;
        attachBaseContext(base);
    }

    @Override
    public final Context getApplicationContext() {
        // Always return ourselves
        return this;
    }

    /**
     * @see Service#onBind(Intent)
     */
    abstract public IBinder onBind(@NonNull Intent intent);

    /**
     * @see Service#onCreate()
     */
    public void onCreate() {
    }

    /**
     * @see Service#onUnbind(Intent)
     */
    public boolean onUnbind(@NonNull Intent intent) {
        return false;
    }

    /**
     * @see Service#onRebind(Intent)
     */
    public void onRebind(@NonNull Intent intent) {
    }

    /**
     * @see Service#onDestroy()
     */
    public void onDestroy() {
    }

    /**
     * Force stop this root service process.
     * <p>
     * This is the same as calling {@link #stop(Intent)} for this particular service.
     */
    public final void stopSelf() {
        mServer.stop();
    }
}
