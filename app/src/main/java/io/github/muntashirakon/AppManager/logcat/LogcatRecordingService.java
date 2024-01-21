// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.Random;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper;
import io.github.muntashirakon.AppManager.logcat.helper.WidgetHelper;
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReader;
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReaderLoader;
import io.github.muntashirakon.AppManager.logcat.struct.LogLine;
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler;
import io.github.muntashirakon.AppManager.progress.QueuedProgressHandler;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * Reads logs.
 */
// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
public class LogcatRecordingService extends ForegroundService {
    public static final String TAG = LogcatRecordingService.class.getSimpleName();

    public static final String URI_SCHEME = "am_logcat_recording_service";
    public static final String EXTRA_FILENAME = "filename";
    public static final String EXTRA_LOADER = "loader";
    public static final String EXTRA_QUERY_FILTER = "filter";
    public static final String EXTRA_LEVEL = "level";

    private static final String ACTION_STOP_RECORDING = BuildConfig.APPLICATION_ID + ".action.STOP_RECORDING";
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.LOGCAT_RECORDER";

    private final Object mLock = new Object();
    private LogcatReader mReader;
    private boolean mKilled;
    private Handler mHandler;
    private QueuedProgressHandler mProgressHandler;
    private PowerManager.WakeLock mWakeLock;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Received broadcast to kill service
            killProcess();
            ServiceHelper.stopBackgroundServiceIfRunning(context);
        }
    };

    public LogcatRecordingService() {
        super("AppTrackerService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter intentFilter = new IntentFilter(ACTION_STOP_RECORDING);
        intentFilter.addDataScheme(URI_SCHEME);
        ContextCompat.registerReceiver(this, mReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        mHandler = new Handler(Looper.getMainLooper());
        mWakeLock = CpuUtils.getPartialWakeLock("logcat_recorder");
        mWakeLock.acquire();
    }


    private void initializeReader(@NonNull LogcatReaderLoader loader) {
        try {
            mReader = loader.loadReader();
            while (mReader != null && !mReader.readyToRecord() && !mKilled) {
                mReader.readLine();
                // Keep skipping lines until we find one that is past the last log line, i.e.
                // it's ready to record
            }
            if (!mKilled) {
                makeToast(R.string.log_recording_started, Toast.LENGTH_SHORT);
            }
        } catch (IOException e) {
            Log.e(TAG, e);
        }
    }

    @Override
    public void onDestroy() {
        CpuUtils.releaseWakeLock(mWakeLock);
        super.onDestroy();
        killProcess();
        unregisterReceiver(mReceiver);
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        WidgetHelper.updateWidgets(getApplicationContext(), false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Update widget
        WidgetHelper.updateWidgets(getApplicationContext());
        mProgressHandler = new NotificationProgressHandler(this,
                new NotificationProgressHandler.NotificationManagerInfo(CHANNEL_ID, "Logcat Recorder", NotificationManagerCompat.IMPORTANCE_DEFAULT),
                NotificationUtils.HIGH_PRIORITY_NOTIFICATION_INFO,
                null);
        Intent stopRecordingIntent = new Intent();
        stopRecordingIntent.setAction(ACTION_STOP_RECORDING);
        // Have to make this unique for God knows what reason
        stopRecordingIntent.setData(Uri.withAppendedPath(Uri.parse(URI_SCHEME + "://stop/"),
                Long.toHexString(new Random().nextLong())));
        PendingIntent pendingIntent = PendingIntentCompat.getBroadcast(this, 0 /* no requestCode */,
                stopRecordingIntent, PendingIntent.FLAG_ONE_SHOT, false);

        Object notificationInfo = new NotificationProgressHandler.NotificationInfo()
                .setTitle(getString(R.string.notification_title))
                .setBody(getString(R.string.notification_subtext))
                .setStatusBarText(getText(R.string.notification_ticker))
                .setDefaultAction(pendingIntent);
        mProgressHandler.onAttach(this, notificationInfo);
        return super.onStartCommand(intent, flags, startId);
    }

    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) {
            // Empty calls
            return;
        }
        Log.d(TAG, "Starting with intent: %s", intent);
        String filename = intent.getStringExtra(EXTRA_FILENAME);
        String queryText = intent.getStringExtra(EXTRA_QUERY_FILTER);
        SearchCriteria searchCriteria = new SearchCriteria(queryText);
        int logLevel = intent.getIntExtra(EXTRA_LEVEL, Prefs.LogViewer.getLogLevel());
        boolean searchCriteriaWillAlwaysMatch = searchCriteria.isEmpty();
        boolean logLevelAcceptsEverything = logLevel == android.util.Log.VERBOSE;
        StringBuilder stringBuilder = new StringBuilder();
        LogcatReaderLoader loader = IntentCompat.getParcelableExtra(intent, EXTRA_LOADER, LogcatReaderLoader.class);
        if (loader == null) {
            // No loader found
            return;
        }

        SaveLogHelper.deleteLogIfExists(filename);
        initializeReader(loader);
        try {
            String line;
            int lineCount = 0;
            int logLinePeriod = Prefs.LogViewer.getLogWritingInterval();
            Pattern filterPattern = Pattern.compile(Prefs.LogViewer.getFilterPattern());
            while (mReader != null && (line = mReader.readLine()) != null && !mKilled) {
                // filter
                if (!searchCriteriaWillAlwaysMatch || !logLevelAcceptsEverything) {
                    if (!checkLogLine(line, searchCriteria, logLevel, filterPattern)) {
                        continue;
                    }
                }
                stringBuilder.append(line).append("\n");
                if (++lineCount % logLinePeriod == 0) {
                    // avoid OutOfMemoryErrors; flush now
                    SaveLogHelper.saveLog(stringBuilder, filename);
                    stringBuilder.delete(0, stringBuilder.length()); // clear
                }
            }
        } catch (IOException e) {
            Log.e(TAG, e);
        } finally {
            killProcess();
            Log.d(TAG, "Service ended");
            boolean logSaved = SaveLogHelper.saveLog(stringBuilder, filename);
            NotificationProgressHandler.NotificationInfo notificationInfo =
                    new NotificationProgressHandler.NotificationInfo()
                            .setTitle(getString(R.string.notification_title))
                            .setAutoCancel(true);
            if (logSaved) {
                notificationInfo.setTitle(getString(R.string.log_saved))
                        .setStatusBarText(getString(R.string.log_saved))
                        .setBody(getString(R.string.tap_to_see_details))
                        .setDefaultAction(getLogcatActivityToViewSavedFile(filename));
            } else {
                notificationInfo.setTitle(getString(R.string.unable_to_save_log));
            }
            ThreadUtils.postOnMainThread(() -> mProgressHandler.onResult(notificationInfo));
        }
    }

    private boolean checkLogLine(String line, SearchCriteria searchCriteria, int logLevel, Pattern filterPattern) {
        LogLine logLine = LogLine.newLogLine(line, false, filterPattern);
        return logLine != null && logLine.getLogLevel() >= logLevel && searchCriteria.matches(logLine);
    }


    private PendingIntent getLogcatActivityToViewSavedFile(String filename) {
        // Start up the logcat activity if necessary and show the saved file
        Intent targetIntent = new Intent(getApplicationContext(), LogViewerActivity.class);
        targetIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        targetIntent.setAction(Intent.ACTION_MAIN);
        targetIntent.putExtra(LogViewerActivity.EXTRA_FILENAME, filename);
        return PendingIntentCompat.getActivity(this, 0, targetIntent, PendingIntent.FLAG_ONE_SHOT, false);
    }


    private void makeToast(final int stringResId, final int toastLength) {
        mHandler.post(() -> Toast.makeText(LogcatRecordingService.this, stringResId, toastLength).show());
    }

    private void killProcess() {
        if (!mKilled) {
            synchronized (mLock) {
                if (!mKilled && mReader != null) {
                    // kill the logcat process
                    mReader.killQuietly();
                    mKilled = true;
                }
            }
        }
    }
}
