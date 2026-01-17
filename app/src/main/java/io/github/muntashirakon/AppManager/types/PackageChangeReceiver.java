// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import static io.github.muntashirakon.AppManager.batchops.BatchOpsService.ACTION_BATCH_OPS_COMPLETED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.core.os.BundleCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;

public abstract class PackageChangeReceiver extends BroadcastReceiver {
    /**
     * Specifies that some packages have been altered. This could be due to batch operations, database update, etc.
     * It has one extra namely {@link Intent#EXTRA_CHANGED_PACKAGE_LIST}.
     */
    public static final String ACTION_PACKAGE_ALTERED = BuildConfig.APPLICATION_ID + ".action.PACKAGE_ALTERED";
    /**
     * Specifies that some packages have been added. This could be due to batch operations, database update, etc.
     * It has one extra namely {@link Intent#EXTRA_CHANGED_PACKAGE_LIST}.
     */
    public static final String ACTION_PACKAGE_ADDED = BuildConfig.APPLICATION_ID + ".action.PACKAGE_ADDED";
    /**
     * Specifies that some packages have been removed. This could be due to batch operations, database update, etc.
     * It has one extra namely {@link Intent#EXTRA_CHANGED_PACKAGE_LIST}.
     */
    public static final String ACTION_PACKAGE_REMOVED = BuildConfig.APPLICATION_ID + ".action.PACKAGE_REMOVED";

    /**
     * Specifies that some packages have been altered. This could be due to batch operations, database update, etc.
     * It has one extra namely {@link Intent#EXTRA_CHANGED_PACKAGE_LIST}.
     */
    public static final String ACTION_DB_PACKAGE_ALTERED = BuildConfig.APPLICATION_ID + ".action.DB_PACKAGE_ALTERED";
    /**
     * Specifies that some packages have been added. This could be due to batch operations, database update, etc.
     * It has one extra namely {@link Intent#EXTRA_CHANGED_PACKAGE_LIST}.
     */
    public static final String ACTION_DB_PACKAGE_ADDED = BuildConfig.APPLICATION_ID + ".action.DB_PACKAGE_ADDED";
    /**
     * Specifies that some packages have been removed. This could be due to batch operations, database update, etc.
     * It has one extra namely {@link Intent#EXTRA_CHANGED_PACKAGE_LIST}.
     */
    public static final String ACTION_DB_PACKAGE_REMOVED = BuildConfig.APPLICATION_ID + ".action.DB_PACKAGE_REMOVED";

    public PackageChangeReceiver(@NonNull Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_EXPORTED);
        // Other filters
        IntentFilter sdFilter = new IntentFilter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sdFilter.addAction(Intent.ACTION_PACKAGES_SUSPENDED);
            sdFilter.addAction(Intent.ACTION_PACKAGES_UNSUSPENDED);
        }
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        sdFilter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
        sdFilter.addAction(ACTION_PACKAGE_ALTERED);
        sdFilter.addAction(ACTION_PACKAGE_ADDED);
        sdFilter.addAction(ACTION_PACKAGE_REMOVED);
        sdFilter.addAction(ACTION_DB_PACKAGE_ALTERED);
        sdFilter.addAction(ACTION_DB_PACKAGE_ADDED);
        sdFilter.addAction(ACTION_DB_PACKAGE_REMOVED);
        sdFilter.addAction(ACTION_BATCH_OPS_COMPLETED);
        ContextCompat.registerReceiver(context, this, sdFilter, ContextCompat.RECEIVER_EXPORTED);
    }

    @WorkerThread
    protected abstract void onPackageChanged(Intent intent, @Nullable Integer uid, @Nullable String[] packages);

    @Override
    @UiThread
    public final void onReceive(Context context, @NonNull Intent intent) {
        HandlerThread thread = new HandlerThread("PackageChangeReceiver", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        ReceiverHandler receiverHandler = new ReceiverHandler(thread.getLooper());
        Message msg = receiverHandler.obtainMessage();
        Bundle args = new Bundle();
        args.putParcelable("intent", intent);
        msg.setData(args);
        receiverHandler.sendMessage(msg);
        thread.quitSafely();
    }

    // Handler that receives messages from the thread
    private final class ReceiverHandler extends Handler {
        public ReceiverHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            Intent intent = Objects.requireNonNull(BundleCompat.getParcelable(msg.getData(), "intent", Intent.class));
            switch (Objects.requireNonNull(intent.getAction())) {
                case Intent.ACTION_PACKAGE_REMOVED:
                    if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        // The package is being updated, not removed
                        return;
                    }
                case Intent.ACTION_PACKAGE_ADDED:
                case Intent.ACTION_PACKAGE_CHANGED:
                case Intent.ACTION_PACKAGE_RESTARTED: {
                    int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                    if (uid != -1) {
                        onPackageChanged(intent, uid, null);
                    }
                    return;
                }
                case ACTION_PACKAGE_ADDED:
                case ACTION_PACKAGE_ALTERED:
                case ACTION_PACKAGE_REMOVED:
                case ACTION_DB_PACKAGE_ADDED:
                case ACTION_DB_PACKAGE_ALTERED:
                case ACTION_DB_PACKAGE_REMOVED:
                case Intent.ACTION_PACKAGES_SUSPENDED:
                case Intent.ACTION_PACKAGES_UNSUSPENDED:
                case Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE:
                case Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE: {
                    String[] packages = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                    onPackageChanged(intent, null, packages);
                    return;
                }
                case ACTION_BATCH_OPS_COMPLETED: {
                    // Trigger for all ops except disable, force-stop and uninstall
                    @BatchOpsManager.OpType int op;
                    op = intent.getIntExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_NONE);
                    if (op != BatchOpsManager.OP_NONE && op != BatchOpsManager.OP_ADVANCED_FREEZE
                            && op != BatchOpsManager.OP_FREEZE && op != BatchOpsManager.OP_UNFREEZE
                            && op != BatchOpsManager.OP_UNINSTALL) {
                        String[] packages = intent.getStringArrayExtra(BatchOpsService.EXTRA_OP_PKG);
                        ArrayList<String> failedPackages = intent.getStringArrayListExtra(BatchOpsService.EXTRA_FAILED_PKG);
                        if (packages != null && failedPackages != null) {
                            List<String> packageList = new ArrayList<>();
                            for (String packageName : packages) {
                                if (!failedPackages.contains(packageName)) {
                                    packageList.add(packageName);
                                }
                            }
                            if (!packageList.isEmpty()) {
                                onPackageChanged(intent, null, packageList.toArray(new String[0]));
                            }
                        }
                    }
                }
            }
        }
    }
}