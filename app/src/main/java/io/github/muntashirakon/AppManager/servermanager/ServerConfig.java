// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.os.UserHandleHidden;
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
import java.util.Locale;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.server.common.Constants;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;

// Copyright 2016 Zheng Li
public final class ServerConfig {
    public static final String TAG = ServerConfig.class.getSimpleName();

    public static final int DEFAULT_ADB_PORT = 5555;
    private static final String LOCAL_TOKEN = "l_token";
    private static final SharedPreferences sPreferences = ContextUtils.getContext()
            .getSharedPreferences("server_config", Context.MODE_PRIVATE);

    @WorkerThread
    @NoOps
    public static void init(@NonNull Context context) throws IOException {
        // Setup paths
        boolean force = BuildConfig.DEBUG;
        try {
            File externalCachePath = FileUtils.getExternalCachePath(context);
            File externalCacheAmJar = new File(externalCachePath, Constants.JAR_NAME);
            File externalCacheMainJar = new File(externalCachePath, Constants.MAIN_JAR_NAME);
            AssetsUtils.copyFile(context, Constants.JAR_NAME, externalCacheAmJar, force);
            AssetsUtils.copyFile(context, Constants.MAIN_JAR_NAME, externalCacheMainJar, force);
            FileUtils.chmod644(externalCacheAmJar);
            FileUtils.chmod644(externalCacheMainJar);
        } catch (Exception e) {
            throw new IllegalStateException("External directory unavailable.", e);
        }

        try {
            File amPath = getAppManagerStoragePath();
            File externalAmJar = new File(amPath, Constants.JAR_NAME);
            File externalMainJar = new File(amPath, Constants.MAIN_JAR_NAME);
            AssetsUtils.copyFile(context, Constants.JAR_NAME, externalAmJar, force);
            AssetsUtils.copyFile(context, Constants.MAIN_JAR_NAME, externalMainJar, force);
            FileUtils.chmod644(externalAmJar);
            FileUtils.chmod644(externalMainJar);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @NonNull
    public static File getAppManagerStoragePath() throws SecurityException {
        File externalStorage = Environment.getExternalStorageDirectory();
        File appManagerDir = new File(externalStorage, "AppManager");
        if (!appManagerDir.exists()) {
            appManagerDir.mkdirs();
        }
        return appManagerDir;
    }

    @AnyThread
    @NonNull
    public static String getServerRunnerCommand() throws IndexOutOfBoundsException {
        // Fetch the extracted directory
        String libDir = ContextUtils.getContext().getApplicationInfo().nativeLibraryDir;
        File executable = new File(libDir, "librun_server.so");

        if (executable.exists()) {
            int port = getLocalServerPort();
            String token = getLocalToken();
            String amJarName = Constants.JAR_NAME;
            String mainJarName = Constants.MAIN_JAR_NAME;
            String appId = BuildConfig.APPLICATION_ID;
            int currentUserId = UserHandleHidden.myUserId();
            int debug = BuildConfig.DEBUG ? 1 : 0;

            return String.format(Locale.ROOT, "%s %d %s %s %s %s %d %d",
                    executable.getAbsolutePath(),
                    port,
                    token,
                    amJarName,
                    mainJarName,
                    appId,
                    currentUserId,
                    debug
            );
        }
        throw new UnsupportedOperationException(executable.getAbsolutePath() + " does not exists!");
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
        return Prefs.Misc.getAdbLocalServerPort();
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
