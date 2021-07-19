// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Inet4Address;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.utils.FileUtils;

// Copyright 2016 Zheng Li
public final class ServerConfig {
    public static final int DEFAULT_ADB_PORT = 5555;
    static String SOCKET_PATH = "am_socket";
    private static int DEFAULT_LOCAL_SERVER_PORT = 60001;
    private static final String LOCAL_TOKEN = "l_token";

    static final String JAR_NAME = "am.jar";
    static final String EXECUTABLE_FILE_NAME = "run_server.sh";

    private static File destJarFile;
    private static File destExecFile;
    private static final SharedPreferences sPreferences = AppManager.getContext()
            .getSharedPreferences("server_config", Context.MODE_PRIVATE);
    private static volatile boolean sInitialised = false;

    @AnyThread
    static void init(Context context, int userHandle) throws IOException {
        if (sInitialised) {
            return;
        }

        File internalStorage = context.getFilesDir().getParentFile();
        if (internalStorage == null || !internalStorage.exists()) {
            throw new FileNotFoundException("Internal storage unavailable");
        }

        // Set folder permission
        FileUtils.chmod711(internalStorage);

        destJarFile = new File(internalStorage, JAR_NAME);
        destExecFile = new File(internalStorage, EXECUTABLE_FILE_NAME);

        if (userHandle != 0) {
            SOCKET_PATH += userHandle;
            DEFAULT_LOCAL_SERVER_PORT += userHandle;
        }

        sInitialised = true;
    }

    @AnyThread
    @NonNull
    public static File getDestJarFile() {
        return destJarFile;
    }

    @AnyThread
    @NonNull
    static String getClassPath() {
        return destJarFile.getAbsolutePath();
    }

    @AnyThread
    @NonNull
    public static File getExecPath() {
        return destExecFile;
    }

    /**
     * Get existing or generate new 16-digit token for client session
     *
     * @return Existing or new token
     */
    @AnyThread
    @NonNull
    static String getLocalToken() {
        String token = sPreferences.getString(LOCAL_TOKEN, null);
        if (TextUtils.isEmpty(token)) {
            token = AssetsUtils.generateToken();
            sPreferences.edit().putString(LOCAL_TOKEN, token).apply();
        }
        return token;
    }

    @AnyThread
    static boolean getAllowBgRunning() {
        return sPreferences.getBoolean("allow_bg_running", true);
    }

    @AnyThread
    public static int getAdbPort() {
        return sPreferences.getInt("adb_port", DEFAULT_ADB_PORT);
    }

    @AnyThread
    public static void setAdbPort(int port) {
        sPreferences.edit().putInt("adb_port", port).apply();
    }

    @AnyThread
    static int getLocalServerPort() {
        return DEFAULT_LOCAL_SERVER_PORT;
    }

    @WorkerThread
    @NonNull
    public static String getAdbHost() {
        return Inet4Address.getLoopbackAddress().getHostAddress();
    }

    @WorkerThread
    @NonNull
    public static String getLocalServerHost() {
        return Inet4Address.getLoopbackAddress().getHostAddress();
    }
}
