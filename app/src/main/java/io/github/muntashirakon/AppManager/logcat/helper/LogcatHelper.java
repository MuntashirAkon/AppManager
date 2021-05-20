// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.servermanager.ProcessCompat;

// Copyright 2012 Nolan Lawson
public class LogcatHelper {
    public static final String TAG = LogcatHelper.class.getSimpleName();

    @IntDef(value = {LOG_ID_MAIN, LOG_ID_RADIO, LOG_ID_EVENTS, LOG_ID_SYSTEM, LOG_ID_CRASH}, flag = true)
    public @interface LogBufferId {
    }

    public static final int LOG_ID_MAIN = 1;
    public static final int LOG_ID_RADIO = 1 << 1;
    public static final int LOG_ID_EVENTS = 1 << 2;
    public static final int LOG_ID_SYSTEM = 1 << 3;
    public static final int LOG_ID_CRASH = 1 << 4;

    public static final String BUFFER_MAIN = "main";
    public static final String BUFFER_RADIO = "radio";
    public static final String BUFFER_EVENTS = "events";
    public static final String BUFFER_SYSTEM = "system";
    public static final String BUFFER_CRASH = "crash";

    public static Process getLogcatProcess(@LogBufferId int buffers) throws IOException {
        return ProcessCompat.exec(getLogcatArgs(buffers, false));
    }

    @Nullable
    public static String getLastLogLine(@LogBufferId int buffers) {
        Process dumpLogcatProcess = null;
        BufferedReader reader;
        String result = null;
        try {
            dumpLogcatProcess = ProcessCompat.exec(getLogcatArgs(buffers, true));
            reader = new BufferedReader(new InputStreamReader(dumpLogcatProcess
                    .getInputStream()), 8192);

            String line;
            while ((line = reader.readLine()) != null) {
                result = line;
            }
        } catch (IOException e) {
            Log.e(TAG, e);
        } finally {
            if (dumpLogcatProcess != null) {
                dumpLogcatProcess.destroy();
                Log.d(TAG, "destroyed 1 dump logcat process");
            }
        }
        return result;
    }

    @NonNull
    private static String[] getLogcatArgs(@LogBufferId int buffers, boolean dumpAndExit) {
        List<String> args = new ArrayList<>(Arrays.asList("logcat", "-v", "time"));
        // For some reason, adding -b main excludes log output from AndroidRuntime runtime exceptions,
        // whereas just leaving it blank keeps them in.  So do not specify the buffer if it is "main"
        if ((buffers & LOG_ID_MAIN) != 0) {
            args.add("-b");
            args.add(BUFFER_MAIN);
        }
        if ((buffers & LOG_ID_RADIO) != 0) {
            args.add("-b");
            args.add(BUFFER_RADIO);
        }
        if ((buffers & LOG_ID_EVENTS) != 0) {
            args.add("-b");
            args.add(BUFFER_EVENTS);
        }
        if ((buffers & LOG_ID_SYSTEM) != 0) {
            args.add("-b");
            args.add(BUFFER_SYSTEM);
        }
        if ((buffers & LOG_ID_CRASH) != 0) {
            args.add("-b");
            args.add(BUFFER_CRASH);
        }
        if (dumpAndExit) args.add("-d");
        return args.toArray(new String[0]);
    }
}
