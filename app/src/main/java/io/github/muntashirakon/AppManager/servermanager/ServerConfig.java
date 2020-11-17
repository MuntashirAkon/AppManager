package io.github.muntashirakon.AppManager.servermanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.io.File;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;

public class ServerConfig {
    static String SOCKET_PATH = "am_socket";
    private static final String DEFAULT_ADB_HOST = "127.0.0.1";
    private static int DEFAULT_ADB_PORT = 60001;
    private static final String LOCAL_TOKEN = "l_token";

    static final String JAR_NAME = "am.jar";
    static final String EXECUTABLE_FILE_NAME = "run_server.sh";

    private static File destJarFile;
    private static File destExecFile;
    private static final SharedPreferences sPreferences = AppManager.getContext()
            .getSharedPreferences("server_config", Context.MODE_PRIVATE);
    private static volatile boolean sInitialised = false;

    static void init(Context context, int userHandleId) {
        if (sInitialised) {
            return;
        }
        destJarFile = new File(context.getExternalFilesDir(null), JAR_NAME);
        destExecFile = new File(context.getExternalFilesDir(null), EXECUTABLE_FILE_NAME);
        if (userHandleId != 0) {
            SOCKET_PATH += userHandleId;
            DEFAULT_ADB_PORT += userHandleId;
        }
        sInitialised = true;
    }

    @NonNull
    public static File getDestJarFile() {
        return destJarFile;
    }

    @NonNull
    static String getClassPath() {
        return destJarFile.getAbsolutePath();
    }

    @NonNull
    public static String getExecPath() {
        return destExecFile.getAbsolutePath();
    }

    /**
     * Get existing or generate new 16-digit token for client session
     *
     * @return Existing or new token
     */
    static String getLocalToken() {
        String token = sPreferences.getString(LOCAL_TOKEN, null);
        if (TextUtils.isEmpty(token)) {
            token = AssetsUtils.generateToken();
            sPreferences.edit().putString(LOCAL_TOKEN, token).apply();
        }
        return token;
    }

    static boolean getAllowBgRunning() {
        return sPreferences.getBoolean("allow_bg_running", true);
    }

    public static int getAdbPort() {
        return sPreferences.getInt("adb_port", 5555);
    }

    /**
     * Get ADB port for client session
     *
     * @return ADB port
     */
    static int getPort() {
        return DEFAULT_ADB_PORT;
    }

    /**
     * Get ADB host for client session
     *
     * @return ADB host (localhost)
     */
    public static String getHost() {
        return DEFAULT_ADB_HOST;
    }
}
