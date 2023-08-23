// SPDX-License-Identifier: MIT

package io.github.muntashirakon.AppManager.servermanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.server.common.ConfigParams;
import io.github.muntashirakon.AppManager.server.common.ServerActions;

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
            String type = intent.getStringExtra(ConfigParams.PARAM_TYPE);
            Log.d(TAG, "onReceive --> %s %s", action, type);

            switch (action) {
                case ServerActions.ACTION_SERVER_STARTED:
                    break;
                case ServerActions.ACTION_SERVER_STOPPED:
                    break;
                case ServerActions.ACTION_SERVER_CONNECTED:
                    break;
                case ServerActions.ACTION_SERVER_DISCONNECTED:
                    break;
            }
        }
    }
}
