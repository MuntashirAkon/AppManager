// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import io.github.muntashirakon.AppManager.adb.AdbConnection;
import io.github.muntashirakon.AppManager.adb.AdbConnectionManager;
import io.github.muntashirakon.AppManager.adb.AdbStream;
import io.github.muntashirakon.AppManager.adb.LineReader;
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
    private ClientSession mSession = null;
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
    private DataTransmission getSessionTransmission() throws Exception {
        ClientSession session = getSession();
        if (session == null) {
            throw new RuntimeException("create session error ------");
        }
        DataTransmission transfer = session.getTransmission();
        if (transfer == null) {
            throw new RuntimeException("get transfer error -----");
        }
        return transfer;
    }

    @WorkerThread
    private byte[] execPre(byte[] params) throws Exception {
        try {
            return getSessionTransmission().sendAndReceiveMessage(params);
        } catch (IOException e) {
            e.printStackTrace();
            if (e.getMessage() != null && e.getMessage().contains("pipe")) {
                closeSession();
                return getSessionTransmission().sendAndReceiveMessage(params);
            }
            throw e;
        }
    }

    @WorkerThread
    CallerResult execNew(@NonNull Caller caller) throws Exception {
        byte[] result = execPre(ParcelableUtil.marshall(new BaseCaller(caller.wrapParams())));
        return ParcelableUtil.unmarshall(result, CallerResult.CREATOR);
    }

    @WorkerThread
    void closeBgServer() {
        try {
            BaseCaller baseCaller = new BaseCaller(BaseCaller.TYPE_CLOSE);
            createSession().getTransmission().sendAndReceiveMessage(ParcelableUtil.marshall(baseCaller));
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
    private AdbConnection connection;
    @Nullable
    private AdbStream adbStream;

    void tryAdb() throws Exception {
        if (connection != null) {
            // Connection still present
            return;
        }
        connection = AdbConnectionManager.connect(mConfig.adbHost, mConfig.adbPort);
    }

    @WorkerThread
    private void useAdbStartServer() throws Exception {
        if (adbStream != null && !adbStream.isClosed()) {
            // ADB shell running
            return;
        }
        if (connection == null) {
            // ADB server not running running
            Log.d(TAG, "useAdbStartServer: Connecting using host=" + mConfig.adbHost + ", port=" + mConfig.adbPort);
            connection = AdbConnectionManager.connect(mConfig.adbHost, mConfig.adbPort);
        }

        Log.d(TAG, "useAdbStartServer: opening shell...");
        adbStream = connection.open("shell:");

        // Logging thread
        new Thread(() -> {
            if (adbStream == null) return;
            LineReader reader;
            StringBuilder sb = new StringBuilder();
            try {
                reader = new LineReader(adbStream);
                int line = 0;
                String s;
                while (!adbStream.isClosed()) {
                    s = reader.readLine();
                    if (s != null) sb.append(s);
                    line++;
                    if (!mConfig.printLog && (line >= 50 || (s != null && s.startsWith("runGet")))) {
                        break;
                    }
                }
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "useAdbStartServer: unable to read from shell.", e);
            }
            Log.e(TAG, "useAdbStartServer: " + sb);
        }).start();

        adbStream.write("\n\n".getBytes());
        SystemClock.sleep(100);
        adbStream.write("id\n".getBytes());
        SystemClock.sleep(100);
        String command = getExecCommand();
        Log.d(TAG, "useAdbStartServer: " + command);
        adbStream.write((command + "\n").getBytes());
        SystemClock.sleep(3000);

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
     * @throws RuntimeException If supplied token is empty
     */
    @WorkerThread
    private ClientSession createSession() throws IOException {
        if (isRunning()) {
            return mSession;
        }
        if (!AppPref.isRootOrAdbEnabled()) {
            throw new IOException("Root/ADB not enabled.");
        }
        Socket socket = new Socket(ServerConfig.getLocalServerHost(), ServerConfig.getLocalServerPort());
        socket.setSoTimeout(1000 * 30);
        OutputStream os = socket.getOutputStream();
        InputStream is = socket.getInputStream();
        String token = ServerConfig.getLocalToken();
        if (TextUtils.isEmpty(token)) {
            throw new RuntimeException("No token supplied.");
        }
        DataTransmission transfer = new DataTransmission(os, is, false);
        transfer.shakeHands(token, false);
        return new ClientSession(transfer);
    }

    /**
     * The client session handler
     */
    private static class ClientSession implements AutoCloseable {
        private volatile boolean isRunning;
        private DataTransmission transmission;

        @AnyThread
        ClientSession(DataTransmission transmission) {
            this.transmission = transmission;
            this.isRunning = true;
        }

        /**
         * Close the session, stop any active transmission
         */
        @AnyThread
        @Override
        public void close() {
            isRunning = false;
            if (transmission != null) {
                transmission.stop();
            }
            transmission = null;
        }

        /**
         * Whether the client session is running
         */
        @AnyThread
        boolean isRunning() {
            return isRunning && transmission != null;
        }

        @AnyThread
        DataTransmission getTransmission() {
            return transmission;
        }
    }
}
