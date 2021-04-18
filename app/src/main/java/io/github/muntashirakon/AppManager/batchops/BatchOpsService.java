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
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.main.MainActivity;
import io.github.muntashirakon.AppManager.misc.AlertDialogActivity;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

public class BatchOpsService extends ForegroundService {
    /**
     * The {@link String} to be placed in the notification header. Default: "Batch Operations"
     */
    public static final String EXTRA_HEADER = "EXTRA_HEADER";
    /**
     * Name of the batch operation, {@link Integer} value.
     */
    public static final String EXTRA_OP = "EXTRA_OP";
    /**
     * An {@link ArrayList} of package names (string value) on which operations will be carried out.
     */
    public static final String EXTRA_OP_PKG = "EXTRA_OP_PKG";
    /**
     * An {@link ArrayList} of user handles associated with each package.
     */
    public static final String EXTRA_OP_USERS = "EXTRA_OP_USERS";
    /**
     * A {@link Bundle} containing additional arguments, these arguments are unwrapped by
     * {@link BatchOpsManager} based on necessity.
     */
    public static final String EXTRA_OP_EXTRA_ARGS = "EXTRA_OP_EXTRA_ARGS";
    /**
     * An {@link ArrayList} of package name (string value) which are failed after the batch
     * operation is complete.
     */
    public static final String EXTRA_FAILED_PKG = "EXTRA_FAILED_PKG_ARR";
    /**
     * The failure message
     */
    public static final String EXTRA_FAILURE_MESSAGE = "EXTRA_FAILURE_MESSAGE";
    /**
     * The progress message to be used with {@link #ACTION_BATCH_OPS_PROGRESS}
     */
    public static final String EXTRA_PROGRESS_MESSAGE = "EXTRA_PROGRESS_MESSAGE";
    /**
     * Max value for progress, to be used with {@link #ACTION_BATCH_OPS_PROGRESS}
     */
    public static final String EXTRA_PROGRESS_MAX = "EXTRA_PROGRESS_MAX";
    /**
     * Current value for progress, to be used with {@link #ACTION_BATCH_OPS_PROGRESS}
     */
    public static final String EXTRA_PROGRESS_CURRENT = "EXTRA_PROGRESS_CURRENT";

    /**
     * Send to the appropriate broadcast receiver denoting that the batch operation is completed. It
     * includes the following extras:
     * <ul>
     *     <li>
     *         {@link #EXTRA_OP} is the integer value denoting the type of operation.
     *     </li>
     *     <li>
     *         {@link #EXTRA_OP_PKG} is the array of packages on which operations were carried out. Never null.
     *     </li>
     *     <li>
     *         {@link #EXTRA_FAILED_PKG} is the array of failed packages. Never null.
     *     </li>
     * </ul>
     */
    public static final String ACTION_BATCH_OPS_COMPLETED = BuildConfig.APPLICATION_ID + ".action.BATCH_OPS_COMPLETED";
    public static final String ACTION_BATCH_OPS_STARTED = BuildConfig.APPLICATION_ID + ".action.BATCH_OPS_STARTED";
    /**
     * Send progress info to appropriate broadcast receiver. It includes the following extras:
     * <ul>
     *     <li>
     *         {@link #EXTRA_PROGRESS_MESSAGE} is the message displayed in the progress area, should
     *         be the app label
     *     </li>
     *     <li>
     *         {@link #EXTRA_PROGRESS_MAX} is the maximum progress (the upper limit), should be
     *         equal to the package count
     *     </li>
     *     <li>
     *         {@link #EXTRA_PROGRESS_CURRENT} is the current progress
     *     </li>
     * </ul>
     */
    public static final String ACTION_BATCH_OPS_PROGRESS = BuildConfig.APPLICATION_ID + ".action.BATCH_OPS_PROGRESS";

    /**
     * Notification channel ID
     */
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.BATCH_OPS";
    public static final int NOTIFICATION_ID = 1;

