// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logs;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        File cacheDir = AppManager.getContext().getExternalCacheDir();
        if (cacheDir != null && cacheDir.canWrite()) {
            LOG_FILE = new File(cacheDir, "am.log");
        } else LOG_FILE = new File(AppManager.getContext().getCacheDir(), "am.log");
        INSTANCE = new Log();
    }

    @Nullable
    private PrintWriter writer;
    private final ExecutorService executor = Executors.newFixedThreadPool(1);

    private Log() {
        try {
            writer = new PrintWriter(new BufferedWriter(new FileWriter(LOG_FILE)));
        } catch (IOException ignore) {
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

    public static void e(@Nullable String tag, @NonNull Throwable e) {
        println(ERROR, tag, null, e);
        if (BuildConfig.DEBUG) android.util.Log.e(tag, null, e);
    }

    public static void e(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
        println(ERROR, tag, msg, tr);
        if (BuildConfig.DEBUG) android.util.Log.e(tag, msg, tr);
    }

    private static void println(@Level int level, @Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
        if (INSTANCE.writer == null) return;

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

        INSTANCE.executor.submit(() -> {
            synchronized (INSTANCE) {
                INSTANCE.writer.println(sb);
                if (tr != null) {
                    tr.printStackTrace(INSTANCE.writer);
                }
                INSTANCE.writer.flush();
            }
        });
    }
}
