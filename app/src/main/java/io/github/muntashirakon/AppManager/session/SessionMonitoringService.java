// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.session;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.Process;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.DummyActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.ScreenLockChecker;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class SessionMonitoringService extends Service {
    public static final String TAG = SessionMonitoringService.class.getSimpleName();

    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.SESSION_MONITOR";

    public static final String STOP_ACTION = BuildConfig.APPLICATION_ID + ".action.STOP_SESSION_MONITOR";

    private boolean mIsWorking;
    @Nullable
    private Future<?> mCheckLockResult;
    private ScreenLockChecker mScreenLockChecker;
    private boolean mScreenLockedReceiverRegistered = false;

    private final BroadcastReceiver mScreenLockedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (mCheckLockResult != null) {
                    mCheckLockResult.cancel(true);
                }
                mCheckLockResult = ThreadUtils.postOnBackgroundThread(() -> {
                    if (mScreenLockChecker == null) {
                        mScreenLockChecker = new ScreenLockChecker(SessionMonitoringService.this, () -> lockScreen());
                    }
                    mScreenLockChecker.checkLock();
                });
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    };

    public SessionMonitoringService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP_ACTION.equals(intent.getAction())) {
            lockScreen();
            return START_NOT_STICKY;
        }
        if (mIsWorking) {
            return START_NOT_STICKY;
        }
        mIsWorking = true;
        boolean screenLockEnabled = Prefs.Privacy.isScreenLockEnabled();
        NotificationUtils.getNewNotificationManager(this, CHANNEL_ID, "Session Monitor",
                NotificationManagerCompat.IMPORTANCE_LOW);
        Intent notificationSettingIntent = NotificationUtils.getNotificationSettingIntent(CHANNEL_ID);
        PendingIntent defaultIntent = PendingIntentCompat.getActivity(this, 0, notificationSettingIntent, PendingIntent.FLAG_UPDATE_CURRENT, false);
        Intent stopIntent = new Intent(this, SessionMonitoringService.class).setAction(STOP_ACTION);
        PendingIntent pendingIntent = PendingIntentCompat.getService(this, 0, stopIntent, PendingIntent.FLAG_ONE_SHOT, false);
        NotificationCompat.Action stopServiceAction = new NotificationCompat.Action.Builder(null,
                getString(screenLockEnabled ? R.string.action_lock_app : R.string.action_stop_service), pendingIntent)
                .setAuthenticationRequired(true)
                .build();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setLocalOnly(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentTitle(getString(screenLockEnabled ? R.string.app_manager_is_unlocked : R.string.app_manager_is_running))
                .setContentText(getString(R.string.tap_to_open_notification_settings))
                .setSmallIcon(R.drawable.ic_default_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(defaultIntent)
                .addAction(stopServiceAction);
        ForegroundService.start(this, NotificationUtils.nextNotificationId(null), builder.build(),
                ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        | ForegroundService.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        if (screenLockEnabled && Prefs.Privacy.isAutoLockEnabled()) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            ContextCompat.registerReceiver(this, mScreenLockedReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
            mScreenLockedReceiverRegistered = true;
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // https://issuetracker.google.com/issues/36967794
        Intent intent = new Intent(this, DummyActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onDestroy() {
        if (mScreenLockedReceiverRegistered) {
            unregisterReceiver(mScreenLockedReceiver);
            mScreenLockedReceiverRegistered = false;
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    public void lockScreen() {
        // Simply stopping the service is enough
        // TODO: 16/7/23 Wipe memory? Ref: https://github.com/mollyim/mollyim-android/blob/2f8fe769628f7daddc87e8acfab1c4b5d301f728/app/src/main/java/org/thoughtcrime/securesms/service/WipeMemoryService.java#L102
        stopSelf();
        Process.killProcess(Process.myPid());
    }
}