    @BatchOpsManager.OpType
    private int op = BatchOpsManager.OP_NONE;
    @Nullable
    private ArrayList<String> packages;
    private Bundle args;
    private String header;
    private NotificationCompat.Builder builder;
    private NotificationManagerCompat notificationManager;
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            if (intent.getAction() == null) return;
            if (ACTION_BATCH_OPS_PROGRESS.equals(intent.getAction())) {
                if (notificationManager == null) return;
                int progressMax = intent.getIntExtra(EXTRA_PROGRESS_MAX, 0);
                String progressMessage = intent.getStringExtra(EXTRA_PROGRESS_MESSAGE);
                if (progressMessage == null)
                    progressMessage = getString(R.string.operation_running);
                builder.setContentText(progressMessage);
                builder.setProgress(progressMax, intent.getIntExtra(EXTRA_PROGRESS_CURRENT, 0), progressMax == 0);
                notificationManager.notify(NOTIFICATION_ID, builder.build());
            }
        }
    };

    public BatchOpsService() {
        super("BatchOpsService");
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null) {
            op = intent.getIntExtra(EXTRA_OP, BatchOpsManager.OP_NONE);
            header = intent.getStringExtra(EXTRA_HEADER);
        }
        if (header == null) header = getString(R.string.batch_ops);
        notificationManager = NotificationUtils.getNewNotificationManager(this, CHANNEL_ID,
                "Batch Ops Progress", NotificationManagerCompat.IMPORTANCE_LOW);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);
        builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getDesiredOpTitle())
                .setContentText(getString(R.string.operation_running))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setSubText(header)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(0, 0, true)
                .setContentIntent(pendingIntent);
        startForeground(NOTIFICATION_ID, builder.build());
        registerReceiver(broadcastReceiver, new IntentFilter(ACTION_BATCH_OPS_PROGRESS));
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        try {
            if (intent == null) {
                sendResults(Activity.RESULT_CANCELED, null);
                return;
            }
            op = intent.getIntExtra(EXTRA_OP, BatchOpsManager.OP_NONE);
            packages = intent.getStringArrayListExtra(EXTRA_OP_PKG);
            if (packages == null) return;
            args = intent.getBundleExtra(EXTRA_OP_EXTRA_ARGS);
            ArrayList<Integer> userHandles = intent.getIntegerArrayListExtra(EXTRA_OP_USERS);
            if (userHandles == null) {
                userHandles = new ArrayList<>(packages.size());
                for (String ignore : packages) userHandles.add(Users.getCurrentUserHandle());
            }
            if (op == BatchOpsManager.OP_NONE || packages == null) {
                sendResults(Activity.RESULT_CANCELED, null);
                return;
            }
            sendStarted();
            BatchOpsManager batchOpsManager = new BatchOpsManager();
            batchOpsManager.setArgs(args);
            BatchOpsManager.Result result = batchOpsManager.performOp(op, packages, userHandles);
            if (result.isSuccessful()) {
                sendResults(Activity.RESULT_OK, null);
            } else {
                sendResults(Activity.RESULT_FIRST_USER, result);
            }
        } finally {
            stopForeground(true);
            // Hack to remove ongoing notification
            notificationManager.deleteNotificationChannel(CHANNEL_ID);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(broadcastReceiver);
        super.onDestroy();
    }

    private void sendStarted() {
        Intent broadcastIntent = new Intent(ACTION_BATCH_OPS_STARTED);
        broadcastIntent.putExtra(EXTRA_OP, op);
        broadcastIntent.putExtra(EXTRA_OP_PKG, packages != null ? packages.toArray(new String[0]) : new String[0]);
        sendBroadcast(broadcastIntent);
    }

    private void sendResults(int result, @Nullable BatchOpsManager.Result opResult) {
        Intent broadcastIntent = new Intent(ACTION_BATCH_OPS_COMPLETED);
        broadcastIntent.putExtra(EXTRA_OP, op);
        broadcastIntent.putExtra(EXTRA_OP_PKG, packages != null ? packages.toArray(new String[0]) : new String[0]);
        broadcastIntent.putExtra(EXTRA_FAILED_PKG, opResult != null ? opResult.getFailedPackages().toArray(new String[0]) : new String[0]);
        sendBroadcast(broadcastIntent);
        sendNotification(result, opResult);
    }

    private void sendNotification(int result, BatchOpsManager.Result opResult) {
        String contentTitle = getDesiredOpTitle();
        NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(this);
        builder.setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setTicker(contentTitle)
                .setContentTitle(contentTitle)
                .setSubText(header);
        switch (result) {
            case Activity.RESULT_CANCELED:  // Cancelled
                break;
            case Activity.RESULT_OK:  // Successful
                builder.setContentText(getString(R.string.the_operation_was_successful));
                break;
            case Activity.RESULT_FIRST_USER:  // Failed
                String detailsMessage = getString(R.string.full_stop_tap_to_see_details);
                String message = getDesiredErrorString(opResult.getFailedPackages().size());
                Intent intent = new Intent(this, AlertDialogActivity.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    intent.setIdentifier(String.valueOf(System.currentTimeMillis()));
                }
                intent.putExtra(EXTRA_OP, op);
                intent.putExtra(EXTRA_OP_EXTRA_ARGS, args);
                intent.putExtra(EXTRA_FAILURE_MESSAGE, message);
                intent.putStringArrayListExtra(EXTRA_FAILED_PKG, opResult.getFailedPackages());
                intent.putIntegerArrayListExtra(EXTRA_OP_USERS, opResult.getAssociatedUserHandles());
                PendingIntent pendingIntent = PendingIntent.getActivity(this,
                        0, intent, PendingIntent.FLAG_ONE_SHOT);
                builder.setContentIntent(pendingIntent);
                builder.setContentText(message + detailsMessage);
        }
        NotificationUtils.displayHighPriorityNotification(this, builder.build());
    }

    @NonNull
    private String getDesiredOpTitle() {
        switch (op) {
            case BatchOpsManager.OP_BACKUP:
            case BatchOpsManager.OP_DELETE_BACKUP:
            case BatchOpsManager.OP_RESTORE_BACKUP:
                return getString(R.string.backup_restore);
            case BatchOpsManager.OP_BACKUP_APK:
                return getString(R.string.save_apk);
            case BatchOpsManager.OP_BLOCK_TRACKERS:
                return getString(R.string.block_trackers);
            case BatchOpsManager.OP_CLEAR_DATA:
                return getString(R.string.clear_data);
            case BatchOpsManager.OP_DISABLE:
                return getString(R.string.disable);
            case BatchOpsManager.OP_DISABLE_BACKGROUND:
                return getString(R.string.disable_background);
            case BatchOpsManager.OP_ENABLE:
                return getString(R.string.enable);
            case BatchOpsManager.OP_EXPORT_RULES:
                return getString(R.string.export_blocking_rules);
            case BatchOpsManager.OP_FORCE_STOP:
                return getString(R.string.force_stop);
            case BatchOpsManager.OP_UNINSTALL:
                return getString(R.string.uninstall);
            case BatchOpsManager.OP_UNBLOCK_TRACKERS:
                return getString(R.string.unblock_trackers);
            case BatchOpsManager.OP_BLOCK_COMPONENTS:
                return getString(R.string.block_components_dots);
            case BatchOpsManager.OP_SET_APP_OPS:
                return getString(R.string.set_mode_for_app_ops_dots);
            case BatchOpsManager.OP_NONE:
                break;
        }
        return getString(R.string.batch_ops);
    }

    private String getDesiredErrorString(int failedCount) {
        switch (op) {
            case BatchOpsManager.OP_BACKUP:
                return getResources().getQuantityString(R.plurals.alert_failed_to_backup, failedCount, failedCount);
            case BatchOpsManager.OP_DELETE_BACKUP:
                return getResources().getQuantityString(R.plurals.alert_failed_to_delete_backup, failedCount, failedCount);
            case BatchOpsManager.OP_RESTORE_BACKUP:
                return getResources().getQuantityString(R.plurals.alert_failed_to_restore, failedCount, failedCount);
            case BatchOpsManager.OP_EXPORT_RULES:
            case BatchOpsManager.OP_NONE:
                break;
            case BatchOpsManager.OP_BACKUP_APK:
                return getResources().getQuantityString(R.plurals.failed_to_backup_some_apk_files, failedCount, failedCount);
            case BatchOpsManager.OP_BLOCK_TRACKERS:
                return getResources().getQuantityString(R.plurals.alert_failed_to_disable_trackers, failedCount, failedCount);
            case BatchOpsManager.OP_CLEAR_DATA:
                return getResources().getQuantityString(R.plurals.alert_failed_to_clear_data, failedCount, failedCount);
            case BatchOpsManager.OP_DISABLE:
                return getResources().getQuantityString(R.plurals.alert_failed_to_disable, failedCount, failedCount);
            case BatchOpsManager.OP_ENABLE:
                return getResources().getQuantityString(R.plurals.alert_failed_to_enable, failedCount, failedCount);
            case BatchOpsManager.OP_DISABLE_BACKGROUND:
                return getResources().getQuantityString(R.plurals.alert_failed_to_disable_background, failedCount, failedCount);
            case BatchOpsManager.OP_FORCE_STOP:
                return getResources().getQuantityString(R.plurals.alert_failed_to_force_stop, failedCount, failedCount);
            case BatchOpsManager.OP_UNINSTALL:
                return getResources().getQuantityString(R.plurals.alert_failed_to_uninstall, failedCount, failedCount);
            case BatchOpsManager.OP_UNBLOCK_TRACKERS:
                return getResources().getQuantityString(R.plurals.alert_failed_to_unblock_trackers, failedCount, failedCount);
            case BatchOpsManager.OP_BLOCK_COMPONENTS:
                return getResources().getQuantityString(R.plurals.alert_failed_to_block_components, failedCount, failedCount);
            case BatchOpsManager.OP_SET_APP_OPS:
                return getResources().getQuantityString(R.plurals.alert_failed_to_set_app_ops, failedCount, failedCount);
        }
        return getString(R.string.error);
    }
}
