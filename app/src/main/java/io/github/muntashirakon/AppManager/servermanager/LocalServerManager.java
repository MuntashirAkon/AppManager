// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.os.SystemClock;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.adb.AdbConnectionManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.server.common.BaseCaller;
import io.github.muntashirakon.AppManager.server.common.Caller;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.DataTransmission;
import io.github.muntashirakon.AppManager.server.common.ParcelableUtil;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.adb.AdbStream;

// Copyright 2016 Zheng Li
class LocalServerManager {
    private static final String TAG = "LocalServerManager";

    private static LocalServerManager sLocalServerManager;

    @WorkerThread
    static LocalServerManager getInstance() {
        if (sLocalServerManager == null) {
            synchronized (LocalServerManager.class) {
                if (sLocalServerManager == null) {
                    sLocalServerManager = new LocalServerManager();
                }
            }
        }
        return sLocalServerManager;
    }

    @Nullable
    private ClientSession mSession;
    private LocalServer.Config mConfig;

    @WorkerThread
    private LocalServerManager() {
        mConfig = new LocalServer.Config();
    }

    /**
     * Update preferences
     *
     * @param config The new preferences
     */
    @AnyThread
    void updateConfig(LocalServer.Config config) {
        if (config != null) {
            mConfig = config;
        }
    }

    @AnyThread
    LocalServer.Config getConfig() {
        return mConfig;
    }

    /**
     * Get current session. If no session is running, create a new one. If no server is running,
     * create one first.
     *
     * @return Currently running session
     * @throws IOException When creating session fails or server couldn't be started
     */
    @WorkerThread
    @NonNull
    private ClientSession getSession() throws IOException {
        if (mSession == null || !mSession.isRunning()) {
            try {
                mSession = createSession();
            } catch (Exception ignore) {
            }
            if (mSession == null) {
                try {
                    startServer();
                } catch (Exception e) {
                    throw new IOException("Could not create session", e);
                }
                mSession = createSession();
            }
        }
        return mSession;
    }

    @AnyThread
    public boolean isRunning() {
        return mSession != null && mSession.isRunning();
    }

    /**
     * Close client session
     */
    @AnyThread
    void closeSession() {
        FileUtils.closeQuietly(mSession);
        mSession = null;
    }

    /**
     * Stop ADB and then close client session
     */
    void stop() {
        FileUtils.closeQuietly(adbStream);
        FileUtils.closeQuietly(mSession);
        adbStream = null;
        mSession = null;
    }

    @WorkerThread
    void start() throws IOException {
        getSession();
    }

    @WorkerThread
    @NonNull
    private DataTransmission getSessionDataTransmission() throws IOException {
        return getSession().getDataTransmission();
    }

