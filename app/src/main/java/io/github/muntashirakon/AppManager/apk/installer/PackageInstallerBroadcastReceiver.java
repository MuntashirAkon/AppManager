// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import androidx.annotation.NonNull;
import androidx.core.app.PendingIntentCompat;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

class PackageInstallerBroadcastReceiver extends BroadcastReceiver {
    public static final String TAG = PackageInstallerBroadcastReceiver.class.getSimpleName();
    public static final String ACTION_PI_RECEIVER = BuildConfig.APPLICATION_ID + ".action.PI_RECEIVER";

    private String mPackageName;
    private CharSequence mAppLabel;
    private int mConfirmNotificationId = 0;

    public void setPackageName(String packageName) {
        mPackageName = packageName;
    }

    public void setAppLabel(CharSequence appLabel) {
        mAppLabel = appLabel;
    }

    @Override
    public void onReceive(Context nullableContext, @NonNull Intent intent) {
        Context context = nullableContext != null ? nullableContext : ContextUtils.getContext();
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
        Log.d(TAG, "Session ID: %d", sessionId);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                Log.d(TAG, "Requesting user confirmation...");
                // Send broadcast first
                Intent broadcastIntent2 = new Intent(PackageInstallerCompat.ACTION_INSTALL_INTERACTION_BEGIN);
                broadcastIntent2.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, mPackageName);
                broadcastIntent2.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                context.sendBroadcast(broadcastIntent2);
                // Open confirmIntent using the PackageInstallerActivity.
                // If the confirmIntent isn't open via an activity, it will fail for large apk files
                Intent confirmIntent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent.class);
                Intent intent2 = new Intent(context, PackageInstallerActivity.class);
                intent2.setAction(PackageInstallerActivity.ACTION_PACKAGE_INSTALLED);
                intent2.putExtra(Intent.EXTRA_INTENT, confirmIntent);
                intent2.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, mPackageName);
                intent2.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                boolean appInForeground = Utils.isAppInForeground();
                if (appInForeground) {
                    // Open activity directly and issue a silent notification
                    context.startActivity(intent2);
                }
                // Delete intent: aborts the operation
                Intent broadcastCancel = new Intent(PackageInstallerCompat.ACTION_INSTALL_COMPLETED);
                broadcastCancel.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, mPackageName);
                broadcastCancel.putExtra(PackageInstaller.EXTRA_STATUS, PackageInstallerCompat.STATUS_FAILURE_ABORTED);
                broadcastCancel.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                // Ask user for permission
                mConfirmNotificationId = NotificationUtils.displayInstallConfirmNotification(context, builder -> builder
                        .setAutoCancel(false)
                        .setSilent(appInForeground)
                        .setDefaults(Notification.DEFAULT_ALL)
                        .setWhen(System.currentTimeMillis())
                        .setSmallIcon(R.drawable.ic_default_notification)
                        .setTicker(mAppLabel)
                        .setContentTitle(mAppLabel)
                        .setSubText(context.getString(R.string.package_installer))
                        // A neat way to find the title is to check for sessionId
                        .setContentText(context.getString(sessionId == -1 ? R.string.confirm_uninstallation : R.string.confirm_installation))
                        .setContentIntent(PendingIntentCompat.getActivity(context, 0, intent2,
                                PendingIntent.FLAG_UPDATE_CURRENT, false))
                        .setDeleteIntent(PendingIntentCompat.getBroadcast(context, 0, broadcastCancel,
                                PendingIntent.FLAG_UPDATE_CURRENT, false))
                        .build());
                break;
            case PackageInstaller.STATUS_SUCCESS:
                Log.d(TAG, "Install success!");
                NotificationUtils.cancelInstallConfirmNotification(context, mConfirmNotificationId);
                PackageInstallerCompat.sendCompletedBroadcast(context, mPackageName, PackageInstallerCompat.STATUS_SUCCESS, sessionId);
                break;
            default:
                NotificationUtils.cancelInstallConfirmNotification(context, mConfirmNotificationId);
                Intent broadcastError = new Intent(PackageInstallerCompat.ACTION_INSTALL_COMPLETED);
                String statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                broadcastError.putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, statusMessage);
                broadcastError.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, mPackageName);
                broadcastError.putExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME, intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME));
                broadcastError.putExtra(PackageInstaller.EXTRA_STATUS, status);
                broadcastError.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                context.sendBroadcast(broadcastError);
                Log.d(TAG, "Install failed! %s", statusMessage);
                break;
        }
    }
}
