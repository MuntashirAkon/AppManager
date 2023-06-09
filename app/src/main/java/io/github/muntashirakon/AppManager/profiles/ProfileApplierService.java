// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsResultsActivity;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.compat.PendingIntentCompat;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler;
import io.github.muntashirakon.AppManager.progress.QueuedProgressHandler;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

public class ProfileApplierService extends ForegroundService {
    public static final String EXTRA_PROFILE_NAME = "prof";
    public static final String EXTRA_PROFILE_STATE = "state";
    /**
     * Notification channel ID
     */
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.PROFILE_APPLIER";

    @Nullable
    private String profileName;
    private QueuedProgressHandler progressHandler;
    private NotificationProgressHandler.NotificationInfo notificationInfo;

    public ProfileApplierService() {
        super("ProfileApplierService");
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (isWorking()) return super.onStartCommand(intent, flags, startId);
        if (intent != null) {
            profileName = intent.getStringExtra(EXTRA_PROFILE_NAME);
        }
        progressHandler = new NotificationProgressHandler(this,
                new NotificationProgressHandler.NotificationManagerInfo(CHANNEL_ID, "Profile Applier", NotificationManagerCompat.IMPORTANCE_LOW),
                NotificationUtils.HIGH_PRIORITY_NOTIFICATION_INFO,
                NotificationUtils.HIGH_PRIORITY_NOTIFICATION_INFO);
        notificationInfo = new NotificationProgressHandler.NotificationInfo()
                .setBody(getString(R.string.operation_running))
                .setOperationName(getText(R.string.profiles));
        progressHandler.onAttach(this, notificationInfo);
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
        Object notificationInfo = new NotificationProgressHandler.NotificationInfo()
                .setAutoCancel(true)
                .setTime(System.currentTimeMillis())
                .setOperationName(getText(R.string.profiles))
                .setTitle(profileName)
                .setBody(getString(R.string.added_to_queue));
        progressHandler.onQueue(notificationInfo);
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
        notificationInfo.setTitle(profileName).setDefaultAction(pendingIntent);
        progressHandler.onProgressStart(-1, 0, notificationInfo);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (progressHandler != null) {
            progressHandler.onDetach(this);
        }
    }

    @Override
    public void onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    private void sendNotification(int result, boolean requiresRestart) {
        NotificationProgressHandler.NotificationInfo notificationInfo = new NotificationProgressHandler
                .NotificationInfo()
                .setAutoCancel(true)
                .setTime(System.currentTimeMillis())
                .setOperationName(getText(R.string.profiles))
                .setTitle(profileName);
        switch (result) {
            case Activity.RESULT_CANCELED:  // Failure
                notificationInfo.setBody(getString(R.string.error));
                break;
            case Activity.RESULT_OK:  // Successful
                notificationInfo.setBody(getString(R.string.the_operation_was_successful));
        }
        if (requiresRestart) {
            Intent intent = new Intent(this, BatchOpsResultsActivity.class);
            intent.putExtra(BatchOpsService.EXTRA_REQUIRES_RESTART, true);
            @SuppressLint("WrongConstant")
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntentCompat.FLAG_IMMUTABLE);
            notificationInfo.addAction(0, getString(R.string.restart_device), pendingIntent);
        }
        progressHandler.onResult(notificationInfo);
    }
}
