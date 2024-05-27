// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
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
import java.security.SecureRandom;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.server.common.Constants;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;

// Copyright 2016 Zheng Li
public final class ServerConfig {
    public static final String TAG = ServerConfig.class.getSimpleName();

    public static final int DEFAULT_ADB_PORT = 5555;
    static final String SERVER_RUNNER_EXEC_NAME = "run_server.sh";
    private static final int DEFAULT_LOCAL_SERVER_PORT = 60001;
    private static final String LOCAL_TOKEN = "l_token";
    private static final File[] SERVER_RUNNER_EXEC = new File[2];
    private static final File[] SERVER_RUNNER_JAR = new File[2];
    private static final SharedPreferences sPreferences = ContextUtils.getContext()
            .getSharedPreferences("server_config", Context.MODE_PRIVATE);
    private static int sServerPort = DEFAULT_LOCAL_SERVER_PORT;
    private static volatile boolean sInitialised = false;

    @WorkerThread
    @NoOps
    public static void init(@NonNull Context context, @UserIdInt int userHandle) throws IOException {
        if (sInitialised) {
            return;
        }

        // Setup paths
        File externalCachePath = FileUtils.getExternalCachePath(context);
        File externalMediaPath = FileUtils.getExternalMediaPath(context);
        File deStorage = ContextUtils.getDeContext(context).getCacheDir();
        SERVER_RUNNER_EXEC[0] = new File(externalCachePath, SERVER_RUNNER_EXEC_NAME);
        SERVER_RUNNER_EXEC[1] = new File(deStorage, SERVER_RUNNER_EXEC_NAME);
        SERVER_RUNNER_JAR[0] = new File(externalCachePath, Constants.JAR_NAME);
        SERVER_RUNNER_JAR[1] = new File(deStorage, Constants.JAR_NAME);
        // Copy JAR
        boolean force = BuildConfig.DEBUG;
        AssetsUtils.copyFile(context, Constants.JAR_NAME, SERVER_RUNNER_JAR[0], force);
        AssetsUtils.copyFile(context, Constants.JAR_NAME, SERVER_RUNNER_JAR[1], force);
        // Write script
        AssetsUtils.writeServerExecScript(context, SERVER_RUNNER_EXEC[0], SERVER_RUNNER_JAR[0].getAbsolutePath());
        AssetsUtils.writeServerExecScript(context, SERVER_RUNNER_EXEC[1], SERVER_RUNNER_JAR[1].getAbsolutePath());
        // Update permission
        FileUtils.chmod711(deStorage);
        FileUtils.chmod644(SERVER_RUNNER_JAR[1]);
        FileUtils.chmod644(SERVER_RUNNER_EXEC[1]);
        if (userHandle != 0) {
            sServerPort += userHandle;
        }

        sInitialised = true;
    }

    @AnyThread
    @NonNull
    public static File getDestJarFile() {
        // For compatibility only
        return SERVER_RUNNER_JAR[0];
    }

    @AnyThread
    @NonNull
    public static String getServerRunnerCommand(int index) throws IndexOutOfBoundsException {
        Log.e(TAG, "Classpath: %s", SERVER_RUNNER_JAR[index]);
        Log.e(TAG, "Exec path: %s", SERVER_RUNNER_EXEC[index]);
        return "sh " + SERVER_RUNNER_EXEC[index] + " " + getLocalServerPort() + " " + getLocalToken();
    }

    @AnyThread
    @NonNull
    public static String getServerRunnerAdbCommand() throws IndexOutOfBoundsException {
        return getServerRunnerCommand(0) + " || " + getServerRunnerCommand(1);
    }

    /**
     * Get existing or generate new 16-digit token for client session
     *
     * @return Existing or new token
     */
    @AnyThread
    @NonNull
    public static String getLocalToken() {
        String token = sPreferences.getString(LOCAL_TOKEN, null);
        if (TextUtils.isEmpty(token)) {
            token = generateToken();
            sPreferences.edit().putString(LOCAL_TOKEN, token).apply();
        }
        return token;
    }

    @AnyThread
    public static boolean getAllowBgRunning() {
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
    public static int getLocalServerPort() {
        return sServerPort;
    }

    @WorkerThread
    @NonNull
    public static String getAdbHost(Context context) {
        return getHostIpAddress(context);
    }

    @WorkerThread
    @NonNull
    public static String getLocalServerHost(Context context) {
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


    @AnyThread
    @NonNull
    private static String generateToken() {
        Context context = ContextUtils.getContext();
        String[] wordList = context.getResources().getStringArray(R.array.word_list);
        SecureRandom secureRandom = new SecureRandom();
        String[] tokenItems = new String[3 + secureRandom.nextInt(3)];
        for (int i = 0; i < tokenItems.length; ++i) {
            tokenItems[i] = wordList[secureRandom.nextInt(wordList.length)];
        }
        return TextUtils.join("-", tokenItems);
    }
}
