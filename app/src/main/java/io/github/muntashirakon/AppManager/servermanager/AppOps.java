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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AppOps {

    private static final String LOG_FILE = "am.log";

    private static AppOpsManager sManager;

    public static AppOpsManager getInstance(Context context) {
        if (sManager == null) {
            synchronized (AppOps.class) {
                if (sManager == null) {
                    AppOpsManager.Config config = new AppOpsManager.Config();
                    updateConfig(context, config);
                    sManager = new AppOpsManager(context.getApplicationContext(), config);
                }
            }
        }
        return sManager;
    }

    public static void updateConfig(Context context) {
        if (sManager != null) {
            AppOpsManager.Config config = sManager.getConfig();
            if (config != null) {
                updateConfig(context, config);
            }
        }
    }

    private static void updateConfig(Context context, @NonNull AppOpsManager.Config config) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        config.allowBgRunning = sp.getBoolean("allow_bg_remote", true);
        config.logFile = context.getFileStreamPath(LOG_FILE).getAbsolutePath();
        config.useAdb = sp.getBoolean("use_adb", false);
        config.adbPort = sp.getInt("use_adb_port", 5555);
        config.rootOverAdb = sp.getBoolean("allow_root_over_adb", false);
        Log.e("test", "buildConfig --> " + context.getFileStreamPath(LOG_FILE).getAbsolutePath());
    }

    @NonNull
    public static String readLogs(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELinux:");
        if (AppOpsManager.isEnableSELinux()) {
            sb.append("Enforcing");
        }
        sb.append("\n\n");

        File file = context.getFileStreamPath(LOG_FILE);
        if (file.exists()) {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader(file));
                String line = br.readLine();

                while (line != null) {
                    sb.append(line);
                    sb.append("\n");
                    line = br.readLine();
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (br != null) {
                        br.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        } else {

            sb.append("No logs");
        }

        return sb.toString();
    }

    @Nullable
    private static String readProcess() {
        Process exec = null;
        BufferedReader br = null;
        try {
            exec = Runtime.getRuntime().exec("su -C 'ps'");
            br = new BufferedReader(new InputStreamReader(exec.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                Log.e("test", "readProcess --> " + line);
                //sb.append(line);
                //sb.append("\n");
                line = br.readLine();
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (exec != null) {
                exec.destroy();
            }
        }
        return null;
    }
}
