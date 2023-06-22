// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;

import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;

// Copyright 2016 Zheng Li
public final class ServerConfig {
    public static final int DEFAULT_ADB_PORT = 5555;
    static String SOCKET_PATH = "am_socket";
    private static int DEFAULT_LOCAL_SERVER_PORT = 60001;
    private static final String LOCAL_TOKEN = "l_token";

    static final String JAR_NAME = "am.jar";
    static final String EXECUTABLE_FILE_NAME = "run_server.sh";

    private static File sDestJarFile;
    private static File sDestExecFile;
    private static final SharedPreferences sPreferences = ContextUtils.getContext()
            .getSharedPreferences("server_config", Context.MODE_PRIVATE);
    private static volatile boolean sInitialised = false;

    @AnyThread
    @NoOps
    static void init(Context context, int userHandle) throws IOException {
        if (sInitialised) {
            return;
        }

        File externalStorage = FileUtils.getExternalCachePath(context);
        sDestJarFile = new File(externalStorage, JAR_NAME);
        sDestExecFile = new File(externalStorage, EXECUTABLE_FILE_NAME);

        if (userHandle != 0) {
            SOCKET_PATH += userHandle;
            DEFAULT_LOCAL_SERVER_PORT += userHandle;
        }

        sInitialised = true;
    }

    @AnyThread
    @NonNull
    public static File getDestJarFile() {
        return sDestJarFile;
    }

    @AnyThread
    @NonNull
    static String getClassPath() {
        return sDestJarFile.getAbsolutePath();
    }

    @AnyThread
    @NonNull
    public static File getExecPath() {
        return sDestExecFile;
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
    @IntRange(from = 0, to = 65535)
    @NoOps
    public static int getAdbPort() {
        return sPreferences.getInt("adb_port", DEFAULT_ADB_PORT);
    }

    @AnyThread
    @NoOps
    public static void setAdbPort(@IntRange(from = 0, to = 65535) int port) {
        sPreferences.edit().putInt("adb_port", port).apply();
    }

    @AnyThread
    static int getLocalServerPort() {
        return DEFAULT_LOCAL_SERVER_PORT;
    }

    @WorkerThread
    @NonNull
    public static String getAdbHost(Context context) {
        return getHostIpAddress(context);
    }

    @WorkerThread
    @NonNull
    public static String getLocalServerHost(Context context) {
        return getHostIpAddress();
    }

    @WorkerThread
    @NonNull
    private static String getHostIpAddress() {
        String ipAddress = Inet4Address.getLoopbackAddress().getHostAddress();
        if (ipAddress == null || ipAddress.equals("::1")) return "127.0.0.1";
        return ipAddress;
    }

    @WorkerThread
    @NonNull
    private static String getHostIpAddress(@NonNull Context context) {
        if (isEmulator(context)) return "10.0.2.2";
        String ipAddress = Inet4Address.getLoopbackAddress().getHostAddress();
        if (ipAddress == null || ipAddress.equals("::1")) return "127.0.0.1";
        return ipAddress;
    }

    // https://github.com/firebase/firebase-android-sdk/blob/7d86138304a6573cbe2c61b66b247e930fa05767/firebase-crashlytics/src/main/java/com/google/firebase/crashlytics/internal/common/CommonUtils.java#L402
    private static final String GOLDFISH = "goldfish";
    private static final String RANCHU = "ranchu";
    private static final String SDK = "sdk";

    private static boolean isEmulator(@NonNull Context context) {
        @SuppressLint("HardwareIds")
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return Build.PRODUCT.contains(SDK)
                || Build.HARDWARE.contains(GOLDFISH)
                || Build.HARDWARE.contains(RANCHU)
                || androidId == null;
    }
}
