// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.os.RemoteException;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.FileSystemManager;

public class LocalServices {
    @NonNull
    private static final ServiceConnectionWrapper sFileSystemServiceConnectionWrapper
            = new ServiceConnectionWrapper(BuildConfig.APPLICATION_ID, FileSystemService.class.getName());

    @WorkerThread
    public static void bindServicesIfNotAlready() throws RemoteException {
        if (!alive()) {
            bindServices();
        }
    }

    @WorkerThread
    public static void bindServices() throws RemoteException {
        unbindServicesIfRunning();
        bindAmService();
        bindFileSystemManager();
        // Verify binding
        if (!getAmService().asBinder().pingBinder()) {
            throw new RemoteException("IAmService not running.");
        }
        getFileSystemManager();
    }

    public static boolean alive() {
        synchronized (sAMServiceConnectionWrapper) {
            return sAMServiceConnectionWrapper.isBinderActive();
        }
    }

    @WorkerThread
    @NoOps(used = true)
    public static void bindFileSystemManager() throws RemoteException {
        synchronized (sFileSystemServiceConnectionWrapper) {
            try {
                sFileSystemServiceConnectionWrapper.bindService();
            } finally {
                sFileSystemServiceConnectionWrapper.notifyAll();
            }
        }
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
            = new ServiceConnectionWrapper(BuildConfig.APPLICATION_ID, AMService.class.getName());

    @WorkerThread
    @NoOps(used = true)
    private static void bindAmService() throws RemoteException {
        synchronized (sAMServiceConnectionWrapper) {
            try {
                sAMServiceConnectionWrapper.bindService();
            } finally {
                sAMServiceConnectionWrapper.notifyAll();
            }
        }
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
    }

    @MainThread
    public static void unbindServices() {
        synchronized (sAMServiceConnectionWrapper) {
            sAMServiceConnectionWrapper.unbindService();
        }
        synchronized (sFileSystemServiceConnectionWrapper) {
            sFileSystemServiceConnectionWrapper.unbindService();
        }
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
