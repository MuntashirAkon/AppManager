/*
 * Copyright (c) 2021 Muntashir Al-Islam
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
