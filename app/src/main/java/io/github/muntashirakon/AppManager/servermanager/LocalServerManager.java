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

import android.os.SystemClock;
import android.text.TextUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

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

class LocalServerManager {
    private static final String TAG = "LocalServerManager";

    private static LocalServerManager sLocalServerManager;

    static LocalServerManager getInstance(LocalServer.Config config) {
        if (sLocalServerManager == null) {
            synchronized (LocalServerManager.class) {
                if (sLocalServerManager == null) {
                    sLocalServerManager = new LocalServerManager(config);
                }
            }
        }
        return sLocalServerManager;
    }

    private ClientSession mSession = null;
    private LocalServer.Config mConfig;

    private LocalServerManager(LocalServer.Config config) {
        mConfig = config;
    }

    /**
     * Update preferences
     *
     * @param config The new preferences
     */
    void updateConfig(LocalServer.Config config) {
        if (config != null) {
            mConfig = config;
        }
    }

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
                if (!startServer()) {
                    throw new IOException("Failed to start server.");
                }
                mSession = createSession();
            }
        }
        return mSession;
    }

    public boolean isRunning() {
        return mSession != null && mSession.isRunning();
    }

    /**
     * Close client session
     */
    void closeSession() {
        if (mSession != null) {
            mSession.close();
            mSession = null;
        }
    }

    /**
     * Stop ADB and then close client session
     */
    void stop() {
        try {
            if (adbStream != null) {
                adbStream.close();
            }
            adbStream = null;
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mSession != null) {
            mSession.close();
            mSession = null;
        }
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

    void closeBgServer() {
        try {
            BaseCaller baseCaller = new BaseCaller(BaseCaller.TYPE_CLOSE);
            createSession().getTransmission().sendAndReceiveMessage(ParcelableUtil.marshall(baseCaller));
        } catch (Exception e) {
            Log.w(TAG, "closeBgServer: " + e.getCause() + "  " + e.getMessage());
        }
    }

    @NonNull
    private String getExecCommand() {
        AssetsUtils.writeScript(mConfig);
        Log.e(TAG, "classpath --> " + ServerConfig.getClassPath());
        Log.e(TAG, "exec path --> " + ServerConfig.getExecPath());
        return "sh " + ServerConfig.getExecPath() + " " + ServerConfig.getLocalServerPort() + " " + ServerConfig.getLocalToken();
    }

    private AdbConnection connection;
    private AdbStream adbStream;

    @WorkerThread
    private boolean useAdbStartServer() {
        if (adbStream != null && !adbStream.isClosed()) {
            return true;
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
                Log.e(TAG, "useAdbStartServer: unable to close previous connection.", e);
                return false;
            }
        }

        Log.d(TAG, "useAdbStartServer: connecting...");
        try {
            connection = AdbConnectionManager.connect(mConfig.context, mConfig.adbHost, mConfig.adbPort);
        } catch (Exception e) {
            Log.e(TAG, "useAdbStartServer: unable to connect.", e);
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException e1) {
                    Log.e(TAG, "useAdbStartServer: unable to close previous connection.", e);
                }
            }
            return false;
        }

        Log.d(TAG, "useAdbStartServer: opening shell...");
        try {
            adbStream = connection.open("shell:");
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "useAdbStartServer: unable to open shell.", e);
            return false;
        }

        // Logging thread
        new Thread(() -> {
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
            Log.e(TAG, "useAdbStartServer: " + sb.toString());
        }).start();

        try {
            adbStream.write("\n\n".getBytes());
            SystemClock.sleep(100);
            adbStream.write("id\n".getBytes());
            SystemClock.sleep(100);
            String command = getExecCommand();
            Log.d(TAG, "useAdbStartServer: " + command);
            adbStream.write((command + "\n").getBytes());
            SystemClock.sleep(3000);
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "useAdbStartServer: unable to write to shell.", e);
            return false;
        }

        Log.d(TAG, "useAdbStartServer: Server has started.");
        return true;
    }

    @WorkerThread
    private boolean useRootStartServer() {
        if (!RunnerUtils.isRootGiven()) {
            Log.e(TAG, "useRootStartServer: Root access denied.");
            return false;
        }
        String command = getExecCommand(); // + "\n" + "supolicy --live 'allow qti_init_shell zygote_exec file execute'";
        Log.d(TAG, "useRootStartServer: " + command);
        Runner.Result result = Runner.runCommand(Runner.getRootInstance(), command);

        Log.d(TAG, "useRootStartServer: " + result.getOutput());
        if (!result.isSuccessful()) {
            Log.e(TAG, "useRootStartServer: Failed to start server.");
            return false;
        }
        SystemClock.sleep(3000);
        Log.e(TAG, "useRootStartServer: Server has started.");
        return true;
    }

    /**
     * Start root or ADB server based on config
     */
    @WorkerThread
    private boolean startServer() {
        if (AppPref.isAdbEnabled()) {
            return useAdbStartServer();
        } else if (AppPref.isRootEnabled()) {
            return useRootStartServer();
        } else return false;
    }

    /**
     * Create a client session
     *
     * @return New session if not running, running session otherwise
     * @throws IOException      If session creation failed
     * @throws RuntimeException If supplied token is empty
     */
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

        ClientSession(DataTransmission transmission) {
            this.transmission = transmission;
            this.isRunning = true;
        }

        /**
         * Close the session, stop any active transmission
         */
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
        boolean isRunning() {
            return isRunning && transmission != null;
        }

        DataTransmission getTransmission() {
            return transmission;
        }
    }
}
