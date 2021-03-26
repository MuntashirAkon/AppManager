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

package io.github.muntashirakon.AppManager.profiles;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;

import java.io.FileNotFoundException;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

public class ProfileApplierService extends ForegroundService {
    public static final String EXTRA_PROFILE_NAME = "prof";
    public static final String EXTRA_PROFILE_STATE = "state";
    /**
     * Notification channel ID
     */
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.PROFILE_APPLIER";
    public static final int NOTIFICATION_ID = 1;

    @Nullable
    private String profileName;
    private NotificationManagerCompat notificationManager;

    public ProfileApplierService() {
        super("ProfileApplierService");
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null) {
            profileName = intent.getStringExtra(EXTRA_PROFILE_NAME);
            Intent notificationIntent = new Intent(this, AppsProfileActivity.class);
            intent.putExtra(AppsProfileActivity.EXTRA_PROFILE_NAME, profileName);
            PendingIntent pendingIntent = PendingIntent.getActivity(this,
                    0, notificationIntent, 0);
            notificationManager = NotificationUtils.getNewNotificationManager(this, CHANNEL_ID,
                    "Profile Applier", NotificationManagerCompat.IMPORTANCE_LOW);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(profileName)
                    .setContentText(getString(R.string.operation_running))
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setSubText(getText(R.string.profiles))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setProgress(0, 0, true)
                    .setContentIntent(pendingIntent);
            startForeground(NOTIFICATION_ID, builder.build());
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null || profileName == null) return;
        String state = intent.getStringExtra(EXTRA_PROFILE_STATE);
        try {
            ProfileMetaManager metaManager = new ProfileMetaManager(profileName);
            ProfileManager profileManager = new ProfileManager(metaManager);
            profileManager.applyProfile(state);
            sendNotification(Activity.RESULT_OK);
        } catch (FileNotFoundException e) {
            Log.e("ProfileApplier", "Could not apply the profile");
            sendNotification(Activity.RESULT_CANCELED);
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

    private void sendNotification(int result) {
        NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(this);
        builder.setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setTicker(profileName)
                .setContentTitle(profileName)
                .setSubText(getText(R.string.profiles));
        switch (result) {
            case Activity.RESULT_CANCELED:  // Failure
                builder.setContentText(getString(R.string.error));
                break;
            case Activity.RESULT_OK:  // Successful
                builder.setContentText(getString(R.string.the_operation_was_successful));
        }
        NotificationUtils.displayHighPriorityNotification(this, builder.build());
    }
}
