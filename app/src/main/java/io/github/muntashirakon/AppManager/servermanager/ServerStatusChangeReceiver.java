// SPDX-License-Identifier: MIT

package io.github.muntashirakon.AppManager.servermanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import java.io.IOException;

import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.server.common.ConfigParams;
import io.github.muntashirakon.AppManager.server.common.ServerActions;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.adb.AdbPairingRequiredException;

// Copyright 2016 Zheng Li
public class ServerStatusChangeReceiver extends BroadcastReceiver {
    private static final String TAG = ServerStatusChangeReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, @NonNull Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        // Verify token before doing action
        String token = intent.getStringExtra(ConfigParams.PARAM_TOKEN);
        if (ServerConfig.getLocalToken().equals(token)) {
            String uidString = intent.getStringExtra(ConfigParams.PARAM_UID);
            Log.d(TAG, "onReceive --> %s %s", action, uidString);
            int uid = Integer.parseInt(uidString);

            switch (action) {
                case ServerActions.ACTION_SERVER_STARTED:
                    // Server was started for the first time
                    startServerIfNotAlready(context);
                    // TODO: 8/4/24 Need to broadcast this message to update UI and/or trigger development
                    break;
                case ServerActions.ACTION_SERVER_STOPPED:
                    // Server was stopped
                    LocalServer.die();
                    break;
                case ServerActions.ACTION_SERVER_CONNECTED:
                    // Server was connected with App Manager
                    break;
                case ServerActions.ACTION_SERVER_DISCONNECTED:
                    // Exited from App Manager
                    break;
            }
        }
    }

    @AnyThread
    private void startServerIfNotAlready(@NonNull Context context) {
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                while (!LocalServer.alive(context)) {
                    // Server isn't yet in listening mode
                    Log.w(TAG, "Waiting for server...");
                    SystemClock.sleep(100);
                }
                LocalServer.getInstance();
                LocalServices.bindServicesIfNotAlready();
            } catch (IOException | AdbPairingRequiredException e) {
                Log.w(TAG, "Failed to start server", e);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to start services", e);
            }
        });
    }
}
