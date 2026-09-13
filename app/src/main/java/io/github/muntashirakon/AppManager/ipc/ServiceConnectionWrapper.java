// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

class ServiceConnectionWrapper {
    public static final String TAG = ServiceConnectionWrapper.class.getSimpleName();

    @Nullable
    private volatile IBinder mIBinder;
    @Nullable
    private volatile CountDownLatch mServiceBoundWatcher;
    @Nullable
    private final Runnable mDeathCallback;

    private class ServiceConnectionImpl implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "service onServiceConnected: %s", name);
            synchronized (this) {
                // The callback may arrive after stopDaemon() cancelled the bind.
                if (mServiceBoundWatcher == null) {
                    RootService.stop(new Intent().setComponent(mComponentName));
                    return;
                }
                mIBinder = service;
            }
            try {
                service.linkToDeath(() -> {
                    boolean binderDied;
                    synchronized (ServiceConnectionImpl.this) {
                        binderDied = mIBinder == service;
                        if (binderDied) {
                            mIBinder = null;
                        }
                    }
                    if (binderDied && mDeathCallback != null) {
                        mDeathCallback.run();
                    }
                }, 0);
            } catch (RemoteException e) {
                synchronized (this) {
                    mIBinder = null;
                }
            }
            onResponseReceived();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "service onServiceDisconnected: %s", name);
            onBinderLost();
            onResponseReceived();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.d(TAG, "service onBindingDied: %s", name);
            onBinderLost();
            onResponseReceived();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.d(TAG, "service onNullBinding: %s", name);
            onBinderLost();
            onResponseReceived();
        }

        private void onBinderLost() {
            boolean binderLost;
            synchronized (this) {
                binderLost = mIBinder != null;
                mIBinder = null;
            }
            if (binderLost && mDeathCallback != null) {
                mDeathCallback.run();
            }
        }

        private void onResponseReceived() {
            synchronized (this) {
                if (mServiceBoundWatcher != null) {
                mServiceBoundWatcher.countDown();
                }
            }
        }
    }

    @NonNull
    private final ComponentName mComponentName;
    @NonNull
    private final ServiceConnectionImpl mServiceConnection;

    public ServiceConnectionWrapper(@NonNull String pkgName, @NonNull String className) {
        this(new ComponentName(pkgName, className), null);
    }

    public ServiceConnectionWrapper(@NonNull String pkgName, @NonNull String className,
                                    @Nullable Runnable deathCallback) {
        this(new ComponentName(pkgName, className), deathCallback);
    }

    public ServiceConnectionWrapper(@NonNull ComponentName cn) {
        this(cn, null);
    }

    public ServiceConnectionWrapper(@NonNull ComponentName cn, @Nullable Runnable deathCallback) {
        mComponentName = cn;
        mDeathCallback = deathCallback;
        mServiceConnection = new ServiceConnectionImpl();
    }

    @NonNull
    public IBinder getService() throws RemoteException {
        if (!isBinderActive()) {
            throw new RemoteException("Binder not running.");
        }
        return Objects.requireNonNull(mIBinder);
    }

    @NonNull
    @NoOps(used = true)
    public IBinder bindService() throws RemoteException {
        if (!isBinderActive()) {
            startDaemon();
        }
        return getService();
    }

    @MainThread
    public void unbindService() {
        synchronized (mServiceConnection) {
            RootService.unbind(mServiceConnection);
        }
    }

    @WorkerThread
    private void startDaemon() {
        CountDownLatch serviceBoundWatcher;
        synchronized (mServiceConnection) {
            if (isBinderActive()) {
                Log.d(TAG, "Binder is already active?");
                return;
            }
            serviceBoundWatcher = mServiceBoundWatcher;
            if (serviceBoundWatcher == null) {
                serviceBoundWatcher = new CountDownLatch(1);
                mServiceBoundWatcher = serviceBoundWatcher;
                Log.d(TAG, "Launching service...");
                Intent intent = new Intent();
                intent.setComponent(mComponentName);
                ThreadUtils.postOnMainThread(() -> {
                    if (mIBinder != null) {
                        RootService.stop(intent);
                    }
                    RootService.bind(intent, mServiceConnection);
                });
            }
        }
        // Wait for service to be bound without holding mServiceConnection. The stop path may
        // need the same lock to cancel a bind that never receives a callback.
        try {
            serviceBoundWatcher.await(45, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Service watcher interrupted.");
        } finally {
            synchronized (mServiceConnection) {
                if (mServiceBoundWatcher == serviceBoundWatcher) {
                    mServiceBoundWatcher = null;
                }
            }
        }
    }

    @WorkerThread
    public void stopDaemon() {
        Intent intent = new Intent();
        intent.setComponent(mComponentName);
        CountDownLatch serviceBoundWatcher;
        synchronized (mServiceConnection) {
            mIBinder = null;
            serviceBoundWatcher = mServiceBoundWatcher;
            mServiceBoundWatcher = null;
        }
        if (serviceBoundWatcher != null) {
            serviceBoundWatcher.countDown();
        }
        ThreadUtils.postOnMainThread(() -> RootService.stop(intent));
    }

    boolean isBinderActive() {
        return mIBinder != null && mIBinder.pingBinder();
    }
}
