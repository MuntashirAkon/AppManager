// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server;

import android.app.ActivityThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.system.Os;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.server.common.ConfigParam;
import io.github.muntashirakon.AppManager.server.common.Constants;
import io.github.muntashirakon.AppManager.server.common.FLog;

import static io.github.muntashirakon.AppManager.server.common.ConfigParam.PARAM_TYPE;
import static io.github.muntashirakon.AppManager.server.common.ConfigParam.PARAM_TYPE_ADB;
import static io.github.muntashirakon.AppManager.server.common.ConfigParam.PARAM_TYPE_ROOT;

/**
 * ServerRunner runs the server based on the parameters given. It takes two arguments:
 * <ol>
 *     <li>
 *         <b>Parameters.</b> Each parameter is a key-value pair separated by a comma and key-values
 *         are separated by a colon. See {@link ConfigParam} to see a list of parameters.
 *     </li>
 *     <li>
 *         <b>Process ID.</b> The old process ID that has to be killed. This is an optional argument
 *     </li>
 * </ol>
 */
// Copyright 2017 Zheng Li
public final class ServerRunner {
    /**
     * The main method
     *
     * @param args See {@link ServerRunner}
     */
    public static void main(String[] args) {
        try {
            FLog.writeLog = true;
            FLog.log("Arguments: " + Arrays.toString(args));
            if (args == null || args.length == 0) {
                return;
            }
            // Get arguments
            String paramsStr = args[0];
            int oldPid = -1;
            if (args.length > 1) {
                try {
                    oldPid = Integer.parseInt(args[1]);
                } catch (Exception ignore) {
                }
            }
            // Make it main looper
            //noinspection deprecation
            Looper.prepareMainLooper();
            ActivityThread.systemMain();
            // Parse arguments
            String[] split = paramsStr.split(",");
            final Map<String, String> params = new HashMap<>();
            for (String s : split) {
                String[] param = s.split(":");
                params.put(param[0], param[1]);
            }
            params.put(PARAM_TYPE, Process.myUid() == 0 ? PARAM_TYPE_ROOT : PARAM_TYPE_ADB);
            // Set server info
            LifecycleAgent.serverRunInfo.startArgs = paramsStr;
            LifecycleAgent.serverRunInfo.startTime = System.currentTimeMillis();
            LifecycleAgent.serverRunInfo.startRealTime = SystemClock.elapsedRealtime();
            // Print debug
            System.out.println("Type: " + params.get(PARAM_TYPE) + ", UID: " + Process.myUid());
            System.out.println("Params: " + params);
            // Kill old server if requested
            if (oldPid != -1) {
                killOldServer(oldPid);
                SystemClock.sleep(1000);
            }
            // Start server
            Thread thread = new Thread(() -> {
                new ServerRunner().runServer(params);
                // Exit current thread, regardless of whether the server started or not
                FLog.close();
                killSelfProcess();
            });
            thread.setName("AM-IPC");
            thread.start();
            Looper.loop();
        } catch (Throwable e) {
            e.printStackTrace();
            FLog.log(e);
        } finally {
            // Exit current process, regardless of whether the server started or not
            FLog.log("Log closed.");
            FLog.close();
            killSelfProcess();
        }
    }

    /**
     * Kill old server by process ID, process name is verified before killed.
     *
     * @param oldPid Process ID of the old server
     */
    private static void killOldServer(int oldPid) {
        try {
            String processName = getProcessName(oldPid);
            if (Constants.SERVER_NAME.equals(processName)) {
                Process.killProcess(oldPid);
                FLog.log("Killed old server with pid " + oldPid);
            }
        } catch (Throwable throwable) {
            FLog.log(throwable);
        }
    }

    /**
     * Kill current process
     */
    private static void killSelfProcess() {
        int pid = Process.myPid();
        System.out.println("Killing self process with pid " + pid);
        killProcess(pid);
    }

    /**
     * Kill a process by process ID
     *
     * @param pid Process ID to be killed
     */
    private static void killProcess(int pid) {
        try {
            Process.killProcess(pid);
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            try {
                // Kill using SIGNAL 9
                Os.execve("/system/bin/kill", new String[]{"-9", String.valueOf(pid)}, null);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get the process name from process ID
     *
     * @param pid Process ID
     * @return Process name or empty string
     */
    @NonNull
    private static String getProcessName(int pid) {
        File cmdLine = new File("/proc/" + pid + "/cmdline");
        if (!cmdLine.exists()) return "";
        try (FileInputStream fis = new FileInputStream(cmdLine)) {
            byte[] buff = new byte[512];
            int len = fis.read(buff);
            if (len > 0) {
                int i;
                for (i = 0; i < len; i++) {
                    if (buff[i] == '\0') {
                        break;
                    }
                }
                return new String(buff, 0, i);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private ServerRunner() {
    }

    /**
     * Run the local server. The server is actually run by {@link ServerHandler}.
     *
     * @param configParams The parameters to be used during and after the server starts.
     */
    private void runServer(Map<String, String> configParams) {
        try (ServerHandler serverHandler = new ServerHandler(configParams)) {
            // Set params
            LifecycleAgent.sConfigParams = new HashMap<>(configParams);
            System.out.println("Success! Server has started.");
            int pid = Process.myPid();
            System.out.println("Process: " + getProcessName(pid) + ", PID: " + pid);
            // Send broadcast message to the system that the server has started
            LifecycleAgent.onStarted();
            // Start server
            serverHandler.start();
        } catch (IOException | RuntimeException e) {
            System.out.println("Error! Could not start server. " + e.getMessage());
            FLog.log(e);
        } finally {
            // Send broadcast message to the system that the server has stopped
            LifecycleAgent.onStopped();
        }
    }
}
