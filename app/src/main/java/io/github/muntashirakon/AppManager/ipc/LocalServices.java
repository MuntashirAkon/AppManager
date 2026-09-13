// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.os.Process;
import android.os.RemoteException;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.permission.PermissionOverrideManager;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.FileSystemManager;

public class LocalServices {
    private static final Object sBindLock = new Object();
    private static final MutableLiveData<Boolean> sState = new MutableLiveData<>(false);

    @NonNull
    public static LiveData<Boolean> state() {
        return sState;
    }

    @NonNull
    private static final ServiceConnectionWrapper sFileSystemServiceConnectionWrapper
            = new ServiceConnectionWrapper(BuildConfig.APPLICATION_ID, FileSystemService.class.getName(),
            LocalServices::onServiceBinderDied);

    @WorkerThread
    public static void bindServicesIfNotAlready() throws RemoteException {
        // Must be one atomic operation.
        synchronized (sBindLock) {
            if (!alive()) {
                bindServices();
            }
        }
    }

    @WorkerThread
    public static void bindServices() throws RemoteException {
        synchronized (sBindLock) {
            unbindServicesIfRunning();
            try {
                bindAmService();
                bindFileSystemManager();
                // Verify both binders before publishing the capability.
                if (!getAmService().asBinder().pingBinder()
                        || !sFileSystemServiceConnectionWrapper.isBinderActive()) {
                    throw new RemoteException("Required service binder is not running.");
                }
                // Update UID only after both services are valid.
                Ops.setWorkingUid(getAmService().getUid());
                // A reconnect can follow a phone restart that cleared volatile firewall rules.
                PermissionOverrideManager.reconcileAll();
                sState.postValue(true);
            } catch (RemoteException | RuntimeException e) {
                stopServices();
                throw e;
            }
        }
    }

    public static boolean alive() {
        return sAMServiceConnectionWrapper.isBinderActive()
                && sFileSystemServiceConnectionWrapper.isBinderActive();
    }

    private static void onServiceBinderDied() {
        ThreadUtils.postOnBackgroundThread(LocalServices::stopServices);
    }

    @WorkerThread
    @NoOps(used = true)
    private static void bindFileSystemManager() throws RemoteException {
        sFileSystemServiceConnectionWrapper.bindService();
    }

    @AnyThread
    @NonNull
    @NoOps
    public static FileSystemManager getFileSystemManager() throws RemoteException {
        synchronized (sFileSystemServiceConnectionWrapper) {
            try {
                return FileSystemManager.getRemote(sFileSystemServiceConnectionWrapper.getService());
            } finally {
                sFileSystemServiceConnectionWrapper.notifyAll();
            }
        }
    }

    @NonNull
    private static final ServiceConnectionWrapper sAMServiceConnectionWrapper
            = new ServiceConnectionWrapper(BuildConfig.APPLICATION_ID, AMService.class.getName(),
            LocalServices::onServiceBinderDied);

    @WorkerThread
    @NoOps(used = true)
    private static void bindAmService() throws RemoteException {
        sAMServiceConnectionWrapper.bindService();
    }

    @AnyThread
    @NonNull
    @NoOps
    public static IAMService getAmService() throws RemoteException {
        synchronized (sAMServiceConnectionWrapper) {
            try {
                return IAMService.Stub.asInterface(sAMServiceConnectionWrapper.getService());
            } finally {
                sAMServiceConnectionWrapper.notifyAll();
            }
        }
    }

    @WorkerThread
    @NoOps
    public static void stopServices() {
        synchronized (sAMServiceConnectionWrapper) {
            sAMServiceConnectionWrapper.stopDaemon();
        }
        synchronized (sFileSystemServiceConnectionWrapper) {
            sFileSystemServiceConnectionWrapper.stopDaemon();
        }
        Ops.setWorkingUid(Process.myUid());
        Ops.invalidateRuntimeBackend();
        sState.postValue(false);
    }

    @MainThread
    public static void unbindServices() {
        synchronized (sAMServiceConnectionWrapper) {
            sAMServiceConnectionWrapper.unbindService();
        }
        synchronized (sFileSystemServiceConnectionWrapper) {
            sFileSystemServiceConnectionWrapper.unbindService();
        }
        Ops.setWorkingUid(Process.myUid());
        Ops.invalidateRuntimeBackend();
        sState.postValue(false);
    }

    @WorkerThread
    private static void unbindServicesIfRunning() {
        // Basically unregister the services so that we can open another connection
        CountDownLatch unbindWatcher = new CountDownLatch(1);
        ThreadUtils.postOnMainThread(() -> {
            try {
                unbindServices();
            } finally {
                unbindWatcher.countDown();
            }
        });
        try {
            unbindWatcher.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException ignore) {
        }
    }
}
