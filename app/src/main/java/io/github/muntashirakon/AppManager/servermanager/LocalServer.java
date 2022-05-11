// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.server.common.Caller;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.Shell;
import io.github.muntashirakon.AppManager.server.common.ShellCaller;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

// Copyright 2016 Zheng Li
public class LocalServer {
    @GuardedBy("lockObject")
    private static final Object lockObject = new Object();

    @SuppressLint("StaticFieldLeak")
    private static LocalServer localServer;
    private static IAMService amService;

    @GuardedBy("lockObject")
    @WorkerThread
    @NoOps(used = true)
    public static LocalServer getInstance() throws RemoteException, IOException {
        // Non-null check must be done outside the synchronised block to prevent deadlock on ADB over TCP mode.
        if (localServer != null) return localServer;
        synchronized (lockObject) {
            try {
                Log.d("IPC", "Init: Local server");
                localServer = new LocalServer();
                // This calls the AdbShell class which has dependencies on LocalServer which might cause deadlock
                // if not careful (see comment above on non-null check)
                launchAmService();
            } finally {
                lockObject.notifyAll();
            }
        }
        return localServer;
    }

    @WorkerThread
    @NoOps(used = true)
    public static void launchAmService() throws RemoteException {
        if (amService == null || !amService.asBinder().pingBinder()) {
            amService = LocalServices.bindAmService();
            LocalServices.bindFileSystemManager();
        }
    }

    @WorkerThread
    @NoOps
    public static boolean isLocalServerAlive(Context context) {
        if (localServer != null) {
            return true;
        } else {
            try (ServerSocket socket = new ServerSocket()) {
                socket.bind(new InetSocketAddress(ServerConfig.getLocalServerHost(context),
                        ServerConfig.getLocalServerPort()), 1);
                return false;
            } catch (IOException e) {
                return true;
            }
        }
    }

    @AnyThread
    @NoOps
    public static boolean isAMServiceAlive() {
        if (amService != null) return amService.asBinder().pingBinder();
        else return false;
    }

    @NonNull
    private final Context mContext;
    @NonNull
    private final LocalServerManager mLocalServerManager;

    @WorkerThread
    @NoOps(used = true)
    private LocalServer() throws IOException {
        mContext = ContextUtils.getDeContext(AppManager.getContext());
        mLocalServerManager = LocalServerManager.getInstance(mContext);
        // Initialise necessary files and permissions
        ServerConfig.init(mContext, UserHandleHidden.myUserId());
        // Check if am.jar is in the right place
        checkFile();
        // Start server if not already
        checkConnect();
    }

    private final Object connectLock = new Object();
    private boolean connectStarted = false;

    @GuardedBy("connectLock")
    @WorkerThread
    @NoOps(used = true)
    public void checkConnect() throws IOException {
        synchronized (connectLock) {
            if (connectStarted) {
                try {
                    connectLock.wait();
                } catch (InterruptedException e) {
                    return;
                }
            }
            connectStarted = true;
            try {
                if (Ops.isPrivileged()) {
                    mLocalServerManager.start();
                }
            } catch (IOException e) {
                connectStarted = false;
                connectLock.notify();
                throw new IOException(e);
            }
            connectStarted = false;
            connectLock.notify();
        }
    }

    public Shell.Result runCommand(String command) throws IOException, RemoteException {
        ShellCaller shellCaller = new ShellCaller(command);
        CallerResult callerResult = exec(shellCaller);
        Throwable th = callerResult.getThrowable();
        if (th != null) {
            throw new IOException(th);
        }
        return (Shell.Result) callerResult.getReplyObj();
    }

    @WorkerThread
    public CallerResult exec(Caller caller) throws IOException {
        try {
            checkConnect();
            return mLocalServerManager.execNew(caller);
        } catch (SocketTimeoutException e) {
            e.printStackTrace();
            closeBgServer();
            // Retry
            checkConnect();
            return mLocalServerManager.execNew(caller);
        }
    }

    @AnyThread
    public boolean isRunning() {
        return mLocalServerManager.isRunning();
    }

    public void destroy() {
        mLocalServerManager.stop();
    }

    @WorkerThread
    public void closeBgServer() {
        mLocalServerManager.closeBgServer();
        mLocalServerManager.stop();
    }

    @WorkerThread
    @NoOps
    private void checkFile() throws IOException {
        AssetsUtils.copyFile(mContext, ServerConfig.JAR_NAME, ServerConfig.getDestJarFile(), BuildConfig.DEBUG);
        AssetsUtils.writeScript(mContext);
    }

    @WorkerThread
    @NoOps(used = true)
    public static void restart() throws IOException, RemoteException {
        if (localServer != null) {
            LocalServerManager manager = localServer.mLocalServerManager;
            manager.closeBgServer();
            manager.stop();
            manager.start();
        } else {
            getInstance();
        }
    }
}
