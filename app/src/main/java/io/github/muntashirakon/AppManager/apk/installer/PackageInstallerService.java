// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_ABORTED;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_BLOCKED;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_CONFLICT;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_INCOMPATIBLE;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_INCOMPATIBLE_ROM;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_INVALID;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SECURITY;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SESSION_ABANDON;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SESSION_COMMIT;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SESSION_CREATE;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SESSION_WRITE;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_STORAGE;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_SUCCESS;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.app.ServiceCompat;

import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.apk.CachedApkSource;
import io.github.muntashirakon.AppManager.apk.behavior.DexOptimizer;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.main.MainActivity;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler.NotificationInfo;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.progress.QueuedProgressHandler;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class PackageInstallerService extends ForegroundService {
    public static final String EXTRA_QUEUE_ITEM = "queue_item";
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.INSTALL";

    public interface OnInstallFinished {
        @UiThread
        void onFinished(String packageName, int status, @Nullable String blockingPackage,
                        @Nullable String statusMessage);
    }

    public PackageInstallerService() {
        super("PackageInstallerService");
    }

    @Nullable
    private OnInstallFinished mOnInstallFinished;
    private int mSessionId;
    private String mPackageName;
    private QueuedProgressHandler mProgressHandler;
    private NotificationInfo mNotificationInfo;
    private PowerManager.WakeLock mWakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        mWakeLock = CpuUtils.getPartialWakeLock("installer");
        mWakeLock.acquire();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (isWorking()) {
            return super.onStartCommand(intent, flags, startId);
        }
        mProgressHandler = new NotificationProgressHandler(
                this,
                new NotificationProgressHandler.NotificationManagerInfo(CHANNEL_ID, "Install Progress", NotificationManagerCompat.IMPORTANCE_LOW),
                NotificationUtils.HIGH_PRIORITY_NOTIFICATION_INFO,
                NotificationUtils.HIGH_PRIORITY_NOTIFICATION_INFO
        );
        mProgressHandler.setProgressTextInterface(ProgressHandler.PROGRESS_PERCENT);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntentCompat.getActivity(this, 0, notificationIntent, 0, false);
        mNotificationInfo = new NotificationInfo()
                .setBody(getString(R.string.install_in_progress))
                .setOperationName(getText(R.string.package_installer))
                .setDefaultAction(pendingIntent);
        mProgressHandler.onAttach(this, mNotificationInfo);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;
        ApkQueueItem apkQueueItem = IntentCompat.getParcelableExtra(intent, EXTRA_QUEUE_ITEM, ApkQueueItem.class);
        if (apkQueueItem == null) {
            return;
        }
        InstallerOptions options = apkQueueItem.getInstallerOptions() != null
                ? apkQueueItem.getInstallerOptions()
                : new InstallerOptions();
        List<String> selectedSplitIds = Objects.requireNonNull(apkQueueItem.getSelectedSplits());
        // Install package
        PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
        installer.setAppLabel(apkQueueItem.getAppLabel());
        installer.setOnInstallListener(new PackageInstallerCompat.OnInstallListener() {
            @Override
            public void onStartInstall(int sessionId, String packageName) {
                mSessionId = sessionId;
                mPackageName = packageName;
            }

            // MIUI-begin: MIUI 12.5+ workaround
            @Override
            public void onAnotherAttemptInMiui(@Nullable ApkFile apkFile) {
                if (apkFile != null) {
                    installer.install(apkFile, selectedSplitIds, options, mProgressHandler);
                }
            }
            // MIUI-end

            @Override
            public void onFinishedInstall(int sessionId, String packageName, int result,
                                          @Nullable String blockingPackage, @Nullable String statusMessage) {
                if (result == STATUS_SUCCESS) {
                    // Block trackers if requested
                    if (options.isBlockTrackers()) {
                        ComponentUtils.blockTrackingComponents(new UserPackagePair(packageName, options.getUserId()));
                    }
                    // Perform force dex optimization if requested
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && options.isForceDexOpt()) {
                        // Ignore the result because it's irrelevant
                        new DexOptimizer(PackageManagerCompat.getPackageManager(), packageName).forceDexOpt();
                    }
                }
                if (mOnInstallFinished != null) {
                    ThreadUtils.postOnMainThread(() -> {
                        if (mOnInstallFinished != null) {
                            mOnInstallFinished.onFinished(packageName, result, blockingPackage, statusMessage);
                        }
                    });
                } else sendNotification(result, apkQueueItem.getAppLabel(), blockingPackage, statusMessage);
            }
        });
        // Two possibilities: 1. Install-existing, 2. ApkFile/Uri
        if (apkQueueItem.isInstallExisting()) {
            // Install existing (need no progress)
            String packageName = apkQueueItem.getPackageName();
            if (packageName == null) {
                // No package name supplied, abort
                return;
            }
            installer.installExisting(packageName, options.getUserId());
        } else {
            // ApkFile/Uri
            ApkFile apkFile;
            ApkSource apkSource = apkQueueItem.getApkSource();
            if (apkSource != null) {
                // ApkFile set
                try {
                    apkFile = apkSource.resolve();
                } catch (Throwable th) {
                    // Could not get ApkFile for some reason, abort
                    th.printStackTrace();
                    return;
                }
            } else {
                // No apk file, abort
                return;
            }
            installer.install(apkFile, selectedSplitIds, options, mProgressHandler);
            // Delete the cached file
            if (apkSource instanceof CachedApkSource) {
                ((CachedApkSource) apkSource).cleanup();
            }
        }
    }

    @Override
    protected void onQueued(@Nullable Intent intent) {
        if (intent == null) return;
        ApkQueueItem apkQueueItem = IntentCompat.getParcelableExtra(intent, EXTRA_QUEUE_ITEM, ApkQueueItem.class);
        String appLabel = apkQueueItem != null ? apkQueueItem.getAppLabel() : null;
        Object notificationInfo = new NotificationInfo()
                .setAutoCancel(true)
                .setOperationName(getString(R.string.package_installer))
                .setTitle(appLabel)
                .setBody(getString(R.string.added_to_queue))
                .setTime(System.currentTimeMillis());
        mProgressHandler.onQueue(notificationInfo);
    }

    @Override
    protected void onStartIntent(@Nullable Intent intent) {
        if (intent == null) return;
        // Set app name in the ongoing notification
        ApkQueueItem apkQueueItem = IntentCompat.getParcelableExtra(intent, EXTRA_QUEUE_ITEM, ApkQueueItem.class);
        String appName;
        if (apkQueueItem != null) {
            String appLabel = apkQueueItem.getAppLabel();
            appName = appLabel != null ? appLabel : apkQueueItem.getPackageName();
        } else appName = null;
        CharSequence title;
        if (appName != null) {
            title = getString(R.string.installing_package, appName);
        } else {
            title = getString(R.string.install_in_progress);
        }
        mNotificationInfo.setTitle(title);
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

    public void setOnInstallFinished(@Nullable OnInstallFinished onInstallFinished) {
        this.mOnInstallFinished = onInstallFinished;
    }

    public int getCurrentSessionId() {
        return mSessionId;
    }

    public String getCurrentPackageName() {
        return mPackageName;
    }

    private void sendNotification(@PackageInstallerCompat.Status int status,
                                  @Nullable String appLabel,
                                  @Nullable String blockingPackage,
                                  @Nullable String statusMessage) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(mPackageName);
        PendingIntent defaultAction = intent != null ? PendingIntentCompat.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT, false) : null;
        String subject = getStringFromStatus(this, status, appLabel, blockingPackage);
        NotificationCompat.Style content = statusMessage != null ? new NotificationCompat.BigTextStyle()
                .bigText(subject + "\n\n" + statusMessage) : null;
        Object notificationInfo = new NotificationInfo()
                .setAutoCancel(true)
                .setTime(System.currentTimeMillis())
                .setOperationName(getText(R.string.package_installer))
                .setTitle(appLabel)
                .setBody(subject)
                .setStyle(content)
                .setDefaultAction(defaultAction);
        NotificationInfo progressNotificationInfo = (NotificationInfo) mProgressHandler.getLastMessage();
        if (progressNotificationInfo != null) {
            progressNotificationInfo.setBody(getString(R.string.done));
        }
        mProgressHandler.setProgressTextInterface(null);
        ThreadUtils.postOnMainThread(() -> mProgressHandler.onResult(notificationInfo));
    }

    @NonNull
    public static String getStringFromStatus(@NonNull Context context,
                                             @PackageInstallerCompat.Status int status,
                                             @Nullable CharSequence appLabel,
                                             @Nullable String blockingPackage) {
        switch (status) {
            case STATUS_SUCCESS:
                return context.getString(R.string.package_name_is_installed_successfully, appLabel);
            case STATUS_FAILURE_ABORTED:
                return context.getString(R.string.installer_error_aborted);
            case STATUS_FAILURE_BLOCKED:
                String blocker = context.getString(R.string.installer_error_blocked_device);
                if (blockingPackage != null) {
                    blocker = PackageUtils.getPackageLabel(context.getPackageManager(), blockingPackage);
                }
                return context.getString(R.string.installer_error_blocked, blocker);
            case STATUS_FAILURE_CONFLICT:
                return context.getString(R.string.installer_error_conflict);
            case STATUS_FAILURE_INCOMPATIBLE:
                return context.getString(R.string.installer_error_incompatible);
            case STATUS_FAILURE_INVALID:
                return context.getString(R.string.installer_error_bad_apks);
            case STATUS_FAILURE_STORAGE:
                return context.getString(R.string.installer_error_storage);
            case STATUS_FAILURE_SECURITY:
                return context.getString(R.string.installer_error_security);
            case STATUS_FAILURE_SESSION_CREATE:
                return context.getString(R.string.installer_error_session_create);
            case STATUS_FAILURE_SESSION_WRITE:
                return context.getString(R.string.installer_error_session_write);
            case STATUS_FAILURE_SESSION_COMMIT:
                return context.getString(R.string.installer_error_session_commit);
            case STATUS_FAILURE_SESSION_ABANDON:
                return context.getString(R.string.installer_error_session_abandon);
            case STATUS_FAILURE_INCOMPATIBLE_ROM:
                return context.getString(R.string.installer_error_lidl_rom);
        }
        return context.getString(R.string.installer_error_generic);
    }
}
