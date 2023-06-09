// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;

import java.util.ArrayList;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.PendingIntentCompat;
import io.github.muntashirakon.AppManager.main.MainActivity;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler;
import io.github.muntashirakon.AppManager.progress.QueuedProgressHandler;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

public class BatchOpsService extends ForegroundService {
    /**
     * The {@link String} to be placed in the notification header. Default: "Batch Operations"
     */
    public static final String EXTRA_HEADER = "EXTRA_HEADER";
    /**
     * Name of the batch operation, {@link Integer} value. One of the {@link BatchOpsManager.OpType}.
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
     * The failure message.
     */
    public static final String EXTRA_FAILURE_MESSAGE = "EXTRA_FAILURE_MESSAGE";
    /**
     * Boolean value to describe whether a reboot is required.
     */
    public static final String EXTRA_REQUIRES_RESTART = "requires_restart";
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

    @BatchOpsManager.OpType
    private int op = BatchOpsManager.OP_NONE;
    @Nullable
    private ArrayList<String> packages;
    private Bundle args;
    private String header;
    private QueuedProgressHandler progressHandler;
    private NotificationProgressHandler.NotificationInfo notificationInfo;
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            if (intent.getAction() == null) return;
            if (ACTION_BATCH_OPS_PROGRESS.equals(intent.getAction())) {
                int progressMax = intent.getIntExtra(EXTRA_PROGRESS_MAX, 0);
                CharSequence progressMessage = intent.getCharSequenceExtra(EXTRA_PROGRESS_MESSAGE);
                if (progressMessage == null) {
                    progressMessage = getString(R.string.operation_running);
                }
                notificationInfo.setBody(progressMessage);
                progressHandler.onProgressUpdate(progressMax, intent.getIntExtra(EXTRA_PROGRESS_CURRENT, 0), notificationInfo);
            }
        }
    };

    public BatchOpsService() {
        super("BatchOpsService");
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (isWorking()) return super.onStartCommand(intent, flags, startId);
        if (intent != null) {
            op = intent.getIntExtra(EXTRA_OP, BatchOpsManager.OP_NONE);
        }
        header = getHeader(intent);
        progressHandler = new NotificationProgressHandler(this,
                new NotificationProgressHandler.NotificationManagerInfo(CHANNEL_ID, "Batch Ops Progress", NotificationManagerCompat.IMPORTANCE_LOW),
                NotificationUtils.HIGH_PRIORITY_NOTIFICATION_INFO,
                NotificationUtils.HIGH_PRIORITY_NOTIFICATION_INFO);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        @SuppressLint("WrongConstant")
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntentCompat.FLAG_IMMUTABLE);
        notificationInfo = new NotificationProgressHandler.NotificationInfo()
                .setOperationName(header)
                .setBody(getString(R.string.operation_running))
                .setDefaultAction(pendingIntent);
        progressHandler.onAttach(this, notificationInfo);
        registerReceiver(broadcastReceiver, new IntentFilter(ACTION_BATCH_OPS_PROGRESS));
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) {
            sendResults(Activity.RESULT_CANCELED, null);
            return;
        }
        op = intent.getIntExtra(EXTRA_OP, BatchOpsManager.OP_NONE);
        packages = intent.getStringArrayListExtra(EXTRA_OP_PKG);
        if (packages == null) {
            packages = new ArrayList<>(0);
        }
        if (op == BatchOpsManager.OP_NONE) return;
        args = intent.getBundleExtra(EXTRA_OP_EXTRA_ARGS);
        ArrayList<Integer> userHandles = intent.getIntegerArrayListExtra(EXTRA_OP_USERS);
        if (userHandles == null) {
            userHandles = new ArrayList<>(packages.size());
            for (String ignore : packages) {
                userHandles.add(UserHandleHidden.myUserId());
            }
        }
        if (op == BatchOpsManager.OP_NONE) {
            sendResults(Activity.RESULT_CANCELED, null);
            return;
        }
        sendStarted();
        BatchOpsManager batchOpsManager = new BatchOpsManager();
        batchOpsManager.setArgs(args);
        BatchOpsManager.Result result = batchOpsManager.performOp(op, packages, userHandles);
        batchOpsManager.conclude();
        if (result.isSuccessful()) {
            sendResults(Activity.RESULT_OK, result);
        } else {
            sendResults(Activity.RESULT_FIRST_USER, result);
        }
    }

    @Override
    protected void onQueued(@Nullable Intent intent) {
        if (intent == null) return;
        int op = intent.getIntExtra(EXTRA_OP, BatchOpsManager.OP_NONE);
        String opTitle = getDesiredOpTitle(op);
        Object notificationInfo = new NotificationProgressHandler.NotificationInfo()
                .setAutoCancel(true)
                .setTime(System.currentTimeMillis())
                .setOperationName(getHeader(intent))
                .setTitle(opTitle)
                .setBody(getString(R.string.added_to_queue));
        progressHandler.onQueue(notificationInfo);
    }

    @Override
    protected void onStartIntent(@Nullable Intent intent) {
        if (intent == null) return;
        int op = intent.getIntExtra(EXTRA_OP, BatchOpsManager.OP_NONE);
        header = getHeader(intent);
        notificationInfo.setTitle(getDesiredOpTitle(op)).setOperationName(header);
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
        unregisterReceiver(broadcastReceiver);
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
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

    private void sendNotification(int result, @Nullable BatchOpsManager.Result opResult) {
        String contentTitle = getDesiredOpTitle(op);
        NotificationProgressHandler.NotificationInfo notificationInfo = new NotificationProgressHandler.NotificationInfo()
                .setAutoCancel(true)
                .setTime(System.currentTimeMillis())
                .setOperationName(header)
                .setTitle(contentTitle);
        switch (result) {
            case Activity.RESULT_CANCELED:  // Cancelled
                break;
            case Activity.RESULT_OK:  // Successful
                notificationInfo.setBody(getString(R.string.the_operation_was_successful));
                break;
            case Activity.RESULT_FIRST_USER:  // Failed
                Objects.requireNonNull(opResult);
                String detailsMessage = getString(R.string.full_stop_tap_to_see_details);
                String message = getDesiredErrorString(opResult.getFailedPackages().size());
                Intent intent = new Intent(this, BatchOpsResultsActivity.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    intent.setIdentifier(String.valueOf(System.currentTimeMillis()));
                }
                intent.putExtra(EXTRA_OP, op);
                intent.putExtra(EXTRA_OP_EXTRA_ARGS, args);
                intent.putExtra(EXTRA_FAILURE_MESSAGE, message);
                intent.putStringArrayListExtra(EXTRA_FAILED_PKG, opResult.getFailedPackages());
                intent.putIntegerArrayListExtra(EXTRA_OP_USERS, opResult.getAssociatedUserHandles());
                @SuppressLint("WrongConstant")
                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntentCompat.FLAG_IMMUTABLE);
                notificationInfo.setDefaultAction(pendingIntent);
                notificationInfo.setBody(message + detailsMessage);
        }
        if (opResult != null && opResult.requiresRestart()) {
            Intent intent = new Intent(this, BatchOpsResultsActivity.class);
            intent.putExtra(EXTRA_REQUIRES_RESTART, true);
            @SuppressLint("WrongConstant")
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntentCompat.FLAG_IMMUTABLE);
            notificationInfo.addAction(0, getString(R.string.restart_device), pendingIntent);
        }
        progressHandler.onResult(notificationInfo);
    }

    @NonNull
    public String getHeader(@Nullable Intent intent) {
        if (intent != null) {
            return intent.getStringExtra(EXTRA_HEADER);
        }
        return getString(R.string.batch_ops);
    }

    @NonNull
    private String getDesiredOpTitle(@BatchOpsManager.OpType int op) {
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
            case BatchOpsManager.OP_CLEAR_CACHE:
                return getString(R.string.clear_cache);
            case BatchOpsManager.OP_FREEZE:
                return getString(R.string.freeze);
            case BatchOpsManager.OP_DISABLE_BACKGROUND:
                return getString(R.string.disable_background);
            case BatchOpsManager.OP_UNFREEZE:
                return getString(R.string.unfreeze);
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
            case BatchOpsManager.OP_UNBLOCK_COMPONENTS:
                return getString(R.string.unblock_components_dots);
            case BatchOpsManager.OP_SET_APP_OPS:
                return getString(R.string.set_mode_for_app_ops_dots);
            case BatchOpsManager.OP_IMPORT_BACKUPS:
                return getString(R.string.pref_import_backups);
            case BatchOpsManager.OP_DEXOPT:
                return getString(R.string.batch_ops_runtime_optimization);
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
            case BatchOpsManager.OP_FREEZE:
                return getResources().getQuantityString(R.plurals.alert_failed_to_freeze, failedCount, failedCount);
            case BatchOpsManager.OP_UNFREEZE:
                return getResources().getQuantityString(R.plurals.alert_failed_to_unfreeze, failedCount, failedCount);
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
            case BatchOpsManager.OP_UNBLOCK_COMPONENTS:
                return getResources().getQuantityString(R.plurals.alert_failed_to_unblock_components, failedCount, failedCount);
            case BatchOpsManager.OP_SET_APP_OPS:
                return getResources().getQuantityString(R.plurals.alert_failed_to_set_app_ops, failedCount, failedCount);
            case BatchOpsManager.OP_IMPORT_BACKUPS:
                return getResources().getQuantityString(R.plurals.alert_failed_to_import_backups, failedCount, failedCount);
            case BatchOpsManager.OP_DEXOPT:
                return getResources().getQuantityString(R.plurals.alert_failed_to_optimize_apps, failedCount, failedCount);
        }
        return getString(R.string.error);
    }
}
