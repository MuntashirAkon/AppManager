// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.server.common.ConfigParams;
import io.github.muntashirakon.AppManager.server.common.Constants;
import io.github.muntashirakon.io.IoUtils;

// Copyright 2016 Zheng Li
@SuppressWarnings("ResultOfMethodCallIgnored")
class AssetsUtils {
    @WorkerThread
    public static void copyFile(@NonNull Context context, String fileName, File destFile, boolean force)
            throws IOException {
        try (AssetFileDescriptor openFd = context.getAssets().openFd(fileName)) {
            if (force) {
                destFile.delete();
            } else {
                if (destFile.exists()) {
                    if (destFile.length() != openFd.getLength()) {
                        destFile.delete();
                    } else {
                        return;
                    }
                }
            }

            try (FileInputStream open = openFd.createInputStream();
                 FileOutputStream fos = new FileOutputStream(destFile)) {
                byte[] buff = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
                int len;
                while ((len = open.read(buff)) != -1) {
                    fos.write(buff, 0, len);
                }
                fos.flush();
                fos.getFD().sync();
            }
        }
    }

    @WorkerThread
    static void writeServerExecScript(@NonNull Context context, @NonNull File destFile, @NonNull String classPath) throws IOException {
        try (AssetFileDescriptor openFd = context.getAssets().openFd(ServerConfig.SERVER_RUNNER_EXEC_NAME);
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(openFd.createInputStream()))) {
            if (destFile.exists()) {
                destFile.delete();
            }
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(destFile, false))) {
                // Set variables
                StringBuilder script = new StringBuilder();
                script.append("SERVER_NAME=").append(Constants.SERVER_NAME).append("\n")
                        .append("JAR_NAME=").append(Constants.JAR_NAME).append("\n")
                        .append("JAR_PATH=").append(classPath).append("\n")
                        .append("ARGS=").append(getServerArgs()).append("\n");
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    String wl;
                    if ("%ENV_VARS%".equals(line.trim())) {
                        wl = script.toString();
                    } else wl = line;
                    bw.write(wl);
                    bw.newLine();
                }
                bw.flush();
            }
        }
    }

    @NotNull
    private static String getServerArgs() {
        StringBuilder argsBuilder = new StringBuilder();
        argsBuilder.append(',').append(ConfigParams.PARAM_APP).append(':').append(BuildConfig.APPLICATION_ID);
        if (ServerConfig.getAllowBgRunning()) {
            argsBuilder.append(',').append(ConfigParams.PARAM_RUN_IN_BACKGROUND).append(':').append(1);
        }
        if (BuildConfig.DEBUG) {
            argsBuilder.append(',').append(ConfigParams.PARAM_DEBUG).append(':').append(1);
        }
        return argsBuilder.toString();
    }
}
