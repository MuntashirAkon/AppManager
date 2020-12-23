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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.io.IOException;
import java.util.Map;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.server.common.BaseCaller;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.DataTransmission;
import io.github.muntashirakon.AppManager.server.common.FLog;
import io.github.muntashirakon.AppManager.server.common.ParcelableUtil;
import io.github.muntashirakon.AppManager.server.common.Shell;
import io.github.muntashirakon.AppManager.server.common.ShellCaller;

import static io.github.muntashirakon.AppManager.server.common.ConfigParam.PARAM_PATH;
import static io.github.muntashirakon.AppManager.server.common.ConfigParam.PARAM_RUN_IN_BACKGROUND;
import static io.github.muntashirakon.AppManager.server.common.ConfigParam.PARAM_TOKEN;

class ServerHandler implements DataTransmission.OnReceiveCallback, AutoCloseable {
    private static final int MSG_TIMEOUT = 1;
    private static final int DEFAULT_TIMEOUT = 1000 * 60; // 1 min
    private static final int BG_TIMEOUT = DEFAULT_TIMEOUT * 10; // 10 min

    private final Server server;
    private Handler handler;
    private volatile boolean isDead = false;
    private final boolean runInBackground;

    ServerHandler(@NonNull Map<String, String> configParams) throws IOException {
        // Set params
        System.out.println("Config params: " + configParams);
        String path = configParams.get(PARAM_PATH);
        int port = -1;
        try {
            if (path != null) port = Integer.parseInt(path);
        } catch (Exception ignore) {
        }
        String token = configParams.get(PARAM_TOKEN);
        if (token == null) throw new IOException("Token is not found.");
        runInBackground = TextUtils.equals(configParams.get(PARAM_RUN_IN_BACKGROUND), "1");
        // Set server
        if (port == -1) {
            server = new Server(path, token, this);
        } else {
            server = new Server(port, token, this);
        }
        server.runInBackground = runInBackground;
        // If run in background not requested, stop server on time out
        if (!runInBackground) {
            HandlerThread handlerThread = new HandlerThread("watcher-ups");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper()) {
                @Override
                public void handleMessage(@NonNull Message message) {
                    super.handleMessage(message);
                    if (message.what == MSG_TIMEOUT) {
                        close();
                    }
                }
            };
            handler.sendEmptyMessageDelayed(MSG_TIMEOUT, DEFAULT_TIMEOUT);
        }
    }

    void start() throws IOException, RuntimeException {
        server.run();
    }

    @Override
    public void close() {
        FLog.log("ServerHandler: Destroying...");
        try {
            if (!runInBackground && handler != null) {
                handler.removeCallbacksAndMessages(null);
                handler.removeMessages(MSG_TIMEOUT);
                handler.getLooper().quit();
            }
        } catch (Exception e) {
            e.printStackTrace();
            FLog.log(e);
        }
        try {
            isDead = true;
            server.setStop();
        } catch (Exception e) {
            e.printStackTrace();
            FLog.log(e);
        }
    }

    private void sendOpResult(Parcelable result) {
        try {
            server.sendResult(ParcelableUtil.marshall(result));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessage(byte[] bytes) {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler.removeMessages(MSG_TIMEOUT);
        }

        if (!isDead) {
            if (!runInBackground && handler != null) {
                handler.sendEmptyMessageDelayed(MSG_TIMEOUT, BG_TIMEOUT);
            }
            LifecycleAgent.serverRunInfo.rxBytes += bytes.length;
            CallerResult result = null;
            try {
                BaseCaller baseCaller = ParcelableUtil.unmarshall(bytes, BaseCaller.CREATOR);
                int type = baseCaller.getType();
                switch (type) {
                    case BaseCaller.TYPE_CLOSE:
                        close();
                        return;
                    case BaseCaller.TYPE_SHELL:
                        ShellCaller shellCaller = ParcelableUtil.unmarshall(baseCaller.getRawBytes(), ShellCaller.CREATOR);
                        Shell shell = Shell.getShell("");
                        Shell.Result shellResult = shell.exec(shellCaller.getCommand());
                        result = new CallerResult();
                        Parcel parcel = Parcel.obtain();
                        parcel.writeValue(shellResult);
                        result.setReply(parcel.marshall());
                        parcel.recycle();
                }
                LifecycleAgent.serverRunInfo.successCount++;
            } catch (Throwable e) {
                FLog.log(e);
                result = new CallerResult();
                result.setThrowable(e);
                LifecycleAgent.serverRunInfo.errorCount++;
            } finally {
                if (result == null) {
                    result = new CallerResult();
                }
                sendOpResult(result);
            }
        }
    }
}
