// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.progress;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

public class NotificationProgressHandler extends QueuedProgressHandler {
    @NonNull
    private final Context mContext;
    @NonNull
    private final NotificationManagerInfo mProgressNotificationManagerInfo;
    @NonNull
    private final NotificationManagerInfo mCompletionNotificationManagerInfo;
    @Nullable
    private final NotificationManagerInfo mQueueNotificationManagerInfo;
    @NonNull
    private final NotificationManagerCompat mProgressNotificationManager;
    @NonNull
    private final NotificationManagerCompat mCompletionNotificationManager;
    @Nullable
    private final NotificationManagerCompat mQueueNotificationManager;
    private final int mProgressNotificationId;
    private final int mQueueNotificationId;

    @Nullable
    private NotificationInfo mLastProgressNotification = null;
    private int mLastMax = MAX_INDETERMINATE;
    private float mLastProgress = 0;
    private boolean mAttachedToService;

    public NotificationProgressHandler(@NonNull Context context,
                                       @NonNull NotificationManagerInfo progressNotificationManagerInfo,
                                       @NonNull NotificationManagerInfo completionNotificationManagerInfo,
                                       @Nullable NotificationManagerInfo queueNotificationManagerInfo) {
        mContext = context;
        mProgressNotificationManagerInfo = progressNotificationManagerInfo;
        mCompletionNotificationManagerInfo = completionNotificationManagerInfo;
        mQueueNotificationManagerInfo = queueNotificationManagerInfo;
        mProgressNotificationManager = getNotificationManager(context, mProgressNotificationManagerInfo);
        mCompletionNotificationManager = getNotificationManager(context, mCompletionNotificationManagerInfo);
        mQueueNotificationManager = getNotificationManager(context, mQueueNotificationManagerInfo);
        mProgressNotificationId = NotificationUtils.getNotificationId(mProgressNotificationManagerInfo.channelId);
        mQueueNotificationId = mQueueNotificationManagerInfo != null ? NotificationUtils.getNotificationId(mQueueNotificationManagerInfo.channelId) : -1;
    }

    @Override
    public void onQueue(@Nullable Object message) {
        if (mQueueNotificationManager == null || mQueueNotificationManagerInfo == null || message == null) {
            return;
        }
        NotificationInfo info = (NotificationInfo) message;
        Notification notification = info
                .getBuilder(mContext, mQueueNotificationManagerInfo)
                .build();
        notify(mContext, mQueueNotificationManager, mQueueNotificationId, notification);
    }

    @Override
    public void onAttach(@Nullable Service service, @NonNull Object message) {
        mLastProgressNotification = (NotificationInfo) message;
        if (service != null) {
            mAttachedToService = true;
            Notification notification = mLastProgressNotification
                    .getBuilder(mContext, mProgressNotificationManagerInfo)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(0, 0, false)
                    .build();
            service.startForeground(mProgressNotificationId, notification);
        }
    }

    @Override
    public void onProgressStart(int max, float current, @Nullable Object message) {
        onProgressUpdate(max, current, message);
    }

    @Override
    public void onProgressUpdate(int max, float current, @Nullable Object message) {
        if (message != null) {
            mLastProgressNotification = (NotificationInfo) message;
        } else {
            Objects.requireNonNull(mLastProgressNotification);
        }
        mLastMax = max;
        mLastProgress = current;
        CharSequence progressText = progressTextInterface.getProgressText(this);
        boolean indeterminate = max == -1;
        int newMax = Math.max(max, 0);
        int newCurrent = max < 0 ? 0 : (int) current;
        NotificationCompat.Builder builder = mLastProgressNotification
                .getBuilder(mContext, mProgressNotificationManagerInfo)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(newMax, newCurrent, indeterminate);
        if (progressText != null) {
            if (max > 0) {
                builder.setContentText(progressText);
            } else if (max == MAX_FINISHED) {
                builder.setContentText(mContext.getString(R.string.done));
            } else {
                builder.setContentText(mContext.getString(R.string.operation_running));
            }
        }
        notify(mContext, mProgressNotificationManager, mProgressNotificationId, builder.build());
    }

    @Override
    public void onResult(@Nullable Object message) {
        if (!mAttachedToService) {
            mProgressNotificationManager.cancel(mProgressNotificationId);
        } else {
            onProgressUpdate(MAX_FINISHED, 0, null); // Trick to remove progressbar
        }
        if (message == null) {
            return;
        }
        NotificationInfo info = (NotificationInfo) message;
        Notification notification = info
                .getBuilder(mContext, mCompletionNotificationManagerInfo)
                .build();
        int notificationId = NotificationUtils.getNotificationId(mCompletionNotificationManagerInfo.channelId);
        notify(mContext, mCompletionNotificationManager, notificationId, notification);
    }

    @Override
    public void onDetach(@Nullable Service service) {
        if (service != null) {
            mAttachedToService = false;
            mProgressNotificationManager.cancel(mProgressNotificationId);
        }
    }

