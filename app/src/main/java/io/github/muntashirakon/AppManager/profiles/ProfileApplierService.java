// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static io.github.muntashirakon.AppManager.history.ops.OpHistoryManager.HISTORY_TYPE_PROFILE;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.app.ServiceCompat;

import java.io.IOException;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsResultsActivity;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler.NotificationManagerInfo;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.progress.QueuedProgressHandler;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.io.Path;

public class ProfileApplierService extends ForegroundService {
    public static final String EXTRA_QUEUE_ITEM = "queue_item";
    public static final String EXTRA_NOTIFY = "notify";
    /**
     * Notification channel ID
     */
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.PROFILE_APPLIER";

    private QueuedProgressHandler mProgressHandler;
    private NotificationProgressHandler.NotificationInfo mNotificationInfo;
    private PowerManager.WakeLock mWakeLock;

    public ProfileApplierService() {
        super("ProfileApplierService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mWakeLock = CpuUtils.getPartialWakeLock("profile_applier");
        mWakeLock.acquire();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (isWorking()) return super.onStartCommand(intent, flags, startId);
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
        ProfileQueueItem item = IntentCompat.getParcelableExtra(intent, EXTRA_QUEUE_ITEM, ProfileQueueItem.class);
        if (item == null) {
            return;
        }
        boolean notify = intent.getBooleanExtra(EXTRA_NOTIFY, true);
        Path tempProfilePath = item.getTempProfilePath();
        try {
            ProfileManager profileManager = new ProfileManager(item.getProfileId(), tempProfilePath);
            profileManager.applyProfile(item.getState(), mProgressHandler);
            profileManager.conclude();
            OpHistoryManager.addHistoryItem(HISTORY_TYPE_PROFILE, item, true);
            sendNotification(item.getProfileName(), Activity.RESULT_OK, notify, profileManager.requiresRestart());
        } catch (IOException e) {
            sendNotification(item.getProfileName(), Activity.RESULT_CANCELED, notify, false);
        } finally {
            if (tempProfilePath != null) {
                tempProfilePath.delete();
            }
        }
    }

    @Override
    protected void onQueued(@Nullable Intent intent) {
        if (intent == null) return;
        ProfileQueueItem item = IntentCompat.getParcelableExtra(intent, EXTRA_QUEUE_ITEM, ProfileQueueItem.class);
        if (item == null) {
            return;
        }
        Object notificationInfo = new NotificationProgressHandler.NotificationInfo()
                .setAutoCancel(true)
                .setTime(System.currentTimeMillis())
                .setOperationName(getText(R.string.profiles))
                .setTitle(item.getProfileName())
                .setBody(getString(R.string.added_to_queue));
        mProgressHandler.onQueue(notificationInfo);
    }

    @Override
    protected void onStartIntent(@Nullable Intent intent) {
        if (intent == null) return;
        ProfileQueueItem item = IntentCompat.getParcelableExtra(intent, EXTRA_QUEUE_ITEM, ProfileQueueItem.class);
        if (item != null) {
            Intent notificationIntent = AppsProfileActivity.getProfileIntent(this, item.getProfileId());
            PendingIntent pendingIntent = PendingIntentCompat.getActivity(this, 0, notificationIntent,
                    0, false);
            mNotificationInfo.setDefaultAction(pendingIntent);
        }
        // Set profile name in the ongoing notification
        mNotificationInfo.setTitle(item != null ? item.getProfileName() : null);
        mProgressHandler.onProgressStart(-1, 0, mNotificationInfo);
    }

    @Override
    public void onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        if (mProgressHandler != null) {
            mProgressHandler.onDetach(this);
        }
        CpuUtils.releaseWakeLock(mWakeLock);
        super.onDestroy();
    }

    private void sendNotification(@NonNull String profileName, int result, boolean notify,
                                  boolean requiresRestart) {
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
            PendingIntent pendingIntent = PendingIntentCompat.getActivity(this, 0, intent,
                    PendingIntent.FLAG_ONE_SHOT, false);
            notificationInfo.addAction(0, getString(R.string.restart_device), pendingIntent);
        }
        mProgressHandler.onResult(notify ? notificationInfo : null);
    }
}
