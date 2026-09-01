// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import static io.github.muntashirakon.AppManager.types.ForegroundService.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.app.RemoteInput;
import androidx.core.app.ServiceCompat;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.adb.android.AdbMdns;

// This works as follows:
// 1. Start searching for a pairing port
// 2. A port is found, ask to enter a pairing code
// 3. Start pairing
// 4. Exit with result, or ask to retry
@RequiresApi(Build.VERSION_CODES.R)
public class AdbPairingService extends Service {
    public static final String TAG = AdbPairingService.class.getSimpleName();
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.ADB_PAIRING";
    public static final String ACTION_START_SEARCHING = BuildConfig.APPLICATION_ID + ".action.START_SEARCHING";
    public static final String ACTION_STOP_SEARCHING = BuildConfig.APPLICATION_ID + ".action.STOP_SEARCHING";
    public static final String ACTION_START_PAIRING = BuildConfig.APPLICATION_ID + ".action.ENTER_CODE";
    public static final String EXTRA_PORT = "port";
    public static final String INPUT_CODE = "code";
    private static final long PAIRING_TIMEOUT_MILLIS = 10 * 60 * 1000L;

    private NotificationCompat.Builder mNotificationBuilder;
    private boolean mStartedSearching = false;
    private volatile boolean mPairingSucceeded = false;
    @Nullable
    private AdbMdns mAdbMdnsPairing;
    private volatile AdbConnectionManager.PairingSession mPairingSession;
    private int mSearchGeneration;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mPairingTimeout = () -> {
        Log.w(TAG, "Pairing timed out.");
        stopSelf();
    };

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        NotificationChannelCompat notificationChannel = new NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("ADB Pairing")
                .setSound(null, null)
                .setShowBadge(false)
                .build();
        notificationManager.createNotificationChannel(notificationChannel);
        mNotificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setDefaults(Notification.DEFAULT_ALL)
                .setLocalOnly(!Prefs.Misc.sendNotificationsToConnectedDevices())
                .setContentTitle(getString(R.string.wireless_debugging))
                .setSubText(getText(R.string.wireless_debugging))
                .setSmallIcon(R.drawable.ic_default_notification)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            // Invalid intent
            return START_NOT_STICKY;
        }
        switch (intent.getAction()) {
            case ACTION_START_SEARCHING:
                startSearching();
                return START_NOT_STICKY;
            case ACTION_START_PAIRING:
                int port = intent.getIntExtra(EXTRA_PORT, -1);
                Bundle remoteInputs = RemoteInput.getResultsFromIntent(intent);
                if (port != -1 && remoteInputs != null) {
                    String code = remoteInputs.getCharSequence(INPUT_CODE, "").toString().trim();
                    startPairing(port, code);
                } else {
                    // Wrong inputs, continue searching
                    startSearching();
                }
                return START_NOT_STICKY;
            case ACTION_STOP_SEARCHING:
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
                stopSelf();
            default:
                return START_NOT_STICKY;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(mPairingTimeout);
        stopSearching();
        if (!mPairingSucceeded) {
            try {
                if (mPairingSession != null) {
                    mPairingSession.cancel();
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not cancel pairing session.", e);
            }
        }
        super.onDestroy();
    }

    @MainThread
    private void startSearching() {
        if (mStartedSearching) {
            AdbConnectionManager.PairingSession session = AdbConnectionManager.getPairingSession();
            if (session != null && session != mPairingSession) {
                mPairingSession = session;
                ++mSearchGeneration;
                mHandler.removeCallbacks(mPairingTimeout);
                mHandler.postDelayed(mPairingTimeout, PAIRING_TIMEOUT_MILLIS);
            }
            return;
        }
        mStartedSearching = true;
        ++mSearchGeneration;
        mPairingSession = AdbConnectionManager.getPairingSession();
        mHandler.removeCallbacks(mPairingTimeout);
        mHandler.postDelayed(mPairingTimeout, PAIRING_TIMEOUT_MILLIS);
        if (mAdbMdnsPairing == null) {
            mAdbMdnsPairing = new AdbMdns(getApplication(), AdbMdns.SERVICE_TYPE_TLS_PAIRING, (hostAddress, port) -> {
                if (port != -1) {
                    int generation = mSearchGeneration;
                    ThreadUtils.postOnMainThread(() -> {
                        if (mStartedSearching && generation == mSearchGeneration) {
                            Log.i(TAG, "Found port %d", port);
                            inputPairingCode(port);
                        }
                    });
                }
            });
        }
        PendingIntent stopPendingIntent = getStopIntent();
        NotificationCompat.Action stopAction = new NotificationCompat.Action.Builder(null, getString(R.string.adb_pairing_stop_searching), stopPendingIntent).build();
        mNotificationBuilder.setContentText(getText(R.string.adb_pairing_searching_for_port))
                .clearActions()
                .addAction(stopAction);
        ServiceCompat.startForeground(this, 1, mNotificationBuilder.build(), FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        try {
            mAdbMdnsPairing.start();
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not start ADB pairing discovery.", e);
            mHandler.removeCallbacks(mPairingTimeout);
            stopSearching();
            mAdbMdnsPairing = null;
            if (mPairingSession != null) {
                mPairingSession.cancel();
            }
            stopSelf();
        }
    }

    @SuppressLint("MissingPermission") // Already checked notification permission
    @MainThread
    private void inputPairingCode(int port) {
        Intent inputIntent = new Intent(this, getClass())
                .setAction(ACTION_START_PAIRING)
                .putExtra(EXTRA_PORT, port);
        PendingIntent inputPendingIntent = PendingIntentCompat.getForegroundService(this, 2, inputIntent, PendingIntent.FLAG_UPDATE_CURRENT, true);
        RemoteInput pairingCodeInput = new RemoteInput.Builder(INPUT_CODE)
                .setLabel(getString(R.string.adb_pairing_pairing_code))
                .build();
        NotificationCompat.Action inputAction = new NotificationCompat.Action.Builder(null, getString(R.string.adb_pairing_input_pairing_code), inputPendingIntent)
                .addRemoteInput(pairingCodeInput)
                .build();
        mNotificationBuilder.setContentText(getString(R.string.adb_pairing_found_pairing_service_with_port, port))
                .clearActions()
                .addAction(inputAction);
        if (canPostNotifications()) {
            NotificationManagerCompat.from(this).notify(1, mNotificationBuilder.build());
        }
    }

    @SuppressLint("MissingPermission") // Already checked notification permission
    @MainThread
    private void startPairing(int port, String code) {
        mNotificationBuilder.setContentText(getString(R.string.adb_pairing_pairing_in_progress))
                .clearActions();
        ServiceCompat.startForeground(this, 1, mNotificationBuilder.build(), FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        final AdbConnectionManager.PairingSession session;
        try {
            session = mPairingSession != null ? mPairingSession : AdbConnectionManager.getPairingSession();
        } catch (Exception e) {
            Log.e(TAG, "Could not access pairing session.", e);
            stopSelf();
            return;
        }
        if (session == null) {
            Log.e(TAG, "Pairing was started without an active session.");
            stopSelf();
            return;
        }
        ThreadUtils.postOnBackgroundThread(() -> {
            boolean isSuccess;
            try {
                AdbConnectionManager.getInstance().pairAndReport(session, ServerConfig.getAdbHost(this), port, code);
                isSuccess = true;
            } catch (Exception e) {
                Log.w(TAG, "Pairing failed.", e);
                isSuccess = false;
            }
            if (session != mPairingSession) {
                return;
            }
            ThreadUtils.postOnMainThread(this::stopSearching);
            if (isSuccess) {
                mPairingSucceeded = true;
                mNotificationBuilder.setContentText(getString(R.string.paired_successfully)).clearActions();
                stopSelf();
            } else {
                PendingIntent deleteIntent = getStopIntent();
                Intent retryIntent = new Intent(this, getClass()).setAction(ACTION_START_SEARCHING);
                PendingIntent retryPendingIntent = PendingIntentCompat.getForegroundService(this, 3, retryIntent, 0, false);
                NotificationCompat.Action retryAction = new NotificationCompat.Action.Builder(null, getString(R.string.adb_pairing_retry_pairing), retryPendingIntent).build();
                mNotificationBuilder.setContentText(getString(R.string.failed))
                        .clearActions()
                        .setDeleteIntent(deleteIntent)
                        .addAction(retryAction);
            }
            if (canPostNotifications()) {
                NotificationManagerCompat.from(this).notify(1, mNotificationBuilder.build());
            }
        });
    }

    @MainThread
    private void stopSearching() {
        if (!mStartedSearching) {
            return;
        }
        mStartedSearching = false;
        ++mSearchGeneration;
        if (mAdbMdnsPairing != null) {
            try {
                mAdbMdnsPairing.stop();
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not stop ADB pairing discovery.", e);
            }
        }
    }

    @NonNull
    private PendingIntent getStopIntent() {
        Intent stopIntent = new Intent(this, getClass()).setAction(ACTION_STOP_SEARCHING);
        return PendingIntentCompat.getForegroundService(this, 1, stopIntent, 0, false);
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || SelfPermissions.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS);
    }
}
