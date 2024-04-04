// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.UserHandleHidden;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;

import io.github.muntashirakon.AppManager.BuildConfig;
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
    private static final Object sLock = new Object();

    @SuppressLint("StaticFieldLeak")
    private static LocalServer sLocalServer;

    @GuardedBy("lockObject")
    @WorkerThread
    @NoOps(used = true)
    public static LocalServer getInstance() throws IOException {
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

    @WorkerThread
    @NoOps
    public static boolean alive(Context context) {
        if (sLocalServer != null) {
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

    @NonNull
    private final Context mContext;
    @NonNull
    private final LocalServerManager mLocalServerManager;

    @WorkerThread
    @NoOps(used = true)
    private LocalServer() throws IOException {
        mContext = ContextUtils.getDeContext(ContextUtils.getContext());
        mLocalServerManager = LocalServerManager.getInstance(mContext);
        // Initialise necessary files and permissions
        ServerConfig.init(mContext, UserHandleHidden.myUserId());
        // Check if am.jar is in the right place
        checkFile();
        // Start server if not already
        checkConnect();
    }

    private final Object mConnectLock = new Object();
    private boolean mConnectStarted = false;

    @GuardedBy("connectLock")
    @WorkerThread
    @NoOps(used = true)
    public void checkConnect() throws IOException {
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
                if (Ops.isPrivileged()) {
                    mLocalServerManager.start();
                }
            } catch (IOException e) {
                mConnectStarted = false;
                mConnectLock.notify();
                throw new IOException(e);
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
    public static void restart() throws IOException {
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
