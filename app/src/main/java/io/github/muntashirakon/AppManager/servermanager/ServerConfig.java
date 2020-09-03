package io.github.muntashirakon.AppManager.servermanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.io.File;

import androidx.annotation.NonNull;

class ServerConfig {
    static String SOCKET_PATH = "am_socket";
    private static final String DEFAULT_ADB_HOST = "127.0.0.1";
    private static int DEFAULT_ADB_PORT = 60001;
    private static final String LOCAL_TOKEN = "l_token";

    static final String JAR_NAME = "am.jar";
//    static final String EXECUTABLE_FILE_NAME = "run_server.sh";

    private static File destJarFile;
    private static SharedPreferences sPreferences;
    private static volatile boolean sInitialised = false;

    static void init(Context context, int userHandleId) {
        if (sInitialised) {
            return;
        }
        destJarFile = new File(context.getExternalFilesDir(null), JAR_NAME);
        sPreferences = context.getSharedPreferences("server_config", Context.MODE_PRIVATE);
        if (userHandleId != 0) {
            SOCKET_PATH += userHandleId;
            DEFAULT_ADB_PORT += userHandleId;
        }
        sInitialised = true;
    }


    static File getDestJarFile() {
        return destJarFile;
    }

    @NonNull
    static String getClassPath() {
        return destJarFile.getAbsolutePath();
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
