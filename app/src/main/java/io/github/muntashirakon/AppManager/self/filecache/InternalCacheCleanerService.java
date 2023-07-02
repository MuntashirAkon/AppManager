// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.filecache;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.PendingIntentCompat;

import java.util.Calendar;

import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.FileSystemManager;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

// IMPORTANT: This service must be run without authentication.
public class InternalCacheCleanerService extends ForegroundService {
    public static final String TAG = InternalCacheCleanerService.class.getSimpleName();

    public static void scheduleAlarm(@NonNull Context context) {
        Intent intent = new Intent(context, InternalCacheCleanerService.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pastAlarmIntent = PendingIntentCompat.getService(context, 0, intent, flags | PendingIntent.FLAG_NO_CREATE, false);
        if (pastAlarmIntent != null) {
            // Already exists
            return;
        }
        PendingIntent alarmIntent = PendingIntentCompat.getService(context, 0, intent, flags, false);

        // Set the alarm to start at 5:00 AM
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, 5);

        // Run everyday
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, alarmIntent);
    }

    public InternalCacheCleanerService() {
        super(TAG);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        clearOldFiles();
        clearOldImages();
    }

    private void clearOldFiles() {
        // Delete any files not accessed the last 3 days
        long lastAccessDate = System.currentTimeMillis() - 259_200_000;
        Path fileCache = Paths.getUnprivileged(FileSystemManager.getLocal().getFile(FileUtils.getCachePath(), "files"));
        int deleteCount = deleteFilesWithAccessDate(fileCache, lastAccessDate);
        Log.i(TAG, "Deleted " + deleteCount + " files and directories.");
    }

    private void clearOldImages() {
        // Delete any files not accessed the last 7 days
        long lastAccessDate = System.currentTimeMillis() - 604_800_000;
        Path fileCache = Paths.getUnprivileged(FileSystemManager.getLocal().getFile(FileUtils.getCachePath(), "images"));
        int deleteCount = deleteFilesWithAccessDate(fileCache, lastAccessDate);
        Log.i(TAG, "Deleted " + deleteCount + " images.");
    }

    private static int deleteFilesWithAccessDate(@NonNull Path basePath, long accessDate) {
        int deleteCount = 0;
        Path[] files = basePath.listFiles();
        for (Path file : files) {
            long lastAccess = file.lastAccess();
            if (lastAccess <= 0) {
                lastAccess = file.lastModified();
            }
            if (lastAccess <= accessDate && file.delete()) {
                ++deleteCount;
            }
        }
        return deleteCount;
    }
}
