// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsResultsActivity;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.compat.PendingIntentCompat;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

public class ProfileApplierService extends ForegroundService {
    public static final String EXTRA_PROFILE_NAME = "prof";
    public static final String EXTRA_PROFILE_STATE = "state";
    /**
     * Notification channel ID
     */
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.PROFILE_APPLIER";
    public static final int NOTIFICATION_ID = 1;

    @Nullable
    private String profileName;
    private NotificationCompat.Builder builder;
    private NotificationManagerCompat notificationManager;

    public ProfileApplierService() {
        super("ProfileApplierService");
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (isWorking()) return super.onStartCommand(intent, flags, startId);
        if (intent != null) {
            profileName = intent.getStringExtra(EXTRA_PROFILE_NAME);
        }
        notificationManager = NotificationUtils.getNewNotificationManager(this, CHANNEL_ID,
                "Profile Applier", NotificationManagerCompat.IMPORTANCE_LOW);
        builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentText(getString(R.string.operation_running))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setSubText(getText(R.string.profiles))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(0, 0, true);
        startForeground(NOTIFICATION_ID, builder.build());
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;
        profileName = intent.getStringExtra(EXTRA_PROFILE_NAME);
        if (profileName == null) return;
        String state = intent.getStringExtra(EXTRA_PROFILE_STATE);
        ProfileManager profileManager = new ProfileManager(new ProfileMetaManager(profileName));
        profileManager.applyProfile(state);
        profileManager.conclude();
        sendNotification(Activity.RESULT_OK, profileManager.requiresRestart());
    }

    @Override
    protected void onQueued(@Nullable Intent intent) {
        if (intent == null) return;
        String profileName = intent.getStringExtra(EXTRA_PROFILE_NAME);
        NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(this)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setTicker(profileName)
                .setContentTitle(profileName)
                .setSubText(getText(R.string.profiles))
                .setContentText(getString(R.string.added_to_queue));
        NotificationUtils.displayHighPriorityNotification(this, builder.build());
    }

    @Override
    protected void onStartIntent(@Nullable Intent intent) {
        if (intent == null) return;
        profileName = intent.getStringExtra(EXTRA_PROFILE_NAME);
        Intent notificationIntent = new Intent(this, AppsProfileActivity.class);
        notificationIntent.putExtra(AppsProfileActivity.EXTRA_PROFILE_NAME, profileName);
        @SuppressLint("WrongConstant")
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntentCompat.FLAG_IMMUTABLE);
        // Set app name in the ongoing notification
        builder.setContentTitle(profileName)
                .setContentIntent(pendingIntent);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        // Hack to remove ongoing notification
        notificationManager.deleteNotificationChannel(CHANNEL_ID);
        super.onDestroy();
    }

    private void sendNotification(int result, boolean requiresRestart) {
        NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(this);
        builder.setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setTicker(profileName)
                .setContentTitle(profileName)
                .setSubText(getText(R.string.profiles));
        switch (result) {
            case Activity.RESULT_CANCELED:  // Failure
                builder.setContentText(getString(R.string.error));
                break;
            case Activity.RESULT_OK:  // Successful
                builder.setContentText(getString(R.string.the_operation_was_successful));
        }
        if (requiresRestart) {
            Intent intent = new Intent(this, BatchOpsResultsActivity.class);
            intent.putExtra(BatchOpsService.EXTRA_REQUIRES_RESTART, true);
            @SuppressLint("WrongConstant")
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntentCompat.FLAG_IMMUTABLE);
            builder.addAction(0, getString(R.string.restart_device), pendingIntent);
        }
        NotificationUtils.displayHighPriorityNotification(this, builder.build());
    }
}
