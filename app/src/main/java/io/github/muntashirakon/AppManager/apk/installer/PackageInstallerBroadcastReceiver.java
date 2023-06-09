// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.PendingIntentCompat;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

class PackageInstallerBroadcastReceiver extends BroadcastReceiver {
    public static final String TAG = "PIReceiver";
    public static final String ACTION_PI_RECEIVER = BuildConfig.APPLICATION_ID + ".action.PI_RECEIVER";

    private String packageName;
    private CharSequence appLabel;
    private int confirmNotificationId = 0;
    private final Context mContext;

    public PackageInstallerBroadcastReceiver() {
        mContext = AppManager.getContext();
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void setAppLabel(CharSequence appLabel) {
        this.appLabel = appLabel;
    }

    @SuppressLint("WrongConstant")
    @Override
    public void onReceive(Context context, @NonNull Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
        Log.d(TAG, "Session ID: " + sessionId);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                Log.d(TAG, "Requesting user confirmation...");
                // Send broadcast first
                Intent broadcastIntent2 = new Intent(PackageInstallerCompat.ACTION_INSTALL_INTERACTION_BEGIN);
                broadcastIntent2.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName);
                broadcastIntent2.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                mContext.sendBroadcast(broadcastIntent2);
                // Open confirmIntent using the PackageInstallerActivity.
                // If the confirmIntent isn't open via an activity, it will fail for large apk files
                Intent confirmIntent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent.class);
                Intent intent2 = new Intent(mContext, PackageInstallerActivity.class);
                intent2.setAction(PackageInstallerActivity.ACTION_PACKAGE_INSTALLED);
                intent2.putExtra(Intent.EXTRA_INTENT, confirmIntent);
                intent2.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName);
                intent2.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                boolean appInForeground = Utils.isAppInForeground();
                if (appInForeground) {
                    // Open activity directly and issue a silent notification
                    context.startActivity(intent2);
                }
                // Delete intent: aborts the operation
                Intent broadcastCancel = new Intent(PackageInstallerCompat.ACTION_INSTALL_COMPLETED);
                broadcastCancel.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName);
                broadcastCancel.putExtra(PackageInstaller.EXTRA_STATUS, PackageInstallerCompat.STATUS_FAILURE_ABORTED);
                broadcastCancel.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                // Ask user for permission
                confirmNotificationId = NotificationUtils.displayInstallConfirmNotification(context, builder -> builder
                        .setAutoCancel(false)
                        .setSilent(appInForeground)
                        .setDefaults(Notification.DEFAULT_ALL)
                        .setWhen(System.currentTimeMillis())
                        .setSmallIcon(R.drawable.ic_default_notification)
                        .setTicker(appLabel)
                        .setContentTitle(appLabel)
                        .setSubText(context.getString(R.string.package_installer))
                        // A neat way to find the title is to check for sessionId
                        .setContentText(context.getString(sessionId == -1 ? R.string.confirm_uninstallation : R.string.confirm_installation))
                        .setContentIntent(PendingIntent.getActivity(context, 0, intent2,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntentCompat.FLAG_IMMUTABLE))
                        .setDeleteIntent(PendingIntent.getBroadcast(context, 0, broadcastCancel,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntentCompat.FLAG_IMMUTABLE))
                        .build());
                break;
            case PackageInstaller.STATUS_SUCCESS:
                Log.d(TAG, "Install success!");
                NotificationUtils.cancelInstallConfirmNotification(context, confirmNotificationId);
                PackageInstallerCompat.sendCompletedBroadcast(packageName, PackageInstallerCompat.STATUS_SUCCESS, sessionId);
                break;
            default:
                NotificationUtils.cancelInstallConfirmNotification(context, confirmNotificationId);
                Intent broadcastError = new Intent(PackageInstallerCompat.ACTION_INSTALL_COMPLETED);
                broadcastError.putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
                broadcastError.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName);
                broadcastError.putExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME, intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME));
                broadcastError.putExtra(PackageInstaller.EXTRA_STATUS, status);
                broadcastError.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                mContext.sendBroadcast(broadcastError);
                Log.d(TAG, "Install failed! " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
                break;
        }
    }
}
