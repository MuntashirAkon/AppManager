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
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.adb.AdbConnectionManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.server.common.BaseCaller;
import io.github.muntashirakon.AppManager.server.common.Caller;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.DataTransmission;
import io.github.muntashirakon.AppManager.server.common.ParcelableUtil;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.io.IoUtils;

// Copyright 2016 Zheng Li
class LocalServerManager {
    private static final String TAG = "LocalServerManager";

    @SuppressLint("StaticFieldLeak")
    private static LocalServerManager sLocalServerManager;

    @WorkerThread
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

    @WorkerThread
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
    private ClientSession getSession() throws IOException {
        synchronized (mLock) {
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
     * Stop ADB and then close client session
     */
    void stop() {
        IoUtils.closeQuietly(mAdbStream);
        IoUtils.closeQuietly(mSession);
        mAdbStream = null;
        mSession = null;
    }

    @WorkerThread
    @NoOps(used = true)
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
            getSession().getDataTransmission().sendAndReceiveMessage(ParcelableUtil.marshall(baseCaller));
        } catch (Exception e) {
            // Since the server is closed abruptly, this should always produce error
            Log.w(TAG, "closeBgServer: Error", e);
        }
    }

    @WorkerThread
    @NonNull
    private String getExecCommand() throws IOException {
        AssetsUtils.writeScript(mContext);
        Log.e(TAG, "classpath --> %s", ServerConfig.getClassPath());
        Log.e(TAG, "exec path --> %s", ServerConfig.getExecPath());
        return "sh " + ServerConfig.getExecPath() + " " + ServerConfig.getLocalServerPort() + " " + ServerConfig.getLocalToken();
    }

    @Nullable
    private volatile AdbStream mAdbStream;
    private volatile CountDownLatch mAdbConnectionWatcher = new CountDownLatch(1);
    private volatile boolean mAdbServerStarted;
    private final Runnable mAdbOutputThread = () -> {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(mAdbStream).openInputStream()))) {
            String s;
            while ((s = reader.readLine()) != null) {
                Log.d(TAG, "RESPONSE: %s", s);
                if (s.startsWith("Success!")) {
                    mAdbServerStarted = true;
                    mAdbConnectionWatcher.countDown();
                    break;
                } else if (s.startsWith("Error!")) {
                    mAdbServerStarted = false;
                    mAdbConnectionWatcher.countDown();
                    break;
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "useAdbStartServer: unable to read from shell.", e);
        }
    };

    @WorkerThread
    private void useAdbStartServer() throws Exception {
        if (mAdbStream == null || Objects.requireNonNull(mAdbStream).isClosed()) {
            // ADB shell not running
            String adbHost = ServerConfig.getAdbHost(mContext);
            int adbPort = ServerConfig.getAdbPort();
            AdbConnectionManager manager = AdbConnectionManager.getInstance();
            Log.d(TAG, "useAdbStartServer: Connecting using host=%s, port=%d", adbHost, adbPort);
            manager.setTimeout(10, TimeUnit.SECONDS);
            if (!manager.isConnected() && !manager.connect(adbHost, adbPort)) {
                throw new IOException("Could not connect to ADB.");
            }

            Log.d(TAG, "useAdbStartServer: Opening shell...");
            mAdbStream = manager.openStream("shell:");
            mAdbConnectionWatcher = new CountDownLatch(1);
            mAdbServerStarted = false;
            new Thread(mAdbOutputThread).start();
        }
        Log.d(TAG, "useAdbStartServer: Shell opened.");

        try (OutputStream os = Objects.requireNonNull(mAdbStream).openOutputStream()) {
            os.write("id\n".getBytes());
            String command = getExecCommand();
            Log.d(TAG, "useAdbStartServer: %s", command);
            os.write((command + "\n").getBytes());
        }

        if (!mAdbConnectionWatcher.await(1, TimeUnit.MINUTES) || !mAdbServerStarted) {
            throw new Exception("Server wasn't started.");
        }
        Log.d(TAG, "useAdbStartServer: Server has started.");
    }

    @WorkerThread
    private void useRootStartServer() throws Exception {
        if (!Ops.hasRoot()) {
            throw new Exception("Root access denied");
        }
        String command = getExecCommand(); // + "\n" + "supolicy --live 'allow qti_init_shell zygote_exec file execute'";
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
        } else if (Ops.isRoot()) {
            useRootStartServer();
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
        if (!Ops.isPrivileged()) {
            throw new IOException("Root/ADB not enabled.");
        }
        Socket socket = new Socket(ServerConfig.getLocalServerHost(mContext), ServerConfig.getLocalServerPort());
        socket.setSoTimeout(1000 * 30);
        // NOTE: (CWE-319) No need for SSL since it only runs on a random port in localhost with specific authorization.
        // TODO: 5/8/23 We could use an SSL server with a randomly generated certificate per session without requiring
        //  any other authorization methods. This session is independent of the application.
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
