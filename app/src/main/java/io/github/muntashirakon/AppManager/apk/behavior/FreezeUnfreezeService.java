// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getBitmapFromDrawable;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getDimmedBitmap;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.DummyActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.misc.ScreenLockChecker;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class FreezeUnfreezeService extends Service {
    public static final String TAG = FreezeUnfreezeService.class.getSimpleName();

    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.FREEZE_UNFREEZE_MONITOR";

    private static final String STOP_ACTION = BuildConfig.APPLICATION_ID + ".action.STOP_FREEZE_UNFREEZE_MONITOR";

    private final Map<String, FreezeUnfreezeShortcutInfo> mPackagesToShortcut = new HashMap<>();
    private final Map<String, String> mPackagesToNotificationTag = new HashMap<>();
    private ScreenLockChecker mScreenLockChecker;
    private final BroadcastReceiver mScreenLockedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (mCheckLockResult != null) {
                    mCheckLockResult.cancel(true);
                }
                mCheckLockResult = ThreadUtils.postOnBackgroundThread(() -> {
                    if (mScreenLockChecker == null) {
                        mScreenLockChecker = new ScreenLockChecker(FreezeUnfreezeService.this, () -> freezeAllPackages());
                    }
                    mScreenLockChecker.checkLock();
                });
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    };

    private boolean mIsWorking;
    @Nullable
    private Future<?> mCheckLockResult;
    private PowerManager.WakeLock mWakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        mWakeLock = CpuUtils.getPartialWakeLock("freeze_unfreeze");
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null && STOP_ACTION.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        onHandleIntent(intent);
        if (mIsWorking) {
            return START_NOT_STICKY;
        }
        mIsWorking = true;
        NotificationUtils.getNewNotificationManager(this, CHANNEL_ID, "Freeze/unfreeze Monitor",
                NotificationManagerCompat.IMPORTANCE_LOW);
        Intent stopIntent = new Intent(this, FreezeUnfreezeService.class).setAction(STOP_ACTION);
        PendingIntent pendingIntent = PendingIntentCompat.getService(this, 0, stopIntent, PendingIntent.FLAG_ONE_SHOT, false);
        NotificationCompat.Action stopServiceAction = new NotificationCompat.Action.Builder(null,
                getString(R.string.action_stop_service), pendingIntent)
                .setAuthenticationRequired(true)
                .build();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setLocalOnly(true)
                .setOngoing(true)
                .setContentTitle(null)
                .setContentText(getString(R.string.waiting_for_the_phone_to_be_locked))
                .setSmallIcon(R.drawable.ic_default_notification)
                .setSubText(getText(R.string.freeze_unfreeze))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(stopServiceAction);
        ForegroundService.start(this, NotificationUtils.nextNotificationId(null), builder.build(),
                ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        | ForegroundService.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        ContextCompat.registerReceiver(this, mScreenLockedReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
        return START_NOT_STICKY;
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
        unregisterReceiver(mScreenLockedReceiver);
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        if (mCheckLockResult != null) {
            mCheckLockResult.cancel(true);
        }
        CpuUtils.releaseWakeLock(mWakeLock);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;
        FreezeUnfreezeShortcutInfo shortcutInfo = FreezeUnfreeze.getShortcutInfo(intent);
        if (shortcutInfo == null) return;
        mPackagesToShortcut.put(shortcutInfo.packageName, shortcutInfo);
        String notificationTag = String.valueOf(shortcutInfo.hashCode());
        mPackagesToNotificationTag.put(shortcutInfo.packageName, notificationTag);
    }

    @WorkerThread
    private void freezeAllPackages() {
        for (String packageName : mPackagesToShortcut.keySet()) {
            FreezeUnfreezeShortcutInfo shortcutInfo = mPackagesToShortcut.get(packageName);
            String notificationTag = mPackagesToNotificationTag.get(packageName);
            if (shortcutInfo != null) {
                try {
                    ApplicationInfo applicationInfo = PackageManagerCompat.getApplicationInfo(shortcutInfo.packageName,
                            MATCH_UNINSTALLED_PACKAGES | MATCH_DISABLED_COMPONENTS
                                    | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, shortcutInfo.userId);
                    Bitmap icon = getBitmapFromDrawable(applicationInfo.loadIcon(getApplication().getPackageManager()));
                    shortcutInfo.setName(applicationInfo.loadLabel(getApplication().getPackageManager()));
                    int freezeType = Optional.ofNullable(FreezeUtils.getFreezingMethod(shortcutInfo.packageName))
                            .orElse(Prefs.Blocking.getDefaultFreezingMethod());
                    FreezeUtils.freeze(shortcutInfo.packageName, shortcutInfo.userId, freezeType);
                    shortcutInfo.setIcon(getDimmedBitmap(icon));
                    updateShortcuts(shortcutInfo);
                } catch (RemoteException | PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            }
            if (notificationTag != null) {
                NotificationUtils.getFreezeUnfreezeNotificationManager(this).cancel(notificationTag, 1);
            }
        }
        stopSelf();
    }

    private void updateShortcuts(@NonNull FreezeUnfreezeShortcutInfo shortcutInfo) {
        Intent intent = FreezeUnfreeze.getShortcutIntent(this, shortcutInfo);
        // Set action for shortcut
        intent.setAction(Intent.ACTION_CREATE_SHORTCUT);
        ShortcutInfoCompat shortcutInfoCompat = new ShortcutInfoCompat.Builder(this, shortcutInfo.getId())
                .setShortLabel(shortcutInfo.getName())
                .setLongLabel(shortcutInfo.getName())
                .setIcon(IconCompat.createWithBitmap(shortcutInfo.getIcon()))
                .setIntent(intent)
                .build();
        ShortcutManagerCompat.updateShortcuts(this, Collections.singletonList(shortcutInfoCompat));
    }
}
