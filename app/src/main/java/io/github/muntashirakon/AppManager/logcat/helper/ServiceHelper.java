// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.util.List;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.logcat.CrazyLoggerService;
import io.github.muntashirakon.AppManager.logcat.LogcatRecordingService;
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReaderLoader;
import io.github.muntashirakon.AppManager.logs.Log;

// Copyright 2012 Nolan Lawson
public class ServiceHelper {
    public static final String TAG = ServiceHelper.class.getSimpleName();

    public static void startOrStopCrazyLogger(Context context) {
        if (BuildConfig.DEBUG) {
            Intent intent = new Intent(context, CrazyLoggerService.class);
            if (!context.stopService(intent)) {
                // Service wasn't running
                context.startService(intent);
            }
        }
    }

    public static synchronized void stopBackgroundServiceIfRunning(Context context) {
        boolean alreadyRunning = ServiceHelper.checkIfServiceIsRunning(context, LogcatRecordingService.class);
        Log.d(TAG, "Is LogcatRecordingService running: " + alreadyRunning);
        if (alreadyRunning) {
            Intent intent = new Intent(context, LogcatRecordingService.class);
            context.stopService(intent);
        }
    }

    public static synchronized void startBackgroundServiceIfNotAlreadyRunning(Context context, String filename,
                                                                              String queryFilter, int logLevel) {
        boolean alreadyRunning = ServiceHelper.checkIfServiceIsRunning(context, LogcatRecordingService.class);
        Log.d(TAG, "Is LogcatRecordingService running: " + alreadyRunning);
        if (!alreadyRunning) {
            Intent intent = new Intent(context, LogcatRecordingService.class);
            intent.putExtra(LogcatRecordingService.EXTRA_FILENAME, filename);
            // Load "lastLine" in the background
            LogcatReaderLoader loader = LogcatReaderLoader.create(true);
            intent.putExtra(LogcatRecordingService.EXTRA_LOADER, loader);
            // Add query text and log level
            intent.putExtra(LogcatRecordingService.EXTRA_QUERY_FILTER, queryFilter);
            intent.putExtra(LogcatRecordingService.EXTRA_LEVEL, logLevel);
            context.startService(intent);
        }
    }

    public static boolean checkIfServiceIsRunning(Context context, Class<?> service) {
        String serviceName = service.getName();
        ComponentName componentName = new ComponentName(context.getPackageName(), serviceName);
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> procList = activityManager.getRunningServices(Integer.MAX_VALUE);
        if (procList != null) {
            for (ActivityManager.RunningServiceInfo appProcInfo : procList) {
                if (appProcInfo != null && componentName.equals(appProcInfo.service)) {
                    Log.d(TAG, serviceName + " is already running.");
                    return true;
                }
            }
        }
        Log.d(TAG, serviceName + " is not running.");
        return false;
    }
}
