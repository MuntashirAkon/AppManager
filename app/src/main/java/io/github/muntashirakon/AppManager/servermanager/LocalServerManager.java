// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.SuppressLint;
import android.content.Context;
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
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.adb.AdbConnectionManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.server.common.BaseCaller;
import io.github.muntashirakon.AppManager.server.common.Caller;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.Constants;
import io.github.muntashirakon.AppManager.server.common.DataTransmission;
import io.github.muntashirakon.AppManager.server.common.ParcelableUtil;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.adb.AdbPairingRequiredException;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.io.IoUtils;

// Copyright 2016 Zheng Li
class LocalServerManager {
    private static final String TAG = "LocalServerManager";

    @SuppressLint("StaticFieldLeak")
    private static LocalServerManager sLocalServerManager;

    @AnyThread
    @NoOps
    @NonNull
    static LocalServerManager getInstance(@NonNull Context context) {
        synchronized (LocalServerManager.class) {
            if (sLocalServerManager == null) {
                sLocalServerManager = new LocalServerManager(context);
            }
        }
        return sLocalServerManager;
    }

    private final Object mLock = new Object();
    @NonNull
    private final Context mContext;
    @Nullable
    private ClientSession mSession;

    @AnyThread
    private LocalServerManager(@NonNull Context context) {
        mContext = context;
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
    @NoOps(used = true)
    private ClientSession getSession() throws IOException, AdbPairingRequiredException {
        synchronized (mLock) {
            if (mSession == null || !mSession.isRunning()) {
                try {
                    mSession = createSession();
                } catch (SocketTimeoutException e) {
                    Log.i(TAG, "Server is running but not responsive. Stopping the server...");
                    try {
                        stopServer();
                    } catch (Exception ex) {
                        throw new IOException(ex);
                    }
                    // Successfully stopped the server.
                    // We try to start server again below.
                } catch (Exception e) {
                    if (!Ops.isDirectRoot() && !Ops.isAdb()) {
                        // Do not bother attempting to create a new session
                        throw new IOException("Could not create session", e);
                    }
                }
                if (mSession == null) {
                    try {
                        startServer();
                    } catch (AdbPairingRequiredException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new IOException("Could not start server", e);
                    }
                    mSession = createSession();
                }
            }
            return mSession;
        }
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
        IoUtils.closeQuietly(mSession);
        mSession = null;
    }

    /**
     * Close the client session.
     */
    void stop() {
        IoUtils.closeQuietly(mSession);
        mSession = null;
    }

    @WorkerThread
    @NoOps(used = true)
    void start() throws IOException, AdbPairingRequiredException {
        getSession();
    }

    @WorkerThread
    @NonNull
    private DataTransmission getSessionDataTransmission() throws IOException {
        try {
            return getSession().getDataTransmission();
        } catch (AdbPairingRequiredException e) {
            throw new IOException(e);
        }
    }

    @WorkerThread
    @NonNull
    private byte[] execPre(@NonNull byte[] params) throws IOException {
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
    void closeBgServer() throws IOException {
        try {
            BaseCaller baseCaller = new BaseCaller(BaseCaller.TYPE_CLOSE);
            getSession().getDataTransmission().sendAndReceiveMessage(ParcelableUtil.marshall(baseCaller));
        } catch (Exception e) {
            // Since the server is closed abruptly, this should always produce error
            Log.w(TAG, "closeBgServer: Error", e);
        }
        // Check if the server is still active
        if (LocalServer.alive(mContext)) {
            // Server still active, need to run killall am_local_server
            try {
                stopServer();
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    @WorkerThread
    private void useAdbStartServer() throws Exception {
        try (AdbStream adbStream = openAdbShell();
             InputStream is = adbStream.openInputStream();
             OutputStream os = adbStream.openOutputStream()) {
            // ADB may require a fallback method
            String command = ServerConfig.getServerRunnerCommand();
            Log.d(TAG, "useAdbStartServer: %s", command);
            executeAdbCommand(is, os, command, "Success!", 1, TimeUnit.MINUTES);
        }
        Log.d(TAG, "useAdbStartServer: Server has started.");
    }

    @WorkerThread
    @NonNull
    private AdbStream openAdbShell() throws Exception {
        String adbHost = ServerConfig.getAdbHost(mContext);
        int adbPort = ServerConfig.getAdbPort();
        AdbConnectionManager manager = AdbConnectionManager.getInstance();
        Log.d(TAG, "Connecting to ADB using host=%s, port=%d", adbHost, adbPort);
        manager.setTimeout(10, TimeUnit.SECONDS);
        // Wireless debugging can select a different port when it is re-enabled.
        manager.disconnect();
        if (!manager.connect(adbHost, adbPort)) {
            throw new IOException("Could not connect to ADB.");
        }
        Log.d(TAG, "Opening ADB shell...");
        return manager.openStream("shell:");
    }

    @WorkerThread
    static void executeAdbCommand(@NonNull InputStream inputStream,
                                  @NonNull OutputStream outputStream,
                                  @NonNull String command,
                                  @NonNull String successPrefix,
                                  long timeout,
                                  @NonNull TimeUnit timeoutUnit) throws IOException, InterruptedException {
        CountDownLatch commandWatcher = new CountDownLatch(1);
        AtomicBoolean commandSucceeded = new AtomicBoolean(false);
        AtomicReference<Throwable> readFailure = new AtomicReference<>();
        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String response;
                while ((response = reader.readLine()) != null) {
                    Log.d(TAG, "RESPONSE: %s", response);
                    if (response.startsWith(successPrefix)) {
                        commandSucceeded.set(true);
                        break;
                    }
                    if (response.startsWith("Error!")) {
                        readFailure.set(new IOException(response));
                        break;
                    }
                }
            } catch (Throwable e) {
                readFailure.set(e);
            } finally {
                commandWatcher.countDown();
            }
        }, "am-adb-command-output");
        outputThread.start();
        try {
            outputStream.write("id\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            if (!commandWatcher.await(timeout, timeoutUnit)) {
                throw new SocketTimeoutException("Timed out waiting for ADB command: " + command);
            }
            if (!commandSucceeded.get()) {
                Throwable failure = readFailure.get();
                throw new IOException("ADB command did not produce " + successPrefix, failure);
            }
        } finally {
            outputThread.interrupt();
        }
    }

    @WorkerThread
    private void useRootStartServer() throws Exception {
        if (!Ops.hasRoot()) {
            throw new Exception("Root access denied");
        }
        String command = ServerConfig.getServerRunnerCommand();
        // + "\n" + "supolicy --live 'allow qti_init_shell zygote_exec file execute'";
        Log.d(TAG, "useRootStartServer: %s", command);
        Runner.Result result = Runner.runCommand(command);

        Log.d(TAG, "useRootStartServer: %s", result.getOutput());
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
    @NoOps(used = true)
    private void startServer() throws Exception {
        if (Ops.isAdb()) {
            useAdbStartServer();
        } else if (Ops.isDirectRoot()) {
            useRootStartServer();
        } else throw new Exception("Neither root nor ADB mode is enabled.");
    }

    /**
     * Stop root or ADB server based on config
     */
    @WorkerThread
    @NoOps(used = true)
    private void stopServer() throws Exception {
        String command = "killall " + Constants.SERVER_NAME + "; echo Stopped!";
        if (Ops.isAdb()) {
            try (AdbStream adbStream = openAdbShell();
                 InputStream is = adbStream.openInputStream();
                 OutputStream os = adbStream.openOutputStream()) {
                Log.d(TAG, "stopServer (ADB): %s", command);
                executeAdbCommand(is, os, command, "Stopped!", 1, TimeUnit.MINUTES);
            }
            Log.d(TAG, "stopServer (ADB): Server has stopped.");
        } else if (Ops.isDirectRoot()) {
            if (!Ops.hasRoot()) {
                throw new Exception("Root access denied");
            }
            Log.d(TAG, "stopServer (root): %s", command);
            Runner.Result result = Runner.runCommand(command);
            Log.d(TAG, "stopServer (root): %s", result.getOutput());
            if (!result.isSuccessful()) {
                throw new Exception("Could not stop server.");
            }
            SystemClock.sleep(3000);
            Log.d(TAG, "stopServer (root): Server has stopped.");
        } else throw new Exception("Neither root nor ADB mode is enabled.");
    }

    /**
     * Create a client session
     *
     * @return New session if not running, running session otherwise
     * @throws IOException If session creation failed
     */
    @WorkerThread
    @NonNull
    @NoOps(used = true)
    private ClientSession createSession() throws IOException {
        if (isRunning()) {
            // Non-null check has already been done
            return Objects.requireNonNull(mSession);
        }
        String host = ServerConfig.getLocalServerHost(mContext);
        int port = ServerConfig.getLocalServerPort();
        Socket socket = new Socket(host, port);
        socket.setSoTimeout(10_000);
        OutputStream os = socket.getOutputStream();
        InputStream is = socket.getInputStream();
        DataTransmission transfer = new DataTransmission(os, is, false);
        transfer.shakeHands(ServerConfig.getLocalToken(), DataTransmission.Role.Client);
        return new ClientSession(socket, transfer);
    }

    /**
     * The client session handler
     */
    private static class ClientSession implements AutoCloseable {
        private volatile boolean mIsRunning;
        @NonNull
        private final Socket mSocket;
        @NonNull
        private final DataTransmission mDataTransmission;

        @AnyThread
        ClientSession(@NonNull Socket socket, @NonNull DataTransmission dataTransmission) {
            mSocket = socket;
            mDataTransmission = dataTransmission;
            mIsRunning = true;
        }

        /**
         * Close the session, stop any active transmission
         */
        @AnyThread
        @Override
        public void close() throws IOException {
            if (mIsRunning) {
                mIsRunning = false;
                mDataTransmission.close();
                mSocket.close();
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
