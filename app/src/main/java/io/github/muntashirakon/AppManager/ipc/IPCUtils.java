package io.github.muntashirakon.AppManager.ipc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.os.RemoteException;
import androidx.annotation.*;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.AppPref;

public final class IPCUtils {
    private static final String TAG = "IPCUtils";

    @Nullable
    private static final ComponentName COMPONENT_NAME = getComponentName();
    private static final AMServiceConnectionWrapper connectionWrapper = new AMServiceConnectionWrapper();
    private static IAMService amService;

    @Nullable
    private static ComponentName getComponentName() {
        try {
            return new ComponentName(AppManager.getContext(), AMService.class);
        } catch (Throwable th) {
            return null;
        }
    }

    @GuardedBy("connectionWrapper")
    @WorkerThread
    @NonNull
    public static IAMService getAmService() throws RemoteException {
        synchronized (connectionWrapper) {
            try {
                return amService = connectionWrapper.getAmService();
            } finally {
                connectionWrapper.notifyAll();
            }
        }
    }

    @GuardedBy("connectionWrapper")
    @NonNull
    public static AMServiceConnectionWrapper getNewConnection() {
        synchronized (connectionWrapper) {
            try {
                try {
                    if (amService == null) {
                        connectionWrapper.wait();
                    }
                } catch (Exception ignore) {
                }
                return new AMServiceConnectionWrapper();
            } finally {
                connectionWrapper.notifyAll();
            }
        }
    }

    @AnyThread
    @Nullable
    public static IAMService getService() {
        return amService;
    }

    @AnyThread
    @NonNull
    public static IAMService getServiceSafe() throws RemoteException {
        if (amService == null || !amService.asBinder().pingBinder()) {
            throw new RemoteException("AMService not running.");
        }
        return amService;
    }

    @WorkerThread
    public static void stopDaemon(@NonNull Context context) {
        Intent intent = new Intent(context, AMService.class);
        // Use stop here instead of unbind because AIDLService is running as a daemon.
        // To verify whether the daemon actually works, kill the app and try testing the
        // daemon again. You should get the same PID as last time (as it was re-using the
        // previous daemon process), and in AIDLService, onRebind should be called.
        // Note: re-running the app in Android Studio is not the same as kill + relaunch.
        // The root service will kill itself when it detects the client APK has updated.
        RootService.stop(intent);
        amService = null;
    }

    public static class AMServiceConnectionWrapper {
        private final AMServiceConnection conn = new AMServiceConnection();
        private IAMService amService;
        private CountDownLatch amServiceBoundWatcher;

        private class AMServiceConnection implements ServiceConnection {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "service onServiceConnected");
                amService = IAMService.Stub.asInterface(service);
                onResponseReceived();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "service onServiceDisconnected");
                amService = null;
                onResponseReceived();
            }

            @Override
            public void onBindingDied(ComponentName name) {
                Log.d(TAG, "service onBindingDied");
                amService = null;
                onResponseReceived();
            }

            @Override
            public void onNullBinding(ComponentName name) {
                Log.d(TAG, "service onNullBinding");
                amService = null;
                onResponseReceived();
            }

            private void onResponseReceived() {
                Log.d(TAG, "service onResponseReceived");
                if (amServiceBoundWatcher != null) {
                    // Should never be null
                    amServiceBoundWatcher.countDown();
                } else throw new RuntimeException("AMService watcher should never be null!");
            }
        }

        private AMServiceConnectionWrapper() {
        }

        @WorkerThread
        private void startDaemon() {
            if (amService == null) {
                if (amServiceBoundWatcher == null || amServiceBoundWatcher.getCount() == 0) {
                    amServiceBoundWatcher = new CountDownLatch(1);
                    Log.e(TAG, "Launching service...");
                    Intent intent = new Intent();
                    intent.setComponent(COMPONENT_NAME);
                    synchronized (conn) {
                        RootService.bind(intent, conn);
                    }
                }
                // Wait for service to be bound
                try {
                    amServiceBoundWatcher.await(45, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Log.e(TAG, "AMService watcher interrupted.");
                }
            }
        }

        @WorkerThread
        @NonNull
        public IAMService getAmService() throws RemoteException {
            synchronized (conn) {
                if (amService == null && AppPref.isRootOrAdbEnabled()) {
                    startDaemon();
                }
                return getServiceSafe();
            }
        }

        @AnyThread
        @Nullable
        public IAMService getService() {
            return amService;
        }

        @AnyThread
        @NonNull
        public IAMService getServiceSafe() throws RemoteException {
            if (amService == null || !amService.asBinder().pingBinder()) {
                throw new RemoteException("AMService not running.");
            }
            return amService;
        }

        public void unbindService() {
            synchronized (conn) {
                RootService.unbind(conn);
            }
        }
    }
}
