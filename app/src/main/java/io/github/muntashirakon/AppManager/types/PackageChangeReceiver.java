// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import android.os.*;
import android.os.Process;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;

import static io.github.muntashirakon.AppManager.batchops.BatchOpsService.ACTION_BATCH_OPS_COMPLETED;

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

    public PackageChangeReceiver(@NonNull Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        context.registerReceiver(this, filter);
        // Other filters
        IntentFilter sdFilter = new IntentFilter();
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        sdFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
        sdFilter.addAction(ACTION_PACKAGE_ALTERED);
        sdFilter.addAction(ACTION_PACKAGE_ADDED);
        sdFilter.addAction(ACTION_PACKAGE_REMOVED);
        sdFilter.addAction(ACTION_BATCH_OPS_COMPLETED);
        context.registerReceiver(this, sdFilter);
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
            Intent intent = msg.getData().getParcelable("intent");
            switch (Objects.requireNonNull(intent.getAction())) {
                case Intent.ACTION_PACKAGE_REMOVED:
                    if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
                case Intent.ACTION_PACKAGE_ADDED:
                case Intent.ACTION_PACKAGE_CHANGED: {
                    int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                    if (uid != -1) onPackageChanged(intent, uid, null);
                    return;
                }
                case ACTION_PACKAGE_ADDED:
                case ACTION_PACKAGE_ALTERED:
                case ACTION_PACKAGE_REMOVED:
                case Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE:
                case Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE: {
                    String[] packages = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                    onPackageChanged(intent, null, packages);
                    return;
                }
                case Intent.ACTION_LOCALE_CHANGED:
                    onPackageChanged(intent, null, null);
                    return;
                case ACTION_BATCH_OPS_COMPLETED: {
                    // Trigger for all ops except disable, force-stop and uninstall
                    @BatchOpsManager.OpType int op;
                    op = intent.getIntExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_NONE);
                    if (op != BatchOpsManager.OP_NONE && op != BatchOpsManager.OP_DISABLE &&
                            op != BatchOpsManager.OP_ENABLE && op != BatchOpsManager.OP_UNINSTALL) {
                        String[] packages = intent.getStringArrayExtra(BatchOpsService.EXTRA_OP_PKG);
                        String[] failedPackages = intent.getStringArrayExtra(BatchOpsService.EXTRA_FAILED_PKG);
                        if (packages != null && failedPackages != null) {
                            List<String> packageList = new ArrayList<>();
                            List<String> failedPackageList = Arrays.asList(failedPackages);
                            for (String packageName : packages) {
                                if (!failedPackageList.contains(packageName)) packageList.add(packageName);
                            }
                            if (packageList.size() > 0) {
                                onPackageChanged(intent, null, packageList.toArray(new String[0]));
                            }
                        }
                    }
                }
            }
        }
    }
}