    @Override
    @NonNull
    public ProgressHandler newSubProgressHandler() {
        return new NotificationProgressHandler(mContext, mProgressNotificationManagerInfo,
                mCompletionNotificationManagerInfo, null);
    }

    @Override
    public int getLastMax() {
        return mLastMax;
    }

    @Override
    public float getLastProgress() {
        return mLastProgress;
    }

    @Nullable
    @Override
    public Object getLastMessage() {
        return mLastProgressNotification;
    }

    private static void notify(@NonNull Context context,
                               @NonNull NotificationManagerCompat notificationManager,
                               int notificationId,
                               @NonNull Notification notification) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(notificationId, notification);
        }
    }

    @Nullable
    @Contract("_, !null -> !null")
    private static NotificationManagerCompat getNotificationManager(@NonNull Context context,
                                                                    @Nullable NotificationManagerInfo info) {
        if (info == null) {
            return null;
        }
        NotificationChannelCompat channel = new NotificationChannelCompat
                .Builder(info.channelId, info.importance)
                .setName(info.channelName)
                .build();
        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(context);
        managerCompat.createNotificationChannel(channel);
        return managerCompat;
    }

    public static class NotificationManagerInfo {
        @NonNull
        public final String channelId;
        @NonNull
        public final CharSequence channelName;
        @NotificationUtils.NotificationImportance
        public final int importance;

        public NotificationManagerInfo(@NonNull String channelId, @NonNull CharSequence channelName,
                                       @NotificationUtils.NotificationImportance int importance) {
            this.channelId = channelId;
            this.channelName = channelName;
            this.importance = importance;
        }
    }

    public static class NotificationInfo {
        @DrawableRes
        private int icon = R.drawable.ic_default_notification;
        private int level;
        private long time = 0L;
        @Nullable
        private CharSequence operationName;
        @Nullable
        private CharSequence title;
        @Nullable
        private CharSequence body;
        @Nullable
        private CharSequence statusBarText;
        @Nullable
        private NotificationCompat.Style style;
        private boolean autoCancel;
        @Nullable
        private PendingIntent defaultAction;
        @Nullable
        private String groupId;

        private final ArrayList<NotificationCompat.Action> actions = new ArrayList<>();


        public NotificationInfo() {
        }

        public NotificationInfo(@NonNull NotificationInfo notificationInfo) {
            icon = notificationInfo.icon;
            level = notificationInfo.level;
            time = notificationInfo.time;
            operationName = notificationInfo.operationName;
            title = notificationInfo.title;
            body = notificationInfo.body;
            statusBarText = notificationInfo.statusBarText;
            style = notificationInfo.style;
            autoCancel = notificationInfo.autoCancel;
            defaultAction = notificationInfo.defaultAction;
            groupId = notificationInfo.groupId;
            actions.addAll(notificationInfo.actions);
        }

        public NotificationInfo setIcon(int icon) {
            this.icon = icon;
            return this;
        }

        public NotificationInfo setLevel(int level) {
            this.level = level;
            return this;
        }

        public NotificationInfo setTitle(@Nullable CharSequence title) {
            this.title = title;
            return this;
        }

        public NotificationInfo setBody(@Nullable CharSequence body) {
            this.body = body;
            return this;
        }

        public NotificationInfo setStatusBarText(@Nullable CharSequence statusBarText) {
            this.statusBarText = statusBarText;
            return this;
        }

        public NotificationInfo setDefaultAction(@Nullable PendingIntent defaultAction) {
            this.defaultAction = defaultAction;
            return this;
        }

        public NotificationInfo setOperationName(@Nullable CharSequence operationName) {
            this.operationName = operationName;
            return this;
        }

        public NotificationInfo setStyle(@Nullable NotificationCompat.Style style) {
            this.style = style;
            return this;
        }

        public NotificationInfo setAutoCancel(boolean autoCancel) {
            this.autoCancel = autoCancel;
            return this;
        }

        public NotificationInfo setTime(long time) {
            this.time = time;
            return this;
        }

        public void setGroupId(@Nullable String groupId) {
            this.groupId = groupId;
        }

        public NotificationInfo addAction(int icon, @Nullable CharSequence title,
                                          @Nullable PendingIntent intent) {
            actions.add(new NotificationCompat.Action(icon, title, intent));
            return this;
        }

        @NonNull
        NotificationCompat.Builder getBuilder(@NonNull Context context, @NonNull NotificationManagerInfo info) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, info.channelId)
                    .setPriority(NotificationUtils.importanceToPriority(info.importance))
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setSmallIcon(icon, level)
                    .setSubText(operationName)
                    .setTicker(statusBarText)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setContentIntent(defaultAction)
                    .setAutoCancel(autoCancel)
                    .setGroup(groupId)
                    .setStyle(style);
            if (groupId == null) {
                builder.setGroupSummary(false);
                builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
            }
            for (NotificationCompat.Action action : actions) {
                builder.addAction(action);
            }
            if (time > 0L) {
                builder.setWhen(time);
                builder.setShowWhen(true);
            }
            return builder;
        }
    }
}
