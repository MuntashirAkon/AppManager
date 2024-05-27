// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.UserHandleHidden;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.server.common.Caller;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.Shell;
import io.github.muntashirakon.AppManager.server.common.ShellCaller;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.adb.AdbPairingRequiredException;

// Copyright 2016 Zheng Li
public class LocalServer {
    @GuardedBy("lockObject")
    private static final Object sLock = new Object();

    @SuppressLint("StaticFieldLeak")
    @Nullable
    private static LocalServer sLocalServer;

    @GuardedBy("lockObject")
    @WorkerThread
    @NoOps(used = true)
    public static LocalServer getInstance() throws IOException, AdbPairingRequiredException {
        // Non-null check must be done outside the synchronised block to prevent deadlock on ADB over TCP mode.
        if (sLocalServer != null) return sLocalServer;
        synchronized (sLock) {
            try {
                Log.d("IPC", "Init: Local server");
                sLocalServer = new LocalServer();
            } finally {
                sLock.notifyAll();
            }
        }
        return sLocalServer;
    }

    public static void die() {
        synchronized (sLock) {
            try {
                if (sLocalServer != null) {
                    sLocalServer.destroy();
                }
            } finally {
                sLocalServer = null;
            }
        }
    }

    @WorkerThread
    @NoOps
    public static boolean alive(Context context) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(ServerConfig.getLocalServerHost(context),
                    ServerConfig.getLocalServerPort()), 1);
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    @NonNull
    private final Context mContext;
    @NonNull
    private final LocalServerManager mLocalServerManager;

    @WorkerThread
    @NoOps(used = true)
    private LocalServer() throws IOException, AdbPairingRequiredException {
        mContext = ContextUtils.getDeContext(ContextUtils.getContext());
        mLocalServerManager = LocalServerManager.getInstance(mContext);
        // Initialise necessary files and permissions
        ServerConfig.init(mContext, UserHandleHidden.myUserId());
        // Start server if not already
        checkConnect();
    }

    private final Object mConnectLock = new Object();
    private boolean mConnectStarted = false;

    @GuardedBy("connectLock")
    @WorkerThread
    @NoOps(used = true)
    public void checkConnect() throws IOException, AdbPairingRequiredException {
        synchronized (mConnectLock) {
            if (mConnectStarted) {
                try {
                    mConnectLock.wait();
                } catch (InterruptedException e) {
                    return;
                }
            }
            mConnectStarted = true;
            try {
                mLocalServerManager.start();
            } catch (IOException | AdbPairingRequiredException e) {
                mConnectStarted = false;
                mConnectLock.notify();
                throw e;
            }
            mConnectStarted = false;
            mConnectLock.notify();
        }
    }

    public Shell.Result runCommand(String command) throws IOException {
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
            try {
                checkConnect();
                return mLocalServerManager.execNew(caller);
            } catch (AdbPairingRequiredException e2) {
                throw new IOException(e2);
            }
        } catch (AdbPairingRequiredException e) {
            throw new IOException(e);
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
    @NoOps(used = true)
    public static void restart() throws IOException, AdbPairingRequiredException {
        if (sLocalServer != null) {
            LocalServerManager manager = sLocalServer.mLocalServerManager;
            manager.closeBgServer();
            manager.stop();
            manager.start();
        } else {
            getInstance();
        }
    }
}
