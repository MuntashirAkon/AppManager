// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Messenger;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.UiThreadHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * A remote root service using native Android Binder IPC.
 * <p>
 * Important: while developing an app with RootServices, modify the run/debug configuration and
 * check the "Always install with package manager" option if testing on Android 11+, or else the
 * code changes will not be reflected after Android Studio's deployment.
 * <p>
 * This class is almost a complete recreation of a bound service running in a root process.
 * Instead of using the original {@code Context.bindService(...)} methods to start and bind
 * to a service, use the provided static methods {@code RootService.bind(...)}.
 * Because the service will not run in the same process as your application, you have to use either
 * {@link Messenger} or AIDL to define the IPC interface for communication. Please read the
 * official documentations for more details.
 * <p>
 * Even though a {@code RootService} is a {@link Context} of your application, the ContextImpl
 * is not constructed in a normal way, so the functionality is much more limited compared
 * to the normal case. Be aware of this and do not expect all context methods to work.
 * <p>
 * All RootServices launched from the same process will run in the same root process.
 * A root service will be destroyed as soon as there are no clients bound to it.
 * This means all services will be destroyed immediately when the client process is terminated.
 * The library will NOT attempt to automatically restart and bind to a service after it was unbound.
 * <p>
 * <strong>Daemon Mode:</strong><br>
 * If you want the service to run in the background independent from the application lifecycle,
 * launch the service in "Daemon Mode". Check the description of {@link #CATEGORY_DAEMON_MODE}
 * for instructions on how to do so.
 * All services running in "Daemon Mode" will run in a daemon process created per-package that
 * is separate from regular root services. This daemon process will be used across application
 * re-launches, and even across different users on the device.
 * A root service running in "Daemon Mode" will be destroyed when any client called
 * {@link #stop(Intent)}, or the root service itself called {@link #stopSelf()}.
 * <p>
 * A root service process, including the daemon process, will terminate under these conditions:
 * <ul>
 *     <li>When the application is updated or deleted</li>
 *     <li>When all services running in the process are destroyed
 *         (after {@link #onDestroy()} is called)</li>
 * </ul>
 *
 * @see <a href="https://developer.android.com/guide/components/bound-services">Bound services</a>
 * @see <a href="https://developer.android.com/guide/components/aidl">Android Interface Definition Language (AIDL)</a>
 */
// Copyright 2020 John "topjohnwu" Wu
public abstract class RootService extends ContextWrapper {
    /**
     * Launch the service in "Daemon Mode".
     * <p>
     * Add this category in the intent passed to {@link #bind(Intent, ServiceConnection)},
     * {@link #bind(Intent, Executor, ServiceConnection)}, or
     * {@link #bindOrTask(Intent, Executor, ServiceConnection)}
     * to have the service launch in "Daemon Mode".
     * This category also has to be added in the intent passed to {@link #stop(Intent)}
     * and {@link #stopOrTask(Intent)} in order to refer to a daemon service instead of
     * a regular root service.
     */
    public static final String CATEGORY_DAEMON_MODE = BuildConfig.APPLICATION_ID + ".DAEMON_MODE";

    static final String TAG = RootService.class.getSimpleName();

    /**
     * Bind to a root service, launching a new root process if needed.
     *
     * @param intent   identifies the service to connect to.
     * @param executor callbacks on ServiceConnection will be called on this executor.
     * @param conn     receives information as the service is started and stopped.
     * @see Context#bindService(Intent, int, Executor, ServiceConnection)
     */
    @MainThread
    public static void bind(
            @NonNull Intent intent,
            @NonNull Executor executor,
            @NonNull ServiceConnection conn) {
        if (!Ops.isPrivileged()) {
            return;
        }
        Shell.Task task = bindOrTask(intent, executor, conn);
        if (task != null) {
            Shell.EXECUTOR.execute(asRunnable(task));
        }
    }

    /**
     * Bind to a root service, launching a new root process if needed.
     *
     * @param intent identifies the service to connect to.
     * @param conn   receives information as the service is started and stopped.
     * @see Context#bindService(Intent, ServiceConnection, int)
     */
    @MainThread
    public static void bind(@NonNull Intent intent, @NonNull ServiceConnection conn) {
        bind(intent, UiThreadHandler.executor, conn);
    }

    /**
     * Bind to a root service, creating a task to launch a new root process if needed.
     * <p>
     * If the application is already connected to a root process, binding will happen immediately
     * and this method will return {@code null}. Or else this method returns a {@link Shell.Task}
     * that has to be executed to launch the root process. Binding will only happen after the
     * developer has executed the returned task with {@link Shell#execTask(Shell.Task)}.
     *
     * @return the task to launch a root process. If there is no need to launch a new root
     * process, {@code null} is returned.
     * @see #bind(Intent, Executor, ServiceConnection)
     */
    @MainThread
    @Nullable
    public static Shell.Task bindOrTask(
            @NonNull Intent intent,
            @NonNull Executor executor,
            @NonNull ServiceConnection conn) {
        return RootServiceManager.getInstance().createBindTask(intent, executor, conn);
    }

    /**
     * Unbind from a root service.
     *
     * @param conn the connection interface previously supplied to
     *             {@link #bind(Intent, ServiceConnection)}
     * @see Context#unbindService(ServiceConnection)
     */
    @MainThread
    public static void unbind(@NonNull ServiceConnection conn) {
        RootServiceManager.getInstance().unbind(conn);
    }

    /**
     * Force stop a root service, launching a new root process if needed.
     * <p>
     * This method is used to immediately stop a root service regardless of its state.
     * ONLY use this method to stop a daemon root service; for normal root services, please use
     * {@link #unbind(ServiceConnection)} instead as this method has to potentially launch
     * an additional root process to ensure daemon services are stopped.
     *
     * @param intent identifies the service to stop.
     */
    @MainThread
    public static void stop(@NonNull Intent intent) {
        if (!Ops.isPrivileged())
            return;
        Shell.Task task = stopOrTask(intent);
        if (task != null) {
            Shell.EXECUTOR.execute(asRunnable(task));
        }
    }

    /**
     * Force stop a root service, creating a task to launch a new root process if needed.
     * <p>
     * This method returns a {@link Shell.Task} that has to be executed to launch a
     * root process if necessary, or else {@code null} will be returned.
     *
     * @see #stop(Intent)
     */
    @MainThread
    @Nullable
    public static Shell.Task stopOrTask(@NonNull Intent intent) {
        return RootServiceManager.getInstance().createStopTask(intent);
    }

    private static Runnable asRunnable(Shell.Task task) {
        return () -> {
            try {
                // Run task while fetching the entire command.
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                // This works because we do not need the other streams.
                task.run(os, null, null);
                // The whole command has now been fetched.
                String cmd = os.toString();
                if (Ops.isAdb()) {
                    // ADB must be checked at first
                    if (LocalServer.getInstance().runCommand(cmd).getStatusCode() != 0) {
                        Log.e(TAG, "Couldn't start service using ADB.");
                    }
                } else if (Ops.isRoot()) {
                    if (!Runner.runCommand(cmd).isSuccessful()) {
                        Log.e(TAG, "Couldn't start service using root.");
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        };
    }

    public RootService() {
        super(null);
    }

    @SuppressLint("RestrictedApi")
    @Override
    protected final void attachBaseContext(Context base) {
        super.attachBaseContext(onAttach(ContextUtils.getContextImpl(base)));
        RootServiceServer.getInstance(base).register(this);
        onCreate();
    }

    /**
     * Called when the RootService is getting attached with a {@link Context}.
     *
     * @param base the context being attached.
     * @return the passed in context by default.
     */
    @NonNull
    protected Context onAttach(@NonNull Context base) {
        return base;
    }

    /**
     * Return the component name that will be used for service lookup.
     * <p>
     * Overriding this method is only for very unusual use cases when a different
     * component name other than the actual class name is desired.
     *
     * @return the desired component name.
     */
    @NonNull
    public ComponentName getComponentName() {
        return new ComponentName(this, getClass());
    }

    @SuppressLint("RestrictedApi")
    @Override
    public final Context getApplicationContext() {
        return ContextUtils.rootContext;
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
     * Force stop this root service.
     */
    public final void stopSelf() {
        RootServiceServer.getInstance(this).selfStop(getComponentName());
    }

    // Deprecated APIs

    /**
     * @deprecated use {@link #bindOrTask(Intent, Executor, ServiceConnection)}
     */
    @MainThread
    @Nullable
    @Deprecated
    public static Runnable createBindTask(
            @NonNull Intent intent,
            @NonNull Executor executor,
            @NonNull ServiceConnection conn) {
        Shell.Task task = bindOrTask(intent, executor, conn);
        return task == null ? null : asRunnable(task);
    }
}
