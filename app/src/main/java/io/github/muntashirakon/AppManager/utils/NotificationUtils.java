// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.INotificationManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Prefs;

public final class NotificationUtils {
    private static final String HIGH_PRIORITY_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.HIGH_PRIORITY";
    private static final String INSTALL_CONFIRM_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.INSTALL_CONFIRM";
    private static final String FREEZE_UNFREEZE_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.FREEZE_UNFREEZE";

    public static final NotificationProgressHandler.NotificationManagerInfo HIGH_PRIORITY_NOTIFICATION_INFO =
            new NotificationProgressHandler.NotificationManagerInfo(
                    HIGH_PRIORITY_CHANNEL_ID, "Alerts", NotificationManagerCompat.IMPORTANCE_HIGH);

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

    private static final Set<Integer> sNotificationIds = Collections.synchronizedSet(new HashSet<>(8));
    private static final Set<Integer> sReservedNotificationIds = new HashSet<>(8);

    public static int acquireNotificationId() {
        int newNotificationId = nextNotificationId();
        synchronized (sReservedNotificationIds) {
            sReservedNotificationIds.add(newNotificationId);
        }
        return newNotificationId;
    }

    public static void releaseNotificationId(int notificationId) {
        synchronized (sReservedNotificationIds) {
            sReservedNotificationIds.remove(notificationId);
        }
    }

    public static int nextNotificationId() {
        return nextNotificationId(1);
    }

    public static int nextNotificationId(int start) {
        synchronized (sNotificationIds) {
            sNotificationIds.clear();
            for (StatusBarNotification notification : getActiveNotifications()) {
                sNotificationIds.add(notification.getId());
            }
            int i = start;
            synchronized (sReservedNotificationIds) {
                while (sNotificationIds.contains(i) || sReservedNotificationIds.contains(i)) ++i;
            }
            return i;
        }
    }

    @NonNull
    public static NotificationCompat.Builder getHighPriorityNotificationBuilder(@NonNull Context context) {
        return new NotificationCompat.Builder(context, NotificationUtils.HIGH_PRIORITY_CHANNEL_ID)
                .setLocalOnly(!Prefs.Misc.sendNotificationsToConnectedDevices())
                .setPriority(NotificationCompat.PRIORITY_HIGH);
    }

    public static void displayHighPriorityNotification(@NonNull Context context, Notification notification) {
        displayHighPriorityNotification(context, builder -> notification);
    }

    public static void displayHighPriorityNotification(@NonNull Context context,
                                                       @NonNull NotificationBuilder notification) {
        int notificationId = nextNotificationId();
        displayNotification(context, HIGH_PRIORITY_CHANNEL_ID, "Alerts",
                NotificationManagerCompat.IMPORTANCE_HIGH, notificationId, notification);
    }

    public static void displayFreezeUnfreezeNotification(@NonNull Context context,
                                                         int notificationId,
                                                         @NonNull NotificationBuilder notification) {
        displayNotification(context, FREEZE_UNFREEZE_CHANNEL_ID, "Freeze",
                NotificationManagerCompat.IMPORTANCE_DEFAULT, notificationId, notification);
    }

    public static int displayInstallConfirmNotification(@NonNull Context context,
                                                        @NonNull NotificationBuilder notification) {
        int notificationId = nextNotificationId();
        displayNotification(context, INSTALL_CONFIRM_CHANNEL_ID, "Confirm Installation",
                NotificationManagerCompat.IMPORTANCE_HIGH, notificationId, notification);
        return notificationId;
    }

    public static void cancelInstallConfirmNotification(@NonNull Context context, int notificationId) {
        if (notificationId <= 0) {
            return;
        }
        NotificationManagerCompat manager = getNewNotificationManager(context, INSTALL_CONFIRM_CHANNEL_ID,
                "Confirm Installation", NotificationManagerCompat.IMPORTANCE_HIGH);
        manager.cancel(notificationId);
    }

    private static void displayNotification(@NonNull Context context,
                                            @NonNull String channelId,
                                            @NonNull CharSequence channelName,
                                            @NotificationImportance int importance,
                                            int notificationId,
                                            @NonNull NotificationBuilder notification) {
        NotificationManagerCompat manager = getNewNotificationManager(context, channelId, channelName, importance);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setLocalOnly(!Prefs.Misc.sendNotificationsToConnectedDevices())
                .setPriority(importanceToPriority(importance));
        if (SelfPermissions.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            manager.notify(notificationId, notification.build(builder));
        }
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

    @NotificationPriority
    public static int importanceToPriority(@NotificationImportance int importance) {
        switch (importance) {
            default:
            case NotificationManagerCompat.IMPORTANCE_DEFAULT:
            case NotificationManagerCompat.IMPORTANCE_UNSPECIFIED:
                return NotificationCompat.PRIORITY_DEFAULT;
            case NotificationManagerCompat.IMPORTANCE_HIGH:
                return NotificationCompat.PRIORITY_HIGH;
            case NotificationManagerCompat.IMPORTANCE_LOW:
                return NotificationCompat.PRIORITY_LOW;
            case NotificationManagerCompat.IMPORTANCE_MAX:
                return NotificationCompat.PRIORITY_MAX;
            case NotificationManagerCompat.IMPORTANCE_NONE:
            case NotificationManagerCompat.IMPORTANCE_MIN:
                return NotificationCompat.PRIORITY_MIN;
        }
    }

    public static Intent getNotificationSettingIntent(@Nullable String channelId) {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (channelId != null) {
                intent.setAction(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId);
            } else {
                intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            }
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
        } else {
            intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
            intent.putExtra("app_package", BuildConfig.APPLICATION_ID);
            intent.putExtra("app_uid", Process.myUid());
        }
        return intent;
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    @SuppressLint("DiscouragedPrivateApi")
    private static StatusBarNotification[] getActiveNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NotificationManager notificationManager = (NotificationManager) ContextUtils.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            return ArrayUtils.defeatNullable(StatusBarNotification.class, notificationManager.getActiveNotifications());
        }
        try {
            // Fetch using private API

            Method getService = NotificationManager.class.getDeclaredMethod("getService");
            INotificationManager notificationManager = (INotificationManager) Objects.requireNonNull(getService.invoke(null));
            return ArrayUtils.defeatNullable(StatusBarNotification.class, notificationManager.getActiveNotifications(BuildConfig.APPLICATION_ID));
        } catch (Throwable th) {
            // Should never happen
            th.printStackTrace();
            return ArrayUtils.emptyArray(StatusBarNotification.class);
        }
    }
}