    @WorkerThread
    private byte[] execPre(byte[] params) throws IOException {
        try {
            return getSessionDataTransmission().sendAndReceiveMessage(params);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("pipe")) {
                closeSession();
                return getSessionDataTransmission().sendAndReceiveMessage(params);
            }
            throw e;
        }
    }

    @WorkerThread
    CallerResult execNew(@NonNull Caller caller) throws IOException {
        byte[] result = execPre(ParcelableUtil.marshall(new BaseCaller(caller.wrapParameters())));
        return ParcelableUtil.unmarshall(result, CallerResult.CREATOR);
    }

    @WorkerThread
    void closeBgServer() {
        try {
            BaseCaller baseCaller = new BaseCaller(BaseCaller.TYPE_CLOSE);
            createSession().getDataTransmission().sendAndReceiveMessage(ParcelableUtil.marshall(baseCaller));
        } catch (Exception e) {
            // Since the server is closed abruptly, this should always produce error
            Log.w(TAG, "closeBgServer: " + e.getCause() + "  " + e.getMessage());
        }
    }

    @WorkerThread
    @NonNull
    private String getExecCommand() throws IOException {
        AssetsUtils.writeScript(mConfig);
        Log.e(TAG, "classpath --> " + ServerConfig.getClassPath());
        Log.e(TAG, "exec path --> " + ServerConfig.getExecPath());
        return "sh " + ServerConfig.getExecPath() + " " + ServerConfig.getLocalServerPort() + " " + ServerConfig.getLocalToken();
    }

    @Nullable
    private volatile AdbStream adbStream;
    private volatile CountDownLatch adbConnectionWatcher = new CountDownLatch(1);
    private volatile boolean adbServerStarted;
    private final Runnable adbOutputThread = () -> {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(adbStream).openInputStream()))) {
            String s;
            while ((s = reader.readLine()) != null) {
                Log.d(TAG, "RESPONSE: " + s);
                if (s.startsWith("Success!")) {
                    adbServerStarted = true;
                    adbConnectionWatcher.countDown();
                } else if (s.startsWith("Error!")) {
                    adbServerStarted = false;
                    adbConnectionWatcher.countDown();
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "useAdbStartServer: unable to read from shell.", e);
        }
    };

    void tryAdb() throws Exception {
        AdbConnectionManager adbConnectionManager = AdbConnectionManager.getInstance();
        if (!adbConnectionManager.isConnected() || !adbConnectionManager.connect(mConfig.adbHost, mConfig.adbPort)) {
            throw new IOException("Could not connect to ADB.");
        }
    }

    @WorkerThread
    private void useAdbStartServer() throws Exception {
        if (adbStream == null || Objects.requireNonNull(adbStream).isClosed()) {
            // ADB shell not running
            AdbConnectionManager manager = AdbConnectionManager.getInstance();
            if (!manager.isConnected() && !manager.connect(mConfig.adbHost, mConfig.adbPort)) {
                throw new IOException("Could not connect to ADB.");
            }
            Log.d(TAG, "useAdbStartServer: Connected using host=" + mConfig.adbHost + ", port=" + mConfig.adbPort);

            Log.d(TAG, "useAdbStartServer: Opening shell...");
            adbStream = manager.openStream("shell:");
            adbConnectionWatcher = new CountDownLatch(1);
            adbServerStarted = false;
            new Thread(adbOutputThread).start();
        }
        Log.d(TAG, "useAdbStartServer: Shell opened.");

        try (OutputStream os = Objects.requireNonNull(adbStream).openOutputStream()) {
            os.write("id\n".getBytes());
            String command = getExecCommand();
            Log.d(TAG, "useAdbStartServer: " + command);
            os.write((command + "\n").getBytes());
        }

        adbConnectionWatcher.await(1, TimeUnit.MINUTES);
        if (adbConnectionWatcher.getCount() == 1 || !adbServerStarted) {
            throw new Exception("Server wasn't started.");
        }
        Log.d(TAG, "useAdbStartServer: Server has started.");
    }

    @WorkerThread
    private void useRootStartServer() throws Exception {
        if (!RunnerUtils.isRootGiven()) {
            throw new Exception("Root access denied");
        }
        String command = getExecCommand(); // + "\n" + "supolicy --live 'allow qti_init_shell zygote_exec file execute'";
        Log.d(TAG, "useRootStartServer: " + command);
        Runner.Result result = Runner.runCommand(Runner.getRootInstance(), command);

        Log.d(TAG, "useRootStartServer: " + result.getOutput());
        if (!result.isSuccessful()) {
            throw new Exception("Could not start server.");
        }
        SystemClock.sleep(3000);
        Log.e(TAG, "useRootStartServer: Server has started.");
    }

    /**
     * Start root or ADB server based on config
     */
    @WorkerThread
    private void startServer() throws Exception {
        if (AppPref.isAdbEnabled()) {
            useAdbStartServer();
        } else if (AppPref.isRootEnabled()) {
            useRootStartServer();
        } else throw new Exception("Neither root nor ADB mode is enabled.");
    }

    /**
     * Create a client session
     *
     * @return New session if not running, running session otherwise
     * @throws IOException      If session creation failed
     */
    @WorkerThread
    @NonNull
    private ClientSession createSession() throws IOException {
        if (isRunning()) {
            // Non-null check has already been done
            return Objects.requireNonNull(mSession);
        }
        if (!AppPref.isRootOrAdbEnabled()) {
            throw new IOException("Root/ADB not enabled.");
        }
        Socket socket = new Socket(ServerConfig.getLocalServerHost(), ServerConfig.getLocalServerPort());
        socket.setSoTimeout(1000 * 30);
        OutputStream os = socket.getOutputStream();
        InputStream is = socket.getInputStream();
        DataTransmission transfer = new DataTransmission(os, is, false);
        transfer.shakeHands(ServerConfig.getLocalToken(), DataTransmission.Role.Client);
        return new ClientSession(transfer);
    }

    /**
     * The client session handler
     */
    private static class ClientSession implements AutoCloseable {
        private volatile boolean mIsRunning;
        @NonNull
        private final DataTransmission mDataTransmission;

        @AnyThread
        ClientSession(@NonNull DataTransmission dataTransmission) {
            this.mDataTransmission = dataTransmission;
            this.mIsRunning = true;
        }

        /**
         * Close the session, stop any active transmission
         */
        @AnyThread
        @Override
        public void close() {
            if (mIsRunning) {
                mIsRunning = false;
                mDataTransmission.close();
            }
        }

        /**
         * Whether the client session is running
         */
        @AnyThread
        boolean isRunning() {
            return mIsRunning;
        }

        @AnyThread
        @NonNull
        DataTransmission getDataTransmission() {
            return mDataTransmission;
        }
    }
}
