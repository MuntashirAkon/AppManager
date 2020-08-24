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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;

public final class NotificationUtils {
    private static final String HIGH_PRIORITY_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.HIGH_PRIORITY";

    private static final int HIGH_PRIORITY_NOTIFICATION_ID = 2;

    private static NotificationManager highPriorityNotificationManager;

    @NonNull
    private static NotificationManager getHighPriorityNotificationManager() {
        if (highPriorityNotificationManager == null) {
            highPriorityNotificationManager = (NotificationManager) AppManager.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel notificationChannel = new NotificationChannel(
                        HIGH_PRIORITY_CHANNEL_ID, "HighPriorityChannel",
                        NotificationManager.IMPORTANCE_HIGH);
                highPriorityNotificationManager.createNotificationChannel(notificationChannel);
            }
        }
        return highPriorityNotificationManager;
    }

    @NonNull
    public static NotificationCompat.Builder getHighPriorityNotificationBuilder(@NonNull Context context) {
        return new NotificationCompat.Builder(context, NotificationUtils.HIGH_PRIORITY_CHANNEL_ID)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
    }

    public static void displayHighPriorityNotification(Notification notification) {
        getHighPriorityNotificationManager().notify(HIGH_PRIORITY_NOTIFICATION_ID, notification);
    }
}
