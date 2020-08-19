package io.github.muntashirakon.AppManager.servermanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.io.File;

import io.github.muntashirakon.AppManager.BuildConfig;

class ServerConfig {
    static String SOCKET_PATH = "am_socket";
    private static String DEFAULT_ADB_HOST = "127.0.0.1";
    private static int DEFAULT_ADB_PORT = 52053;
    private static final String LOCAL_TOKEN = "l_token";

    static final String JAR_NAME = "am.jar";
    static final String EXECUTABLE_FILE_NAME = "run_server.sh";

    private static File destJarFile;
    private static File destExecutableFile;
    private static String sClassPath = null;
    private static SharedPreferences sPreferences;
    private static volatile boolean sInited = false;


    static void init(Context context, int userHandleId) {
        if (sInited) {
            return;
        }
        destJarFile = new File(context.getExternalFilesDir(null), JAR_NAME);
        destExecutableFile = new File(context.getExternalFilesDir(null), EXECUTABLE_FILE_NAME);
        //sClassPath = destJarFile.getAbsolutePath();
        sClassPath = "/sdcard/Android/data/" + BuildConfig.APPLICATION_ID + "/files/" + JAR_NAME;
        sPreferences = context.getSharedPreferences("server_config", Context.MODE_PRIVATE);
        if (userHandleId != 0) {
            SOCKET_PATH += userHandleId;
            DEFAULT_ADB_PORT += userHandleId;
        }
        sInited = true;
    }


    static File getDestJarFile() {
        return destJarFile;
    }

    static File getDestExecutableFile() {
        return destExecutableFile;
    }

    static String getClassPath() {
        return sClassPath;
    }

    /**
     * Get existing or generate new 16-digit token for client session
     * @return Existing or new token
     */
    static String getLocalToken() {
        String token = sPreferences.getString(LOCAL_TOKEN, null);
        if (TextUtils.isEmpty(token)) {
            token = AssetsUtils.generateToken(16);
            sPreferences.edit().putString(LOCAL_TOKEN, token).apply();
        }
        return token;
    }

    /**
     * Get ADB port for client session
     * @return ADB port
     */
    static int getPort() {
        return DEFAULT_ADB_PORT;
    }

    /**
     * Get ADB host for client session
     * @return ADB host (localhost)
     */
    static String getHost() {
        return DEFAULT_ADB_HOST;
    }
}
