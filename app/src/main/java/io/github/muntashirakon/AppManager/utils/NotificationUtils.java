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
