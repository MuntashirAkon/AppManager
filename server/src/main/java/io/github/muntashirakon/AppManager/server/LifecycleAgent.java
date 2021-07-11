// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server;

import android.content.Intent;

import java.util.Map;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.server.common.Actions;
import io.github.muntashirakon.AppManager.server.common.ServerRunInfo;

import static io.github.muntashirakon.AppManager.server.common.ConfigParam.PARAM_TOKEN;
import static io.github.muntashirakon.AppManager.server.common.ConfigParam.PARAM_TYPE;

// Copyright 2017 Zheng Li
class LifecycleAgent {
    static volatile Map<String, String> sConfigParams;
    static ServerRunInfo serverRunInfo = new ServerRunInfo();

    static void onStarted() {
        Intent intent = makeIntent(Actions.ACTION_SERVER_STARTED);
        BroadcastSender.sendBroadcast(intent);
    }

    static void onConnected() {
        Intent intent = makeIntent(Actions.ACTION_SERVER_CONNECTED);
        BroadcastSender.sendBroadcast(intent);
    }

    static void onDisconnected() {
        Intent intent = makeIntent(Actions.ACTION_SERVER_DISCONNECTED);
        BroadcastSender.sendBroadcast(intent);
    }

    static void onStopped() {
        Intent intent = makeIntent(Actions.ACTION_SERVER_STOPPED);
        BroadcastSender.sendBroadcast(intent);
    }

    @NonNull
    private static Intent makeIntent(String action) {
        Intent intent = new Intent(action);
        if (sConfigParams != null) {
            intent.putExtra(PARAM_TOKEN, sConfigParams.get(PARAM_TOKEN));
            intent.putExtra(PARAM_TYPE, sConfigParams.get(PARAM_TYPE));
        }
        return intent;
    }
}
