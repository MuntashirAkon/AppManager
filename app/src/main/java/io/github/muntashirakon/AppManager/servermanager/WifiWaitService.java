// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.adb.AdbUtils;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

@RequiresApi(Build.VERSION_CODES.R)
public class WifiWaitService extends Service {
    private static final String TAG = WifiWaitService.class.getSimpleName();
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.WIFI_WAIT_SERVICE";

    private final ConnectivityManager.NetworkCallback mNetworkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            Log.d(TAG, "Wi-Fi network available");
        }

        @Override
        public void onLost(@NonNull Network network) {
            Log.d(TAG, "Network lost");
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network,
                                          @NonNull NetworkCapabilities networkCapabilities) {
            // Double-check Wi-Fi availability when capabilities change
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !mAutoconnectCompleted) {
                connectAdbWifi();
                unregisterNetworkCallback();
            }
        }
    };
    private ConnectivityManager mConnectivityManager;
    private boolean mAutoconnectCompleted = false;
    private boolean mUnregisterDone = true;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationUtils.getNewNotificationManager(this, CHANNEL_ID, "Wi-Fi Wait Service",
                NotificationManagerCompat.IMPORTANCE_LOW);
        mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.waiting_for_wifi))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        ForegroundService.start(this, NotificationUtils.nextNotificationId(null),
                notification, ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        | ForegroundService.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        if (LocalServer.alive(getApplicationContext())) {
            // Already connected
            mAutoconnectCompleted = true;
        }

        if (!mAutoconnectCompleted) {
            registerNetworkCallback();
        } else {
            stopSelf();
        }

        return START_NOT_STICKY; // Don't restart if killed
    }

    private void registerNetworkCallback() {
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
        try {
            mConnectivityManager.registerNetworkCallback(networkRequest, mNetworkCallback);
            mUnregisterDone = false;
            Log.d(TAG, "Network callback registered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register network callback", e);
            stopSelf();
        }
    }

    private void connectAdbWifi() {
        if (mAutoconnectCompleted) {
            return; // Prevent multiple executions
        }
        mAutoconnectCompleted = true;

        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                doConnectAdbWifi();
            } finally {
                stopSelf();
            }
        });
    }

    @WorkerThread
    private void doConnectAdbWifi() {
        Context context = getApplicationContext();
        if (!Utils.isWifiActive(context)) {
            Log.w(TAG, "Autoconnect failed: Wi-Fi not enabled.");
            return;
        }

        if (!AdbUtils.enableWirelessDebugging(context)) {
            Log.w(TAG, "Autoconnect failed: Could not enable wireless debugging.");
            return;
        }

        int status = Ops.autoConnectWirelessDebugging(context);
        if (status == Ops.STATUS_ADB_PAIRING_REQUIRED) {
            Log.w(TAG, "Autoconnect failed: pairing required");
        } else if (status == Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED) {
            Log.w(TAG, "Autoconnect failed: could not find a valid port");
        } else if (status == Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS) {
            Log.w(TAG, "Autoconnect failed: not enough permissions available");
        } else if (status == Ops.STATUS_SUCCESS) {
            Log.i(TAG, "Autoconnect success!");
        } else {
            Log.w(TAG, "Autoconnect failed");
        }
    }

    private void unregisterNetworkCallback() {
        if (mUnregisterDone) {
            return;
        }
        mUnregisterDone = true;
        try {
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
            Log.d(TAG, "Network callback unregistered");
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering callback", e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        unregisterNetworkCallback();
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
    }
}
