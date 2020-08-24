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

package io.github.muntashirakon.AppManager.batchops;

import android.app.Activity;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import java.util.ArrayList;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.main.MainActivity;
import io.github.muntashirakon.AppManager.misc.AlertDialogActivity;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

public class BatchOpsService extends IntentService {
    /**
     * Name of the batch operation, integer value.
     */
    public static final String EXTRA_OP = "EXTRA_OP";
    /**
     * Flags set for the batch operation
     */
    public static final String EXTRA_OP_FLAGS = "EXTRA_OP_FLAGS";
    /**
     * An ArrayList of package names (string value) on which operations will be carried out.
     */
    public static final String EXTRA_OP_PKG = "EXTRA_OP_PKG_ARR";
    /**
     * An ArrayList of package name (string value) which are failed after the batch operation is
     * complete.
     */
    public static final String EXTRA_FAILED_PKG = "EXTRA_FAILED_PKG_ARR";
    /**
     * The failure message
     */
    public static final String EXTRA_FAILURE_MESSAGE = "EXTRA_FAILURE_MESSAGE";

    /**
     * Send to the appropriate broadcast receiver denoting that the batch operation is completed
     */
    public static final String ACTION_BATCH_OPS = BuildConfig.APPLICATION_ID + ".action.BATCH_OPS";

    /**
     * Notification channel ID
     */
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.BATCH_OPS";
    public static final int NOTIFICATION_ID = 1;

    public BatchOpsService() {
        super("BatchOpsService");
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.batch_ops))
                .setContentText(null)  // TODO: Add suitable string based on op value
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(NOTIFICATION_ID, notification);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) {
            sendResults(-1, Activity.RESULT_CANCELED, null);
            return;
        }
        @BatchOpsManager.OpType int op = intent.getIntExtra(EXTRA_OP, -1);
        int flags = intent.getIntExtra(EXTRA_OP_FLAGS, 0);
        ArrayList<String> packages = intent.getStringArrayListExtra(EXTRA_OP_PKG);
        if (op == -1 || packages == null) {
            sendResults(op, Activity.RESULT_CANCELED, null);
            return;
        }
        BatchOpsManager batchOpsManager = new BatchOpsManager();
        batchOpsManager.setFlags(flags);
        BatchOpsManager.Result result = batchOpsManager.performOp(op, packages);
        if (result.isSuccessful()) {
            sendResults(op, Activity.RESULT_OK, null);
        } else sendResults(op, Activity.RESULT_FIRST_USER,
                new ArrayList<>(result.failedPackages()));
    }

    private void sendResults(int op, int result, @Nullable ArrayList<String> failedPackages) {
        Intent intent = new Intent(ACTION_BATCH_OPS);
        sendBroadcast(intent);
        sendNotification(op, result, failedPackages);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_ID,
                    "BatchOpsServiceChannel", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(serviceChannel);
        }
    }

    private void sendNotification(int op, int result, @Nullable ArrayList<String> failedPackages) {
        NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(this);
        builder.setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setTicker(getString(R.string.batch_ops))
                .setContentTitle(getString(R.string.batch_ops));
        switch (result) {
            case Activity.RESULT_CANCELED:  // Cancelled
                break;
            case Activity.RESULT_OK:  // Successful
                builder.setContentText(getString(R.string.the_operation_was_successful));
                break;
            case Activity.RESULT_FIRST_USER:  // Failed
                if (failedPackages != null) {
                    String message = getDesiredErrorString(op, failedPackages.size());
                    Intent intent = new Intent(this, AlertDialogActivity.class);
                    intent.putExtra(EXTRA_FAILURE_MESSAGE, message);
                    intent.putStringArrayListExtra(EXTRA_FAILED_PKG, failedPackages);
                    PendingIntent pendingIntent = PendingIntent.getActivity(this,
                            0, intent, 0);
                    builder.setContentIntent(pendingIntent);
                    builder.setContentText(message);
                } else builder.setContentText(getString(R.string.error));
        }
        NotificationUtils.displayHighPriorityNotification(builder.build());
    }

    private String getDesiredErrorString(@BatchOpsManager.OpType int op, int failedCount) {
        switch (op) {
            case BatchOpsManager.OP_BACKUP:
                return getResources().getQuantityString(R.plurals.alert_failed_to_backup, failedCount, failedCount);
            case BatchOpsManager.OP_DELETE_BACKUP:
                return getResources().getQuantityString(R.plurals.alert_failed_to_delete_backup, failedCount, failedCount);
            case BatchOpsManager.OP_RESTORE_BACKUP:
                return getResources().getQuantityString(R.plurals.alert_failed_to_restore, failedCount, failedCount);
            case BatchOpsManager.OP_EXPORT_RULES:
                break;
            case BatchOpsManager.OP_BACKUP_APK:
                return getResources().getQuantityString(R.plurals.failed_to_backup_some_apk_files, failedCount, failedCount);
            case BatchOpsManager.OP_BLOCK_TRACKERS:
                return getResources().getQuantityString(R.plurals.alert_failed_to_disable_trackers, failedCount, failedCount);
            case BatchOpsManager.OP_CLEAR_DATA:
                return getResources().getQuantityString(R.plurals.alert_failed_to_clear_data, failedCount, failedCount);
            case BatchOpsManager.OP_DISABLE:
                return getResources().getQuantityString(R.plurals.alert_failed_to_disable, failedCount, failedCount);
            case BatchOpsManager.OP_DISABLE_BACKGROUND:
                return getResources().getQuantityString(R.plurals.alert_failed_to_disable_background, failedCount, failedCount);
            case BatchOpsManager.OP_FORCE_STOP:
                return getResources().getQuantityString(R.plurals.alert_failed_to_force_stop, failedCount, failedCount);
            case BatchOpsManager.OP_UNINSTALL:
                return getResources().getQuantityString(R.plurals.alert_failed_to_uninstall, failedCount, failedCount);
        }
        return getString(R.string.error);
    }
}
