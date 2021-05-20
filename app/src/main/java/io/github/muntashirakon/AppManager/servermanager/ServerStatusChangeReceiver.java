// SPDX-License-Identifier: MIT

package io.github.muntashirakon.AppManager.servermanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.server.common.Actions;

// Copyright 2016 Zheng Li
public class ServerStatusChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "ServerStatusChangeRecei";

    @Override
    public void onReceive(Context context, @NonNull Intent intent) {
        // Verify token before doing action
        String token = intent.getStringExtra("token");
        if (ServerConfig.getLocalToken().equals(token)) {
            String action = intent.getAction();
            Log.e(TAG, "onReceive --> " + action + "   " + token + "  " + intent
                    .getStringExtra("type"));

            if (Actions.ACTION_SERVER_STARTED.equals(action)) {
            } else if (Actions.ACTION_SERVER_CONNECTED.equals(action)) {
            } else if (Actions.ACTION_SERVER_DISCONNECTED.equals(action)) {
            } else if (Actions.ACTION_SERVER_STOPPED.equals(action)) {
            }
        }
    }
}
