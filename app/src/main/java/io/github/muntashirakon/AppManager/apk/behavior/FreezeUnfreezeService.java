// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import static io.github.muntashirakon.AppManager.utils.FileUtils.dimBitmap;
import static io.github.muntashirakon.AppManager.utils.FileUtils.getBitmapFromDrawable;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PendingIntentCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class FreezeUnfreezeService extends Service {
    public static final String TAG = FreezeUnfreezeService.class.getSimpleName();

    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.FREEZE_UNFREEZE_MONITOR";
    public static final int NOTIFICATION_ID = 1;

    private static final String STOP_ACTION = BuildConfig.APPLICATION_ID + ".action.STOP_FREEZE_UNFREEZE_MONITOR";

    private final Map<String, FreezeUnfreeze.ShortcutInfo> packagesToShortcut = new HashMap<>();
    private final Map<String, Integer> packagesToNotificationId = new HashMap<>();
    private static final Timer timer = new Timer();
    private static final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final BroadcastReceiver screenLockedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                executor.submit(() -> checkLock(-1));
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    };

    private CheckLockTask checkLockTask;
    private boolean isWorking;

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null && STOP_ACTION.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        onHandleIntent(intent);
        if (isWorking) {
            return START_NOT_STICKY;
        }
        isWorking = true;
        NotificationManagerCompat notificationManager = NotificationUtils.getNewNotificationManager(this, CHANNEL_ID,
                "Freeze/unfreeze Monitor", NotificationManagerCompat.IMPORTANCE_LOW);
        Intent stopIntent = new Intent(this, FreezeUnfreezeService.class).setAction(STOP_ACTION);
        @SuppressLint("WrongConstant")
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntentCompat.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
        NotificationCompat.Action stopServiceAction = new NotificationCompat.Action.Builder(null,
                getString(R.string.action_stop_service), pendingIntent)
                .setAuthenticationRequired(true)
                .build();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(null)
                .setContentText(getString(R.string.waiting_for_the_phone_to_be_locked))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setSubText(getText(R.string.freeze_unfreeze))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(stopServiceAction);
        startForeground(NOTIFICATION_ID, builder.build());
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenLockedReceiver, filter);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(screenLockedReceiver);
        stopForeground(true);
        executor.shutdownNow();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;
        FreezeUnfreeze.ShortcutInfo shortcutInfo = FreezeUnfreeze.getShortcutInfo(intent);
        if (shortcutInfo == null) return;
        packagesToShortcut.put(shortcutInfo.packageName, shortcutInfo);
        int notificationId = shortcutInfo.hashCode();
        packagesToNotificationId.put(shortcutInfo.packageName, notificationId);
    }

    @WorkerThread
    private void checkLock(int delayIndex) {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        final boolean isProtected = keyguardManager.isKeyguardSecure();
        final boolean isLocked = keyguardManager.inKeyguardRestrictedInputMode();
        final boolean isInteractive = powerManager.isInteractive();
        delayIndex = getSafeCheckLockDelay(delayIndex);
        Log.i(TAG, String.format(Locale.ROOT, "checkLock: isProtected=%b, isLocked=%b, isInteractive=%b, delay=%d",
                isProtected, isLocked, isInteractive, checkLockDelays[delayIndex]));

        if (checkLockTask != null) {
            Log.i(TAG, String.format(Locale.ROOT, "checkLock: cancelling CheckLockTask[%x]", System.identityHashCode(checkLockTask)));
            checkLockTask.cancel();
        }

        if (isProtected && !isLocked && !isInteractive) {
            checkLockTask = new CheckLockTask(delayIndex);
            Log.i(TAG, String.format(Locale.ROOT, "checkLock: scheduling CheckLockTask[%x] for %d ms", System.identityHashCode(checkLockTask), checkLockDelays[delayIndex]));
            timer.schedule(checkLockTask, checkLockDelays[delayIndex]);
        } else {
            Log.d(TAG, "checkLock: no need to schedule CheckLockTask");
            if (isProtected && isLocked) {
                freezeAllPackages();
            }
        }
    }

    @WorkerThread
    private void freezeAllPackages() {
        for (String packageName : packagesToShortcut.keySet()) {
            FreezeUnfreeze.ShortcutInfo shortcutInfo = packagesToShortcut.get(packageName);
            Integer notificationId = packagesToNotificationId.get(packageName);
            if (shortcutInfo != null) {
                try {
                    ApplicationInfo applicationInfo = PackageManagerCompat.getApplicationInfo(shortcutInfo.packageName, PackageUtils.flagMatchUninstalled | PackageUtils.flagDisabledComponents, shortcutInfo.userId);
                    Bitmap icon = getBitmapFromDrawable(applicationInfo.loadIcon(getApplication().getPackageManager()));
                    shortcutInfo.setLabel(applicationInfo.loadLabel(getApplication().getPackageManager()).toString());
                    FreezeUtils.freeze(shortcutInfo.packageName, shortcutInfo.userId);
                    dimBitmap(icon);
                    shortcutInfo.setIcon(icon);
                    updateShortcuts(shortcutInfo);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            if (notificationId != null) {
                NotificationUtils.getFreezeUnfreezeNotificationManager(this).cancel(notificationId);
            }
        }
        stopSelf();
    }

    private void updateShortcuts(@NonNull FreezeUnfreeze.ShortcutInfo shortcutInfo) {
        Intent intent = FreezeUnfreeze.getShortcutIntent(this, shortcutInfo);
        // Set action for shortcut
        intent.setAction(Intent.ACTION_CREATE_SHORTCUT);
        ShortcutInfoCompat shortcutInfoCompat = new ShortcutInfoCompat.Builder(this, shortcutInfo.shortcutId)
                .setShortLabel(shortcutInfo.getLabel())
                .setLongLabel(shortcutInfo.getLabel())
                .setIcon(IconCompat.createWithBitmap(shortcutInfo.getIcon()))
                .setIntent(intent)
                .build();
        ShortcutManagerCompat.updateShortcuts(this, Collections.singletonList(shortcutInfoCompat));
    }

    private static final int SECOND = 1000;
    private static final int MINUTE = 60 * SECOND;
    // This tracks the deltas between the actual options of 5s, 15s, 30s, 1m, 2m, 5m, 10m
    // It also includes an initial offset and some extra times (for safety)
    private static final int[] checkLockDelays = new int[]{SECOND, 5 * SECOND, 10 * SECOND, 20 * SECOND, 30 * SECOND, MINUTE, 3 * MINUTE, 5 * MINUTE, 10 * MINUTE, 30 * MINUTE};

    private static int getSafeCheckLockDelay(final int delayIndex) {
        final int safeDelayIndex;
        if (delayIndex >= checkLockDelays.length) {
            safeDelayIndex = checkLockDelays.length - 1;
        } else safeDelayIndex = Math.max(delayIndex, 0);
        Log.v(TAG, String.format(Locale.ROOT, "getSafeCheckLockDelay(%d) returns %d", delayIndex, safeDelayIndex));
        return safeDelayIndex;
    }

    private class CheckLockTask extends TimerTask {
        final int delayIndex;

        CheckLockTask(final int delayIndex) {
            this.delayIndex = delayIndex;
        }

        @Override
        public void run() {
            Log.i(TAG, String.format("CLT.run [%x]: redirect intent to LockMonitor", System.identityHashCode(this)));
            checkLock(getSafeCheckLockDelay(delayIndex + 1));
        }
    }
}
