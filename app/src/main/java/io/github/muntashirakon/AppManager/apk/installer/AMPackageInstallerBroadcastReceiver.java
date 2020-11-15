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

package io.github.muntashirakon.AppManager.apk.installer;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

class AMPackageInstallerBroadcastReceiver extends BroadcastReceiver {
    public static final String TAG = "AM_PI_BR";
    public static final String ACTION_PI_RECEIVER = BuildConfig.APPLICATION_ID + ".action.PI_RECEIVER";

    private String packageName;
    private String appLabel;
    private final Context mContext;

    public AMPackageInstallerBroadcastReceiver() {
        mContext = AppManager.getContext();
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void setAppLabel(String appLabel) {
        this.appLabel = appLabel;
    }

    @Override
    public void onReceive(Context context, @NonNull Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0);
        int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, 0);
        Log.d(TAG, "Session ID: " + sessionId);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                Log.d(TAG, "Requesting user confirmation...");
                // Send broadcast first
                Intent broadcastIntent2 = new Intent(AMPackageInstaller.ACTION_INSTALL_INTERACTION_BEGIN);
                broadcastIntent2.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName);
                broadcastIntent2.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                mContext.sendBroadcast(broadcastIntent2);
                // Open confirmIntent using the PackageInstallerActivity.
                // If the confirmIntent isn't open via an activity, it will fail for large apk files
                Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                Intent intent2 = new Intent(mContext, PackageInstallerActivity.class);
                intent2.setAction(PackageInstallerActivity.ACTION_PACKAGE_INSTALLED);
                intent2.putExtra(Intent.EXTRA_INTENT, confirmIntent);
                intent2.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName);
                intent2.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // Delete intent: aborts the operation
                Intent broadcastCancel = new Intent(AMPackageInstaller.ACTION_INSTALL_COMPLETED);
                broadcastCancel.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName);
                broadcastCancel.putExtra(PackageInstaller.EXTRA_STATUS, AMPackageInstaller.STATUS_FAILURE_ABORTED);
                broadcastCancel.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                // Ask user for permission
                NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(context)
                        .setAutoCancel(true)
                        .setDefaults(Notification.DEFAULT_ALL)
                        .setWhen(System.currentTimeMillis())
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setTicker(appLabel)
                        .setContentTitle(appLabel)
                        .setSubText(context.getString(R.string.package_installer))
                        .setContentText(context.getString(R.string.confirm_installation))
                        .setContentIntent(PendingIntent.getActivity(context, 0, intent2, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT))
                        .setDeleteIntent(PendingIntent.getBroadcast(context, 0, broadcastCancel, PendingIntent.FLAG_UPDATE_CURRENT));
                NotificationUtils.displayHighPriorityNotification(context, builder.build());
                break;
            case PackageInstaller.STATUS_SUCCESS:
                Log.d(TAG, "Install success!");
                AMPackageInstaller.sendCompletedBroadcast(packageName, AMPackageInstaller.STATUS_SUCCESS, sessionId);
                break;
            default:
                Intent broadcastError = new Intent(AMPackageInstaller.ACTION_INSTALL_COMPLETED);
                broadcastError.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName);
                broadcastError.putExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME, intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME));
                broadcastError.putExtra(PackageInstaller.EXTRA_STATUS, status);
                broadcastError.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                mContext.sendBroadcast(broadcastError);
                Log.e(TAG, "Install failed! " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
                break;
        }
    }
}
