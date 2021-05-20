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
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.IOException;
import java.util.Random;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper;
import io.github.muntashirakon.AppManager.logcat.helper.WidgetHelper;
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReader;
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReaderLoader;
import io.github.muntashirakon.AppManager.logcat.struct.LogLine;
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

/**
 * Reads logs.
 */
// Copyright 2012 Nolan Lawson
public class LogcatRecordingService extends ForegroundService {
    public static final String TAG = LogcatRecordingService.class.getSimpleName();

    public static final String URI_SCHEME = "catlog_recording_service";
    public static final String EXTRA_FILENAME = "filename";
    public static final String EXTRA_LOADER = "loader";
    public static final String EXTRA_QUERY_FILTER = "filter";
    public static final String EXTRA_LEVEL = "level";
    private static final String ACTION_STOP_RECORDING = BuildConfig.APPLICATION_ID + ".action.STOP_RECORDING";
    private final Object lock = new Object();
    private LogcatReader mReader;
    private boolean mKilled;
    private Handler handler;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
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
        registerReceiver(receiver, intentFilter);
        handler = new Handler(Looper.getMainLooper());
    }


    private void initializeReader(Intent intent) {
        try {
            // use the "time" log so we can see what time the logs were logged at
            LogcatReaderLoader loader = intent.getParcelableExtra(EXTRA_LOADER);
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
        super.onDestroy();
        killProcess();
        unregisterReceiver(receiver);
        stopForeground(true);
        WidgetHelper.updateWidgets(getApplicationContext(), false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Update widget
        WidgetHelper.updateWidgets(getApplicationContext());
        CharSequence tickerText = getText(R.string.notification_ticker);
        Intent stopRecordingIntent = new Intent();
        stopRecordingIntent.setAction(ACTION_STOP_RECORDING);
        // Have to make this unique for God knows what reason
        stopRecordingIntent.setData(Uri.withAppendedPath(Uri.parse(URI_SCHEME + "://stop/"),
                Long.toHexString(new Random().nextLong())));
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0 /* no requestCode */,
                stopRecordingIntent, PendingIntent.FLAG_ONE_SHOT);

        final String CHANNEL_ID = "matlog_logging_channel";
        // Set the icon, scrolling text and timestamp
        NotificationCompat.Builder notification = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
        notification.setSmallIcon(R.drawable.ic_launcher_foreground);
        notification.setTicker(tickerText);
        notification.setWhen(System.currentTimeMillis());
        notification.setContentTitle(getString(R.string.notification_title));
        notification.setContentText(getString(R.string.notification_subtext));
        notification.setContentIntent(pendingIntent);

        NotificationUtils.getNewNotificationManager(this, CHANNEL_ID, "Logcat Recording Service",
                NotificationManagerCompat.IMPORTANCE_DEFAULT);
        startForeground(R.string.notification_title, notification.build());
        return super.onStartCommand(intent, flags, startId);
    }

    protected void onHandleIntent(Intent intent) {
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        Log.d(TAG, "Starting with intent: " + intent);
        String filename = intent.getStringExtra(EXTRA_FILENAME);
        String queryText = intent.getStringExtra(EXTRA_QUERY_FILTER);
        SearchCriteria searchCriteria = new SearchCriteria(queryText);
        int logLevel = intent.getIntExtra(EXTRA_LEVEL, AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT));
        boolean searchCriteriaWillAlwaysMatch = searchCriteria.isEmpty();
        boolean logLevelAcceptsEverything = logLevel == android.util.Log.VERBOSE;
        StringBuilder stringBuilder = new StringBuilder();

        SaveLogHelper.deleteLogIfExists(filename);
        initializeReader(intent);
        try {
            String line;
            int lineCount = 0;
            int logLinePeriod = AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_WRITE_PERIOD_INT);
            String filterPattern = AppPref.getString(AppPref.PrefKey.PREF_LOG_VIEWER_FILTER_PATTERN_STR);
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
            if (logSaved) {
                makeToast(R.string.log_saved, Toast.LENGTH_SHORT);
                startLogcatActivityToViewSavedFile(filename);
            } else {
                makeToast(R.string.unable_to_save_log, Toast.LENGTH_LONG);
            }
        }
    }

    private boolean checkLogLine(String line, SearchCriteria searchCriteria, int logLevel, String filterPattern) {
        LogLine logLine = LogLine.newLogLine(line, false, filterPattern);
        return logLine.getLogLevel() >= logLevel && searchCriteria.matches(logLine);
    }


    private void startLogcatActivityToViewSavedFile(String filename) {
        // Start up the logcat activity if necessary and show the saved file
        Intent targetIntent = new Intent(getApplicationContext(), LogViewerActivity.class);
        targetIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        targetIntent.setAction(Intent.ACTION_MAIN);
        targetIntent.putExtra("filename", filename);
        startActivity(targetIntent);
    }


    private void makeToast(final int stringResId, final int toastLength) {
        handler.post(() -> Toast.makeText(LogcatRecordingService.this, stringResId, toastLength).show());
    }

    private void killProcess() {
        if (!mKilled) {
            synchronized (lock) {
                if (!mKilled && mReader != null) {
                    // kill the logcat process
                    mReader.killQuietly();
                    mKilled = true;
                }
            }
        }
    }
}
