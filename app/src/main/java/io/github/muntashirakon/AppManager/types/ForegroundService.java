// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.types;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class ForegroundService extends Service {
    public class Binder extends android.os.Binder {
        @SuppressWarnings("unchecked")
        public <T extends ForegroundService> T getService() {
            return (T) ForegroundService.this;
        }
    }

    private final String name;
    private final IBinder binder = new Binder();
    @SuppressWarnings("FieldCanBeLocal")
    private Looper serviceLooper;
    private ServiceHandler serviceHandler;

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            onHandleIntent(msg.getData().getParcelable("intent"));
            stopSelf(msg.arg1);
        }
    }

    protected ForegroundService(String name) {
        this.name = name;
    }

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread(name, Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        serviceLooper = thread.getLooper();
        serviceHandler = new ServiceHandler(serviceLooper);
    }

    @CallSuper
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Message msg = serviceHandler.obtainMessage();
        msg.arg1 = startId;
        Bundle args = new Bundle();
        args.putParcelable("intent", intent);
        msg.setData(args);
        serviceHandler.sendMessage(msg);
        return START_NOT_STICKY;
    }

    protected abstract void onHandleIntent(@Nullable Intent intent);

    @NonNull
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}