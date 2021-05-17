// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.app.Notification;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import io.github.muntashirakon.AppManager.BuildConfig;

public final class NotificationUtils {
    private static final String HIGH_PRIORITY_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.HIGH_PRIORITY";

    private static final int HIGH_PRIORITY_NOTIFICATION_ID = 2;

    @NonNull
    private static NotificationManagerCompat getHighPriorityNotificationManager(@NonNull Context context) {
        return getNewNotificationManager(context, HIGH_PRIORITY_CHANNEL_ID,
                "HighPriorityChannel", NotificationManagerCompat.IMPORTANCE_HIGH);
    }

    @NonNull
    public static NotificationCompat.Builder getHighPriorityNotificationBuilder(@NonNull Context context) {
        return new NotificationCompat.Builder(context, NotificationUtils.HIGH_PRIORITY_CHANNEL_ID)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
    }

    public static void displayHighPriorityNotification(@NonNull Context context, Notification notification) {
        getHighPriorityNotificationManager(context).notify(HIGH_PRIORITY_NOTIFICATION_ID, notification);
    }

    @NonNull
    public static NotificationManagerCompat getNewNotificationManager(@NonNull Context context, @NonNull String channelId, String channelName, int importance) {
        NotificationChannelCompat channel = new NotificationChannelCompat.Builder(channelId, importance).setName(channelName).build();
        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(context);
        managerCompat.createNotificationChannel(channel);
        return managerCompat;
    }
}
