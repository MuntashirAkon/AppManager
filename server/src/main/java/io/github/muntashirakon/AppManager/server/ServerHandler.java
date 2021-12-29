// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.io.Closeable;
import java.io.IOException;

import io.github.muntashirakon.AppManager.server.common.BaseCaller;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.ConfigParams;
import io.github.muntashirakon.AppManager.server.common.DataTransmission;
import io.github.muntashirakon.AppManager.server.common.FLog;
import io.github.muntashirakon.AppManager.server.common.ParcelableUtil;
import io.github.muntashirakon.AppManager.server.common.Shell;
import io.github.muntashirakon.AppManager.server.common.ShellCaller;

// Copyright 2017 Zheng Li
class ServerHandler implements DataTransmission.OnReceiveCallback, Closeable {
    private static final int MSG_TIMEOUT = 1;
    private static final int DEFAULT_TIMEOUT = 1000 * 60; // 1 min
    private static final int BG_TIMEOUT = DEFAULT_TIMEOUT * 10; // 10 min

    private final LifecycleAgent mLifecycleAgent;
    private final ConfigParams mConfigParams;
    private final Server mServer;
    private final boolean mRunInBackground;

    private Handler mHandler;
    private volatile boolean mIsDead = false;

    ServerHandler(@NonNull LifecycleAgent lifecycleAgent) throws IOException {
        mLifecycleAgent = lifecycleAgent;
        mConfigParams = mLifecycleAgent.getConfigParams();
        // Set params
        System.out.println("Config params: " + mConfigParams);
        String path = mConfigParams.getPath();
        int port = -1;
        try {
            if (path != null) port = Integer.parseInt(path);
        } catch (Exception ignore) {
        }
        String token = mConfigParams.getToken();
        if (token == null) throw new IOException("Token is not found.");
        mRunInBackground = mConfigParams.isRunInBackground();
        // Set server
        if (port == -1) {
            mServer = new Server(path, token, mLifecycleAgent, this);
        } else {
            mServer = new Server(port, token, mLifecycleAgent, this);
        }
        mServer.mRunInBackground = mRunInBackground;
        // If run in background not requested, stop server on timeout
        if (!mRunInBackground) {
            HandlerThread handlerThread = new HandlerThread("am_server_watcher");
            handlerThread.start();
            mHandler = new Handler(handlerThread.getLooper()) {
                @Override
                public void handleMessage(@NonNull Message message) {
                    super.handleMessage(message);
                    if (message.what == MSG_TIMEOUT) {
                        close();
                    }
                }
            };
            mHandler.sendEmptyMessageDelayed(MSG_TIMEOUT, DEFAULT_TIMEOUT);
        }
    }

    void start() throws IOException, RuntimeException {
        mServer.run();
    }

    @Override
    public void close() {
        FLog.log("ServerHandler: Destroying...");
        try {
            if (!mRunInBackground && mHandler != null) {
                mHandler.removeCallbacksAndMessages(null);
                mHandler.removeMessages(MSG_TIMEOUT);
                mHandler.getLooper().quit();
            }
        } catch (Exception e) {
            e.printStackTrace();
            FLog.log(e);
        }
        try {
            mIsDead = true;
            mServer.close();
        } catch (Exception e) {
            e.printStackTrace();
            FLog.log(e);
        }
    }

    private void sendOpResult(Parcelable result) {
        try {
            mServer.sendResult(ParcelableUtil.marshall(result));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessage(@NonNull byte[] bytes) {
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler.removeMessages(MSG_TIMEOUT);
        }

        if (!mIsDead) {
            if (!mRunInBackground && mHandler != null) {
                mHandler.sendEmptyMessageDelayed(MSG_TIMEOUT, BG_TIMEOUT);
            }
            LifecycleAgent.sServerInfo.rxBytes += bytes.length;
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
                LifecycleAgent.sServerInfo.successCount++;
            } catch (Throwable e) {
                FLog.log(e);
                result = new CallerResult();
                result.setThrowable(e);
                LifecycleAgent.sServerInfo.errorCount++;
            } finally {
                if (result == null) {
                    result = new CallerResult();
                }
                sendOpResult(result);
            }
        }
    }
}
