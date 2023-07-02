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
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.os.BundleCompat;

import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public abstract class ForegroundService extends Service {
    public static class Binder extends android.os.Binder {
        private final ForegroundService mService;

        private Binder(ForegroundService service) {
            mService = service;
        }

        @SuppressWarnings("unchecked")
        public <T extends ForegroundService> T getService() {
            return (T) mService;
        }
    }

    private final String mName;
    private final IBinder mBinder = new Binder(this);
    @SuppressWarnings("FieldCanBeLocal")
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    private volatile boolean mIsWorking = false;

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Intent intent = BundleCompat.getParcelable(msg.getData(), "intent", Intent.class);
            ThreadUtils.postOnMainThread(() -> onStartIntent(intent));
            onHandleIntent(intent);
            // It works because of Handler uses FIFO
            stopSelfResult(msg.arg1);
        }
    }

    protected ForegroundService(String name) {
        mName = name;
    }

    public final boolean isWorking() {
        return mIsWorking;
    }

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread(mName, Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @CallSuper
    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        // TODO: 15/6/21 Make it final, extended classes shouldn't need to use it
        if (mIsWorking) {
            // Service already running
            onQueued(intent);
        }
        mIsWorking = true;
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        Bundle args = new Bundle();
        args.putParcelable("intent", intent);
        msg.setData(args);
        mServiceHandler.sendMessage(msg);
        return START_NOT_STICKY;
    }

    @CallSuper
    @Override
    public void onDestroy() {
        super.onDestroy();
        mServiceLooper.quitSafely();
    }

    /**
     * The work to be performed.
     *
     * @param intent The intent sent by {@link android.content.Context#startService(Intent)}
     */
    @WorkerThread
    protected abstract void onHandleIntent(@Nullable Intent intent);

    /**
     * The service is running and a new intent has been queued.
     *
     * @param intent The new intent that has been queued
     */
    @UiThread
    protected void onQueued(@Nullable Intent intent) {
    }

    /**
     * An intent is being processed. Called right before {@link #onHandleIntent(Intent)}.
     *
     * @param intent The intent to be processed.
     */
    @UiThread
    protected void onStartIntent(@Nullable Intent intent) {
    }

    @NonNull
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}