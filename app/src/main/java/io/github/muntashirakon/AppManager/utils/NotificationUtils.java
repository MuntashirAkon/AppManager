// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.app.Notification;
import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.BuildConfig;

public final class NotificationUtils {
    private static final String HIGH_PRIORITY_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.HIGH_PRIORITY";
    private static final String INSTALL_CONFIRM_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.INSTALL_CONFIRM";
    private static final String FREEZE_UNFREEZE_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.FREEZE_UNFREEZE";

    @IntDef({
            NotificationManagerCompat.IMPORTANCE_UNSPECIFIED,
            NotificationManagerCompat.IMPORTANCE_NONE,
            NotificationManagerCompat.IMPORTANCE_MIN,
            NotificationManagerCompat.IMPORTANCE_LOW,
            NotificationManagerCompat.IMPORTANCE_DEFAULT,
            NotificationManagerCompat.IMPORTANCE_HIGH,
            NotificationManagerCompat.IMPORTANCE_MAX,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface NotificationImportance {
    }

    @IntDef({
            NotificationCompat.PRIORITY_MIN,
            NotificationCompat.PRIORITY_LOW,
            NotificationCompat.PRIORITY_DEFAULT,
            NotificationCompat.PRIORITY_HIGH,
            NotificationCompat.PRIORITY_MAX,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface NotificationPriority {
    }

    public interface NotificationBuilder {
        Notification build(NotificationCompat.Builder builder);
    }

    private static final Map<String, Integer> notificationIds = new HashMap<>();

    private static int getNotificationId(String channelId) {
        Integer id = notificationIds.get(channelId);
        if (id == null) {
            notificationIds.put(channelId, 1);
            return 1;
        }
        notificationIds.put(channelId, id + 1);
        return id;
    }

    @NonNull
    public static NotificationCompat.Builder getHighPriorityNotificationBuilder(@NonNull Context context) {
        return new NotificationCompat.Builder(context, NotificationUtils.HIGH_PRIORITY_CHANNEL_ID)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
    }

    @NonNull
    public static NotificationCompat.Builder getFreezeUnfreezeNotificationBuilder(@NonNull Context context) {
        return new NotificationCompat.Builder(context, NotificationUtils.FREEZE_UNFREEZE_CHANNEL_ID)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
    }

    public static void displayHighPriorityNotification(@NonNull Context context, Notification notification) {
        displayHighPriorityNotification(context, builder -> notification);
    }

    public static void displayHighPriorityNotification(@NonNull Context context,
                                                       @NonNull NotificationBuilder notification) {
        int notificationId = getNotificationId(HIGH_PRIORITY_CHANNEL_ID);
        displayNotification(context, HIGH_PRIORITY_CHANNEL_ID, "Alerts",
                NotificationManagerCompat.IMPORTANCE_HIGH, notificationId,
                NotificationCompat.PRIORITY_HIGH, notification);
    }

    public static int displayFreezeUnfreezeNotification(@NonNull Context context,
                                                        int notificationId,
                                                        @NonNull NotificationBuilder notification) {
        displayNotification(context, FREEZE_UNFREEZE_CHANNEL_ID, "Freeze",
                NotificationManagerCompat.IMPORTANCE_DEFAULT, notificationId,
                NotificationCompat.PRIORITY_DEFAULT, notification);
        return notificationId;
    }

    public static void displayInstallConfirmNotification(@NonNull Context context, @NonNull NotificationBuilder notification) {
        int notificationId = getNotificationId(INSTALL_CONFIRM_CHANNEL_ID);
        displayNotification(context, INSTALL_CONFIRM_CHANNEL_ID, "Confirm Installation",
                NotificationManagerCompat.IMPORTANCE_HIGH, notificationId,
                NotificationCompat.PRIORITY_HIGH, notification);
    }

    public static void displayNotification(@NonNull Context context,
                                           @NonNull String channelId,
                                           @NonNull CharSequence channelName,
                                           @NotificationImportance int importance,
                                           int notificationId,
                                           @NotificationPriority int priority,
                                           @NonNull NotificationBuilder notification) {
        NotificationManagerCompat manager = getNewNotificationManager(context, channelId, channelName, importance);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId).setPriority(priority);
        manager.notify(notificationId, notification.build(builder));
    }

    @NonNull
    public static NotificationManagerCompat getFreezeUnfreezeNotificationManager(@NonNull Context context) {
        return getNewNotificationManager(context, FREEZE_UNFREEZE_CHANNEL_ID, "Freeze",
                NotificationManagerCompat.IMPORTANCE_DEFAULT);
    }

    @NonNull
    public static NotificationManagerCompat getNewNotificationManager(@NonNull Context context,
                                                                      @NonNull String channelId,
                                                                      CharSequence channelName,
                                                                      int importance) {
        NotificationChannelCompat channel = new NotificationChannelCompat.Builder(channelId, importance)
                .setName(channelName)
                .build();
        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(context);
        managerCompat.createNotificationChannel(channel);
        return managerCompat;
    }
}
