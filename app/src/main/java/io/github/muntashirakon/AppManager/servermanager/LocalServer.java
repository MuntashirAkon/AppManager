/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.RemoteException;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.ipc.IPCUtils;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.server.common.Caller;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;

public class LocalServer {
    @GuardedBy("lockObject")
    private static final Object lockObject = new Object();

    @SuppressLint("StaticFieldLeak")
    private static LocalServer localServer;
    private static IAMService amService;

    @GuardedBy("lockObject")
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

    public static void launchAmService() throws RemoteException {
        if (amService == null || !amService.asBinder().pingBinder()) {
            amService = IPCUtils.getAmService();
        }
    }

    public static boolean isLocalServerAlive() {
        if (localServer != null) return true;
        else {
            try (ServerSocket ignored = new ServerSocket(ServerConfig.getLocalServerPort())) {
                return false;
            } catch (IOException e) {
                return true;
            }
        }
    }

    public static boolean isAMServiceAlive() {
        if (amService != null) return amService.asBinder().pingBinder();
        else return false;
    }

    private final LocalServerManager mLocalServerManager;
    private final Context mContext;

    private LocalServer() {
        mContext = ContextUtils.getDeContext(AppManager.getContext());
        Config config = new Config();
        config.context = mContext;
        updateConfig(config);
        int userHandleId = Users.getCurrentUserHandle();
        ServerConfig.init(config.context, userHandleId);
        mLocalServerManager = LocalServerManager.getInstance(config);
        // Check if am.jar is in the right place
        checkFile();
        // Start server if not already
        try {
            checkConnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    Config getConfig() {
        return mLocalServerManager.getConfig();
    }

    private final Object connectLock = new Object();
    private boolean connectStarted = false;

    @GuardedBy("connectLock")
    @WorkerThread
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
                if (AppPref.isRootOrAdbEnabled()) {
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

    @WorkerThread
    public CallerResult exec(Caller caller) throws Exception {
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

    public boolean isRunning() {
        return mLocalServerManager != null && mLocalServerManager.isRunning();
    }

    public void destroy() {
        if (mLocalServerManager != null) {
            mLocalServerManager.stop();
        }
    }

    public void closeBgServer() {
        if (mLocalServerManager != null) {
            mLocalServerManager.closeBgServer();
            mLocalServerManager.stop();
        }
    }

    private void checkFile() {
        AssetsUtils.copyFile(mContext, ServerConfig.JAR_NAME, ServerConfig.getDestJarFile(), BuildConfig.DEBUG);
        AssetsUtils.writeScript(getConfig());
    }

    public static void restart() throws IOException, RemoteException {
        LocalServerManager manager = getInstance().mLocalServerManager;
        manager.closeBgServer();
        manager.stop();
        manager.start();
    }

    public static void updateConfig() {
        if (localServer != null) {
            Config config = localServer.getConfig();
            if (config != null) {
                updateConfig(config);
            }
        }
    }

    private static void updateConfig(@NonNull Config config) {
        config.allowBgRunning = ServerConfig.getAllowBgRunning();
        config.adbPort = ServerConfig.getAdbPort();
        if (localServer != null) localServer.mLocalServerManager.updateConfig(config);
    }

    public static class Config {
        public boolean allowBgRunning = false;
        public boolean printLog = false;
        public String adbHost = ServerConfig.getAdbHost();
        public int adbPort = ServerConfig.getAdbPort();
        Context context;
    }
}
