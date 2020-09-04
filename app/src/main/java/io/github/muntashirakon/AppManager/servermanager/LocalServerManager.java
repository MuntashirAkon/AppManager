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
import android.util.Log;

import com.tananaev.adblib.AdbConnection;
import com.tananaev.adblib.AdbStream;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.adb.AdbConnectionManager;
import io.github.muntashirakon.AppManager.adb.LineReader;
import io.github.muntashirakon.AppManager.server.common.BaseCaller;
import io.github.muntashirakon.AppManager.server.common.Caller;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.DataTransmission;
import io.github.muntashirakon.AppManager.server.common.ParcelableUtil;

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
     * @throws Exception When creating session fails or server couldn't be started
     */
    private ClientSession getSession() throws Exception {
        if (mSession == null || !mSession.isRunning()) {
            try {
                mSession = createSession();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (mSession == null) {
                startServer();
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

    void start() throws Exception {
        getSession();
    }

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

    CallerResult execNew(@NonNull Caller caller) throws Exception {
        byte[] result = execPre(ParcelableUtil.marshall(new BaseCaller(caller.wrapParams())));
        return ParcelableUtil.unmarshall(
                result, CallerResult.CREATOR);
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
    private List<String> getCommands() {
        AssetsUtils.writeScript(mConfig);
        Log.e(TAG, "classpath --> " + ServerConfig.getClassPath());
        Log.e(TAG, "exec path --> " + ServerConfig.getExecPath());
        List<String> commands = new ArrayList<>();
        commands.add("sh " + ServerConfig.getExecPath());
        return commands;
    }

    private AdbConnection connection;
    private AdbStream adbStream;

    private boolean useAdbStartServer() throws Exception {
        if (adbStream != null && !adbStream.isClosed()) {
            return true;
        }
        if (connection != null) {
            connection.close();
        }

        final AtomicBoolean connResult = new AtomicBoolean(false);
        connection = AdbConnectionManager.buildConnect(mConfig.context, mConfig.adbHost, mConfig.adbPort);

        Thread thread = new Thread(() -> {
            try {
                connection.connect();
                connResult.set(true);
            } catch (Exception e) {
                connResult.set(false);
                e.printStackTrace();
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });

        try {
            Log.e(TAG, "useAdbStartServer --> start");
            thread.start();
            thread.join(10000);
            Log.e(TAG, "useAdbStartServer --> jion 10000");

            if (!connResult.get()) {
                connection.close();
            }
        } catch (InterruptedException e) {
            connResult.set(false);
            e.printStackTrace();
            if (connection != null) {
                connection.close();
            }
        }

        if (!connResult.get()) {
            throw new RuntimeException("please grant adb permission!");
        }

        adbStream = connection.open("shell:");

        if (!TextUtils.isEmpty(mConfig.logFile)) {
            new Thread(() -> {
                BufferedWriter bw = null;
                LineReader reader;
                try {
                    bw = new BufferedWriter(new FileWriter(mConfig.logFile, false));
                    bw.write(new Date().toString());
                    bw.newLine();
                    bw.write("adb start log");
                    bw.newLine();

                    reader = new LineReader(adbStream);
                    int line = 0;
                    String s = reader.readLine();
                    while (!adbStream.isClosed()) {
                        Log.e(TAG, "log run --> " + s);
                        s = reader.readLine();
                        if (s != null) {
                            bw.write(s);
                            bw.newLine();
                        }
                        line++;
                        if (!mConfig.printLog && (line >= 50 || (s != null && s.startsWith("runGet")))) {
                            break;
                        }
                    }
                    bw.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (bw != null) {
                            bw.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        adbStream.write("\n\n".getBytes());
        SystemClock.sleep(100);
        adbStream.write("id\n".getBytes());
        SystemClock.sleep(100);
        List<String> commands = getCommands();

        for (String cmd : commands) {
            adbStream.write((cmd + "\n").getBytes());
            SystemClock.sleep(100);
        }
        SystemClock.sleep(3000);

        Log.e(TAG, "startServer -->ADB server start ----- ");

        return true;
    }

    private boolean useRootStartServer() throws Exception {
        DataOutputStream outputStream = null;
        RootChecker checker = null;
        Process exec = null;
        try {
            Log.e(TAG, "useRootStartServer --> ");

            exec = Runtime.getRuntime().exec("su");
            checker = new RootChecker(exec);
            checker.start();

            try {
                checker.join(20000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (checker.exit == -1) {
                throw new RuntimeException("grant root timeout");
            }

            if (checker.exit != 1) {
                throw new RuntimeException(checker.errorMsg);
            }

            outputStream = new DataOutputStream(exec.getOutputStream());

            List<String> cmds = getCommands();

            //部分情况下selinux导致执行失败 exec  app_process
            if (mConfig.rootOverAdb) {
                cmds.clear();

                cmds.add("echo 'root over adb mode'");
                cmds.add("getenforce");
                cmds.add("setprop service.adb.tcp.port " + mConfig.adbPort);
                cmds.add("stop adbd");
                cmds.add("start adbd");
                cmds.add("echo $?");
                cmds.add("echo end");

                final OutputStream waitWriter = outputStream;
                final Process waitProcess = exec;
                new Thread(() -> {
                    SystemClock.sleep(1000 * 20);
                    try {
                        Log.e(TAG, "run --> stop adb ");

                        List<String> cls = new ArrayList<String>() {
                            {
                                add("echo 'stop adb!!!'");
                                add("setprop service.adb.tcp.port -1");
                                add("stop adbd");
                                add("start adbd");
                                add("getprop service.adb.tcp.port");
                            }
                        };
                        writeCmds(cls, waitWriter);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            waitProcess.destroy();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).start();

            }

            //cmds.add(0,"supolicy --live \'allow qti_init_shell zygote_exec file execute\'");
            writeCmds(cmds, outputStream);

            final BufferedReader inputStream = new BufferedReader(new InputStreamReader(
                    exec.getInputStream(), StandardCharsets.UTF_8));

            //记录日志
            if (!TextUtils.isEmpty(mConfig.logFile)) {
                new Thread(() -> {
                    BufferedWriter bw = null;
                    try {
                        boolean saveLog = !TextUtils.isEmpty(mConfig.logFile);
                        if (saveLog) {
                            bw = new BufferedWriter(new FileWriter(mConfig.logFile, false));
                            bw.write(new Date().toString());
                            bw.newLine();
                            bw.write("root start log");
                            bw.newLine();
                        }

                        int line = 0;
                        String s = inputStream.readLine();
                        while (s != null) {
                            Log.e(TAG, "log run --> " + s);
                            s = inputStream.readLine();
                            if (saveLog && s != null) {
                                bw.write(s);
                                bw.newLine();
                            }
                            line++;
                            if (!mConfig.printLog && (line >= 50 || (s != null && s.startsWith("runGet")))) {
                                break;
                            }

                        }
                        if (bw != null) {
                            bw.flush();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            if (bw != null) {
                                bw.close();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }

            SystemClock.sleep(3000);

            if (mConfig.rootOverAdb) {
                Log.e(TAG, "startServer --- use root over adb,open adb server----");
                return useAdbStartServer();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            if (checker != null) {
                checker.interrupt();
            }
            throw e;
        } finally {
            try {
                if (exec != null) {
                    //exec.destroy();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private void writeCmds(@NonNull List<String> commands, OutputStream outputStream)
            throws IOException {
        for (String cmd : commands) {
            outputStream.write((cmd + "\n").getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        }
        outputStream.flush();
    }

    /**
     * Start root or ADB server based on config
     *
     * @throws Exception When server cannot be started
     */
    private void startServer() throws Exception {
        if (mConfig.useAdb) {
            useAdbStartServer();
        } else {
            useRootStartServer();
        }
        Log.e(TAG, "startServer --> end ---");
    }

    /**
     * The root checker thread
     */
    private static class RootChecker extends Thread {
        int exit = -1;
        String errorMsg = null;
        Process process;

        private RootChecker(Process process) {
            this.process = process;
        }

        @Override
        public void run() {
            try {
                BufferedReader inputStream = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter outputStream = new BufferedWriter(
                        new OutputStreamWriter(this.process.getOutputStream(), StandardCharsets.UTF_8));
                outputStream.write("echo U333L\n");
                outputStream.flush();

                while (true) {
                    String line = inputStream.readLine();
                    if (line == null) {
                        throw new EOFException();
                    }
                    if ("".equals(line)) {
                        continue;
                    }
                    if ("U333L".equals(line)) {
                        this.exit = 1;
                        break;
                    }
                    errorMsg = "Unknown error occurred.";
                }
            } catch (IOException e) {
                exit = -42;
                if (e.getMessage() != null) {
                    errorMsg = e.getMessage();
                } else {
                    errorMsg = "RootAccess denied.";
                }
            }

        }
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
        Socket socket = new Socket(ServerConfig.getHost(), ServerConfig.getPort());
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
