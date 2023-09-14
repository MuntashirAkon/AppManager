// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.app.ServiceCompat;

import java.util.ArrayList;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.main.MainActivity;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler.NotificationManagerInfo;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.progress.QueuedProgressHandler;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
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
     * Notification channel ID
     */
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.BATCH_OPS";

    @BatchOpsManager.OpType
    private int mOp = BatchOpsManager.OP_NONE;
    @Nullable
    private ArrayList<String> mPackages;
    private Bundle mArgs;
    private String mHeader;
    private QueuedProgressHandler mProgressHandler;
    private NotificationProgressHandler.NotificationInfo mNotificationInfo;
    private PowerManager.WakeLock mWakeLock;

    public BatchOpsService() {
        super("BatchOpsService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mWakeLock = CpuUtils.getPartialWakeLock("batch_ops");
        mWakeLock.acquire();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (isWorking()) return super.onStartCommand(intent, flags, startId);
        if (intent != null) {
            mOp = intent.getIntExtra(EXTRA_OP, BatchOpsManager.OP_NONE);
        }
        mHeader = getHeader(intent);
        NotificationManagerInfo notificationManagerInfo = new NotificationManagerInfo(CHANNEL_ID,
                "Batch Ops Progress", NotificationManagerCompat.IMPORTANCE_LOW);
        mProgressHandler = new NotificationProgressHandler(this,
                notificationManagerInfo,
                NotificationUtils.HIGH_PRIORITY_NOTIFICATION_INFO,
                NotificationUtils.HIGH_PRIORITY_NOTIFICATION_INFO);
        mProgressHandler.setProgressTextInterface(ProgressHandler.PROGRESS_REGULAR);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntentCompat.getActivity(this, 0, notificationIntent, 0, false);
        mNotificationInfo = new NotificationProgressHandler.NotificationInfo()
                .setOperationName(mHeader)
                .setBody(getString(R.string.operation_running))
                .setDefaultAction(pendingIntent);
        mProgressHandler.onAttach(this, mNotificationInfo);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) {
            sendResults(Activity.RESULT_CANCELED, null);
            return;
        }
        mOp = intent.getIntExtra(EXTRA_OP, BatchOpsManager.OP_NONE);
        mPackages = intent.getStringArrayListExtra(EXTRA_OP_PKG);
        if (mPackages == null) {
            mPackages = new ArrayList<>(0);
        }
        if (mOp == BatchOpsManager.OP_NONE) {
            sendResults(Activity.RESULT_CANCELED, null);
            return;
        }
        mArgs = intent.getBundleExtra(EXTRA_OP_EXTRA_ARGS);
        ArrayList<Integer> userHandles = intent.getIntegerArrayListExtra(EXTRA_OP_USERS);
        if (userHandles == null) {
            userHandles = new ArrayList<>(mPackages.size());
            for (String ignore : mPackages) {
                userHandles.add(UserHandleHidden.myUserId());
            }
        }
        sendStarted();
        // Update progress
        if (mProgressHandler != null) {
            mProgressHandler.postUpdate(mPackages.size(), 0);
        }
        BatchOpsManager batchOpsManager = new BatchOpsManager();
        batchOpsManager.setArgs(mArgs);
        BatchOpsManager.Result result = batchOpsManager.performOp(mOp, mPackages, userHandles, mProgressHandler);
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
        mProgressHandler.onQueue(notificationInfo);
    }

    @Override
    protected void onStartIntent(@Nullable Intent intent) {
        if (intent == null) return;
        int op = intent.getIntExtra(EXTRA_OP, BatchOpsManager.OP_NONE);
        mHeader = getHeader(intent);
        mNotificationInfo.setTitle(getDesiredOpTitle(op)).setOperationName(mHeader);
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

    private void sendStarted() {
        Intent broadcastIntent = new Intent(ACTION_BATCH_OPS_STARTED);
        broadcastIntent.putExtra(EXTRA_OP, mOp);
        broadcastIntent.putExtra(EXTRA_OP_PKG, mPackages != null ? mPackages.toArray(new String[0]) : new String[0]);
        sendBroadcast(broadcastIntent);
    }

    private void sendResults(int result, @Nullable BatchOpsManager.Result opResult) {
        Intent broadcastIntent = new Intent(ACTION_BATCH_OPS_COMPLETED);
        broadcastIntent.putExtra(EXTRA_OP, mOp);
        broadcastIntent.putExtra(EXTRA_OP_PKG, mPackages != null ? mPackages.toArray(new String[0]) : new String[0]);
        broadcastIntent.putExtra(EXTRA_FAILED_PKG, opResult != null ? opResult.getFailedPackages().toArray(new String[0]) : new String[0]);
        sendBroadcast(broadcastIntent);
        sendNotification(result, opResult);
    }

    private void sendNotification(int result, @Nullable BatchOpsManager.Result opResult) {
        String contentTitle = getDesiredOpTitle(mOp);
        NotificationProgressHandler.NotificationInfo notificationInfo = new NotificationProgressHandler.NotificationInfo()
                .setAutoCancel(true)
                .setTime(System.currentTimeMillis())
                .setOperationName(mHeader)
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
                intent.putExtra(EXTRA_OP, mOp);
                intent.putExtra(EXTRA_OP_EXTRA_ARGS, mArgs);
                intent.putExtra(EXTRA_FAILURE_MESSAGE, message);
                intent.putStringArrayListExtra(EXTRA_FAILED_PKG, opResult.getFailedPackages());
                intent.putIntegerArrayListExtra(EXTRA_OP_USERS, opResult.getAssociatedUserHandles());
                PendingIntent pendingIntent = PendingIntentCompat.getActivity(this, 0, intent,
                        PendingIntent.FLAG_ONE_SHOT, false);
                notificationInfo.setDefaultAction(pendingIntent);
                notificationInfo.setBody(message + detailsMessage);
        }
        if (opResult != null && opResult.requiresRestart()) {
            Intent intent = new Intent(this, BatchOpsResultsActivity.class);
            intent.putExtra(EXTRA_REQUIRES_RESTART, true);
            PendingIntent pendingIntent = PendingIntentCompat.getActivity(this, 0, intent,
                    PendingIntent.FLAG_ONE_SHOT, false);
            notificationInfo.addAction(0, getString(R.string.restart_device), pendingIntent);
        }
        mProgressHandler.onResult(notificationInfo);
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
        switch (mOp) {
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
