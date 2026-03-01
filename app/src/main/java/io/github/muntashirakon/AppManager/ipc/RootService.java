/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util;

import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.CallbackRegistry.NotifierCallback;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executor;

/**
 * {@link ObservableServiceConnection} is a concrete implementation of {@link ServiceConnection}
 * that enables monitoring the status of a binder connection. It also aides in automatically
 * converting a proxy into an internal wrapper type.
 *
 * @param <T> The type of the wrapper over the resulting service.
 */
public class ObservableServiceConnection<T> implements ServiceConnection {
    /**
     * An interface for converting the service proxy into a given internal wrapper type.
     *
     * @param <T> The type of the wrapper over the resulting service.
     */
    public interface ServiceTransformer<T> {
        /**
         * Called to convert the service proxy to the wrapper type.
         *
         * @param service The service proxy to create the wrapper type from.
         * @return The wrapper type.
         */
        T convert(IBinder service);
    }

    /**
     * An interface for listening to the connection status.
     *
     * @param <T> The wrapper type.
     */
    public interface Callback<T> {
        /**
         * Invoked when the service has been successfully connected to.
         *
         * @param connection The {@link ObservableServiceConnection} instance that is now connected
         * @param service    The service proxy converted into the typed wrapper.
         */
        void onConnected(ObservableServiceConnection<T> connection, T service);

        /**
         * Invoked when the service has been disconnected.
         *
         * @param connection The {@link ObservableServiceConnection} that is now disconnected.
         * @param reason     The reason for the disconnection.
         */
        void onDisconnected(ObservableServiceConnection<T> connection,
                @DisconnectReason int reason);
    }

    /**
     * Default state, service has not yet disconnected.
     */
    public static final int DISCONNECT_REASON_NONE = 0;
    /**
     * Disconnection was due to the resulting binding being {@code null}.
     */
    public static final int DISCONNECT_REASON_NULL_BINDING = 1;
    /**
     * Disconnection was due to the remote end disconnecting.
     */
    public static final int DISCONNECT_REASON_DISCONNECTED = 2;
    /**
     * Disconnection due to the binder dying.
     */
    public static final int DISCONNECT_REASON_BINDING_DIED = 3;
    /**
     * Disconnection from an explicit unbinding.
     */
    public static final int DISCONNECT_REASON_UNBIND = 4;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            DISCONNECT_REASON_NONE,
            DISCONNECT_REASON_NULL_BINDING,
            DISCONNECT_REASON_DISCONNECTED,
            DISCONNECT_REASON_BINDING_DIED,
            DISCONNECT_REASON_UNBIND
    })
    public @interface DisconnectReason {
    }

    private final Object mLock = new Object();
    private final Context mContext;
    private final Executor mExecutor;
    private final ServiceTransformer<T> mTransformer;
    private final Intent mServiceIntent;
    private final int mFlags;

    @GuardedBy("mLock")
    private T mService;
    @GuardedBy("mLock")
    private boolean mBoundCalled = false;
    @GuardedBy("mLock")
    private int mLastDisconnectReason = DISCONNECT_REASON_NONE;

    private final CallbackRegistry<Callback<T>, ObservableServiceConnection<T>, T>
            mCallbackRegistry = new CallbackRegistry<>(
            new NotifierCallback<Callback<T>, ObservableServiceConnection<T>, T>() {
                    @Override
                    public void onNotifyCallback(Callback<T> callback,
                            ObservableServiceConnection<T> sender,
                            int disconnectReason, T service) {
                        mExecutor.execute(() -> {
                            synchronized (mLock) {
                                if (service != null) {
                                    callback.onConnected(sender, service);
                                } else if (mLastDisconnectReason != DISCONNECT_REASON_NONE) {
                                    callback.onDisconnected(sender, disconnectReason);
                                }
                            }
                        });
                    }
                });

    /**
     * Default constructor for {@link ObservableServiceConnection}.
     *
     * @param context     The context from which the service will be bound with.
     * @param executor    The executor for connection callbacks to be delivered on
     * @param transformer A {@link ObservableServiceConnection.ServiceTransformer} for transforming
     *                    the resulting service into a desired type.
     */
    public ObservableServiceConnection(@NonNull Context context,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull ServiceTransformer<T> transformer,
            Intent serviceIntent,
            int flags) {
        mContext = context;
        mExecutor = executor;
        mTransformer = transformer;
        mServiceIntent = serviceIntent;
        mFlags = flags;
    }

    /**
     * Initiate binding to the service.
     *
     * @return {@code true} if initiating binding succeed, {@code false} if the binding failed or
     * if this service is already bound. Regardless of the return value, you should later call
     * {@link #unbind()} to release the connection.
     */
    public boolean bind() {
        synchronized (mLock) {
            if (mBoundCalled) {
                return false;
            }
            final boolean bindResult =
                    mContext.bindService(mServiceIntent, mFlags, mExecutor, this);
            mBoundCalled = true;
            return bindResult;
        }
    }

    /**
     * Disconnect from the service if bound.
     */
    public void unbind() {
        onDisconnected(DISCONNECT_REASON_UNBIND);
    }

    /**
     * Adds a callback for receiving connection updates.
     *
     * @param callback The {@link Callback} to receive future updates.
     */
    public void addCallback(Callback<T> callback) {
        mCallbackRegistry.add(callback);
        mExecutor.execute(() -> {
            synchronized (mLock) {
                if (mService != null) {
                    callback.onConnected(this, mService);
                } else if (mLastDisconnectReason != DISCONNECT_REASON_NONE) {
                    callback.onDisconnected(this, mLastDisconnectReason);
                }
            }
        });
    }

    /**
     * Removes previously added callback from receiving future connection updates.
     *
     * @param callback The {@link Callback} to be removed.
     */
    public void removeCallback(Callback<T> callback) {
        synchronized (mLock) {
            mCallbackRegistry.remove(callback);
        }
    }

    private void onDisconnected(@DisconnectReason int reason) {
        synchronized (mLock) {
            if (!mBoundCalled) {
                return;
            }
            mBoundCalled = false;
            mLastDisconnectReason = reason;
            mContext.unbindService(this);
            mService = null;
            mCallbackRegistry.notifyCallbacks(this, reason, null);
        }
    }

    @Override
    public final void onServiceConnected(ComponentName name, IBinder service) {
        synchronized (mLock) {
            mService = mTransformer.convert(service);
            mLastDisconnectReason = DISCONNECT_REASON_NONE;
            mCallbackRegistry.notifyCallbacks(this, mLastDisconnectReason, mService);
        }
    }

    @Override
    public final void onServiceDisconnected(ComponentName name) {
        onDisconnected(DISCONNECT_REASON_DISCONNECTED);
    }

    @Override
    public final void onBindingDied(ComponentName name) {
        onDisconnected(DISCONNECT_REASON_BINDING_DIED);
    }

    @Override
    public final void onNullBinding(ComponentName name) {
        onDisconnected(DISCONNECT_REASON_NULL_BINDING);
    }
}
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
                if (Ops.isDirectRoot()) {
                    if (!Runner.runCommand(cmd).isSuccessful()) {
                        Log.e(TAG, "Couldn't start service using root.", new Throwable());
                    }
                } else if (LocalServer.alive(ContextUtils.getContext())) {
                    if (LocalServer.getInstance().runCommand(cmd).getStatusCode() != 0) {
                        Log.e(TAG, "Couldn't start service using ADB.", new Throwable());
                    }
                } else {
                    Log.e(TAG, "Unable to start service using an unsupported mode.", new Throwable());
                }
            } catch (Throwable e) {
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
