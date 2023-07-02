// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.app.ServiceCompat;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsResultsActivity;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler.NotificationManagerInfo;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
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
    private String mProfileName;
    private QueuedProgressHandler mProgressHandler;
    private NotificationProgressHandler.NotificationInfo mNotificationInfo;

    public ProfileApplierService() {
        super("ProfileApplierService");
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (isWorking()) return super.onStartCommand(intent, flags, startId);
        if (intent != null) {
            mProfileName = intent.getStringExtra(EXTRA_PROFILE_NAME);
        }
        NotificationManagerInfo notificationManagerInfo = new NotificationManagerInfo(CHANNEL_ID,
                "Profile Applier", NotificationManagerCompat.IMPORTANCE_LOW);
        mProgressHandler = new NotificationProgressHandler(this,
                notificationManagerInfo,
                NotificationUtils.HIGH_PRIORITY_NOTIFICATION_INFO,
                NotificationUtils.HIGH_PRIORITY_NOTIFICATION_INFO);
        mProgressHandler.setProgressTextInterface(ProgressHandler.PROGRESS_REGULAR);
        mNotificationInfo = new NotificationProgressHandler.NotificationInfo()
                .setBody(getString(R.string.operation_running))
                .setOperationName(getText(R.string.profiles));
        mProgressHandler.onAttach(this, mNotificationInfo);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;
        mProfileName = intent.getStringExtra(EXTRA_PROFILE_NAME);
        if (mProfileName == null) return;
        String state = intent.getStringExtra(EXTRA_PROFILE_STATE);
        ProfileManager profileManager = new ProfileManager(new ProfileMetaManager(mProfileName));
        profileManager.applyProfile(state, mProgressHandler);
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
        mProgressHandler.onQueue(notificationInfo);
    }

    @Override
    protected void onStartIntent(@Nullable Intent intent) {
        if (intent == null) return;
        mProfileName = intent.getStringExtra(EXTRA_PROFILE_NAME);
        Intent notificationIntent = new Intent(this, AppsProfileActivity.class);
        notificationIntent.putExtra(AppsProfileActivity.EXTRA_PROFILE_NAME, mProfileName);
        PendingIntent pendingIntent = PendingIntentCompat.getActivity(this, 0, notificationIntent,
                0, false);
        // Set app name in the ongoing notification
        mNotificationInfo.setTitle(mProfileName).setDefaultAction(pendingIntent);
        mProgressHandler.onProgressStart(-1, 0, mNotificationInfo);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (mProgressHandler != null) {
            mProgressHandler.onDetach(this);
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
                .setTitle(mProfileName);
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
            PendingIntent pendingIntent = PendingIntentCompat.getActivity(this, 0, intent,
                    PendingIntent.FLAG_ONE_SHOT, false);
            notificationInfo.addAction(0, getString(R.string.restart_device), pendingIntent);
        }
        mProgressHandler.onResult(notificationInfo);
    }
}
