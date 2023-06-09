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

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;

import java.util.Collections;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.compat.PendingIntentCompat;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.main.MainActivity;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler;
import io.github.muntashirakon.AppManager.progress.QueuedProgressHandler;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
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
    private OnInstallFinished onInstallFinished;
    private int sessionId;
    private String packageName;
    private QueuedProgressHandler progressHandler;
    private NotificationProgressHandler.NotificationInfo notificationInfo;

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (isWorking()) {
            return super.onStartCommand(intent, flags, startId);
        }
        progressHandler = new NotificationProgressHandler(
                this,
                new NotificationProgressHandler.NotificationManagerInfo(CHANNEL_ID, "Install Progress", NotificationManagerCompat.IMPORTANCE_LOW),
                NotificationUtils.HIGH_PRIORITY_NOTIFICATION_INFO,
                NotificationUtils.HIGH_PRIORITY_NOTIFICATION_INFO
        );
        Intent notificationIntent = new Intent(this, MainActivity.class);
        @SuppressLint("WrongConstant")
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntentCompat.FLAG_IMMUTABLE);
        notificationInfo = new NotificationProgressHandler.NotificationInfo()
                .setBody(getString(R.string.install_in_progress))
                .setOperationName(getText(R.string.package_installer))
                .setDefaultAction(pendingIntent);
        progressHandler.onAttach(this, notificationInfo);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;
        ApkQueueItem apkQueueItem = IntentCompat.getParcelableExtra(intent, EXTRA_QUEUE_ITEM, ApkQueueItem.class);
        if (apkQueueItem == null) {
            return;
        }
        // Install package
        PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
        installer.setAppLabel(apkQueueItem.getAppLabel());
        installer.setOnInstallListener(new PackageInstallerCompat.OnInstallListener() {
            @Override
            public void onStartInstall(int sessionId, String packageName) {
                PackageInstallerService.this.sessionId = sessionId;
                PackageInstallerService.this.packageName = packageName;
            }

            // MIUI-begin: MIUI 12.5+ workaround
            @Override
            public void onAnotherAttemptInMiui(@Nullable ApkFile apkFile) {
                if (apkFile != null) {
                    installer.install(apkFile, apkQueueItem.getUserId(), progressHandler);
                }
            }
            // MIUI-end

            @Override
            public void onFinishedInstall(int sessionId, String packageName, int result,
                                          @Nullable String blockingPackage, @Nullable String statusMessage) {
                // Block trackers if requested
                if (result == STATUS_SUCCESS && Ops.isRoot() && Prefs.Installer.blockTrackers()) {
                    ComponentUtils.blockTrackingComponents(Collections.singletonList(
                            new UserPackagePair(packageName, apkQueueItem.getUserId())));
                }
                if (onInstallFinished != null) {
                    ThreadUtils.postOnMainThread(() -> {
                        if (onInstallFinished != null) {
                            onInstallFinished.onFinished(packageName, result, blockingPackage, statusMessage);
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
            installer.installExisting(packageName, apkQueueItem.getUserId());
        } else {
            // ApkFile/Uri
            ApkFile apkFile = null;
            int apkFileKey = apkQueueItem.getApkFileKey();
            if (apkFileKey != -1) {
                // ApkFile set
                try {
                    apkFile = ApkFile.getInstance(apkFileKey);
                } catch (Throwable th) {
                    // Could not get ApkFile for some reason, fallback to use Uri
                    th.printStackTrace();
                    apkFileKey = -1;
                }
            }
            if (apkFileKey == -1) {
                try {
                    apkFileKey = ApkFile.createInstance(apkQueueItem.getUri(), apkQueueItem.getMimeType());
                    apkFile = ApkFile.getInstance(apkFileKey);
                } catch (ApkFile.ApkFileException e) {
                    e.printStackTrace();
                }
            }
            if (apkFile == null) {
                // No apk file, abort
                return;
            }
            installer.install(apkFile, apkQueueItem.getUserId(), progressHandler);
        }
    }

    @Override
    protected void onQueued(@Nullable Intent intent) {
        if (intent == null) return;
        ApkQueueItem apkQueueItem = IntentCompat.getParcelableExtra(intent, EXTRA_QUEUE_ITEM, ApkQueueItem.class);
        String appLabel = apkQueueItem != null ? apkQueueItem.getAppLabel() : null;
        Object notificationInfo = new NotificationProgressHandler.NotificationInfo()
                .setAutoCancel(true)
                .setOperationName(getString(R.string.package_installer))
                .setTitle(appLabel)
                .setBody(getString(R.string.added_to_queue))
                .setTime(System.currentTimeMillis());
        progressHandler.onQueue(notificationInfo);
    }

    @Override
    protected void onStartIntent(@Nullable Intent intent) {
        if (intent == null) return;
        // Set app name in the ongoing notification
        ApkQueueItem apkQueueItem = IntentCompat.getParcelableExtra(intent, EXTRA_QUEUE_ITEM, ApkQueueItem.class);
        String appLabel = apkQueueItem != null ? apkQueueItem.getAppLabel() : null;
        notificationInfo.setTitle(appLabel);
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

    public void setOnInstallFinished(@Nullable OnInstallFinished onInstallFinished) {
        this.onInstallFinished = onInstallFinished;
    }

    public int getCurrentSessionId() {
        return sessionId;
    }

    public String getCurrentPackageName() {
        return packageName;
    }

    @SuppressLint("WrongConstant")
    private void sendNotification(@PackageInstallerCompat.Status int status,
                                  @Nullable String appLabel,
                                  @Nullable String blockingPackage,
                                  @Nullable String statusMessage) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        PendingIntent defaultAction = intent != null ? PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntentCompat.FLAG_IMMUTABLE) : null;
        String subject = getStringFromStatus(this, status, appLabel, blockingPackage);
        NotificationCompat.Style content = statusMessage != null ? new NotificationCompat.BigTextStyle()
                .bigText(subject + "\n\n" + statusMessage) : null;
        Object notificationInfo = new NotificationProgressHandler.NotificationInfo()
                .setAutoCancel(true)
                .setTime(System.currentTimeMillis())
                .setOperationName(getText(R.string.package_installer))
                .setTitle(appLabel)
                .setBody(subject)
                .setStyle(content)
                .setDefaultAction(defaultAction);
        ThreadUtils.postOnMainThread(() -> progressHandler.onResult(notificationInfo));
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
