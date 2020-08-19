/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.server;

import android.content.Intent;

import java.util.Map;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.server.common.Actions;
import io.github.muntashirakon.AppManager.server.common.ServerRunInfo;

import static io.github.muntashirakon.AppManager.server.common.ConfigParam.PARAM_TOKEN;
import static io.github.muntashirakon.AppManager.server.common.ConfigParam.PARAM_TYPE;

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
