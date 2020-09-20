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

package io.github.muntashirakon.AppManager.logs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;

import static android.util.Log.ASSERT;
import static android.util.Log.DEBUG;
import static android.util.Log.ERROR;
import static android.util.Log.INFO;
import static android.util.Log.VERBOSE;
import static android.util.Log.WARN;

public class Log {
    @IntDef(value = {VERBOSE, DEBUG, INFO, WARN, ERROR, ASSERT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Level {
    }

    private static final Log INSTANCE;
    private static final File LOG_FILE;
    private static final DateFormat DATE_FORMAT;

    static {
        DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
        LOG_FILE = new File(AppManager.getContext().getExternalFilesDir("cache"), "am.log");
        INSTANCE = new Log();
    }

    @NonNull
    private PrintWriter writer;

    private Log() {
        try {
            writer = new PrintWriter(new BufferedWriter(new FileWriter(LOG_FILE)));
        } catch (IOException e) {
            throw new RuntimeException("Could not write to log file.");
        }
    }

    public static void v(@Nullable String tag, @NonNull String msg) {
        if (BuildConfig.DEBUG) {
            println(VERBOSE, tag, msg, null);
            android.util.Log.v(tag, msg);
        }
    }

    public static void v(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
        if (BuildConfig.DEBUG) {
            println(VERBOSE, tag, msg, tr);
            android.util.Log.v(tag, msg, tr);
        }
    }

    public static void d(@Nullable String tag, @NonNull String msg) {
        if (BuildConfig.DEBUG) {
            println(DEBUG, tag, msg, null);
            android.util.Log.d(tag, msg);
        }
    }

    public static void d(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
        if (BuildConfig.DEBUG) {
            println(DEBUG, tag, msg, tr);
            android.util.Log.d(tag, msg, tr);
        }
    }

    public static void i(@Nullable String tag, @NonNull String msg) {
        if (BuildConfig.DEBUG) {
            println(INFO, tag, msg, null);
            android.util.Log.i(tag, msg);
        }
    }

    public static void i(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
        if (BuildConfig.DEBUG) {
            println(INFO, tag, msg, tr);
            android.util.Log.i(tag, msg, tr);
        }
    }

    public static void w(@Nullable String tag, @NonNull String msg) {
        println(WARN, tag, msg, null);
        if (BuildConfig.DEBUG) android.util.Log.w(tag, msg);
    }

    public static void w(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
        println(WARN, tag, msg, tr);
        if (BuildConfig.DEBUG) android.util.Log.w(tag, msg, tr);
    }

    public static void w(@Nullable String tag, @Nullable Throwable tr) {
        println(WARN, tag, null, tr);
        if (BuildConfig.DEBUG) android.util.Log.w(tag, tr);
    }

    public static void e(@Nullable String tag, @NonNull String msg) {
        println(ERROR, tag, msg, null);
        if (BuildConfig.DEBUG) android.util.Log.e(tag, msg);
    }

    public static void e(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
        println(ERROR, tag, msg, tr);
        if (BuildConfig.DEBUG) android.util.Log.e(tag, msg, tr);
    }

    private static void println(@Level int level, @Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
        StringBuilder sb = new StringBuilder();
        sb.append(DATE_FORMAT.format(new Date(System.currentTimeMillis()))).append(" ");
        switch (level) {
            case ASSERT:
                sb.append("A/");
                break;
            case DEBUG:
                sb.append("D/");
                break;
            case ERROR:
                sb.append("E/");
                break;
            case INFO:
                sb.append("I/");
                break;
            case VERBOSE:
                sb.append("V/");
                break;
            case WARN:
                sb.append("W/");
                break;
        }
        sb.append(tag == null ? "App Manager" : tag);
        if (msg != null) sb.append(": ").append(msg);
        new Thread(() -> {
            synchronized (INSTANCE) {
                INSTANCE.writer.println(sb.toString());
                if (tr != null) {
                    tr.printStackTrace(INSTANCE.writer);
                }
                INSTANCE.writer.flush();
            }
        }).start();
    }
}
