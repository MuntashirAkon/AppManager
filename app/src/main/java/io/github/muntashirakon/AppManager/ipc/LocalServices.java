// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.os.RemoteException;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.io.FileSystemManager;

public class LocalServices {
    @NonNull
    private static final ServiceConnectionWrapper sFileSystemServiceConnectionWrapper
            = new ServiceConnectionWrapper(BuildConfig.APPLICATION_ID, FileSystemService.class.getName());

    @WorkerThread
    @NonNull
    @NoOps(used = true)
    public static FileSystemManager bindFileSystemManager() throws RemoteException {
        synchronized (sFileSystemServiceConnectionWrapper) {
            try {
                return FileSystemManager.getRemote(sFileSystemServiceConnectionWrapper.bindService());
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
    private static final ServiceConnectionWrapper sAMServiceConnectionWrapper = new ServiceConnectionWrapper(BuildConfig.APPLICATION_ID, AMService.class.getName());

    @WorkerThread
    @NonNull
    @NoOps(used = true)
    public static IAMService bindAmService() throws RemoteException {
        synchronized (sAMServiceConnectionWrapper) {
            try {
                return IAMService.Stub.asInterface(sAMServiceConnectionWrapper.bindService());
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
}
