// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class PdfRenderServiceConnection implements AutoCloseable {
    interface Listener {
        void onServiceDied();
    }

    private final Context mContext;
    private final Listener mListener;
    private final Object mLock = new Object();
    @Nullable
    private volatile IPdfRenderService mService;
    private volatile boolean mBound;
    @Nullable
    private CountDownLatch mConnectionLatch;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (mLock) {
                mService = IPdfRenderService.Stub.asInterface(service);
                if (mConnectionLatch != null) mConnectionLatch.countDown();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            handleServiceDeath();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            handleServiceDeath();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            handleServiceDeath();
        }
    };

    PdfRenderServiceConnection(@NonNull Context context, @NonNull Listener listener) {
        mContext = context.getApplicationContext();
        mListener = listener;
    }

    @NonNull
    IPdfRenderService getService() throws Exception {
        CountDownLatch latch;
        synchronized (mLock) {
            IPdfRenderService service = mService;
            if (service != null) {
                return service;
            }
            if (!mBound) {
                mBound = true;
                mConnectionLatch = new CountDownLatch(1);
                Intent intent = new Intent(mContext, PdfRenderService.class);
                if (!mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
                    mBound = false;
                    throw new IllegalStateException("Unable to bind PDF render service");
                }
            }
            latch = mConnectionLatch;
        }
        if (!latch.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out binding PDF render service");
        }
        IPdfRenderService service = mService;
        if (service == null) {
            throw new IllegalStateException("PDF render service unavailable");
        }
        return service;
    }

    @Nullable
    IPdfRenderService getConnectedService() {
        return mService;
    }

    @Override
    public void close() {
        synchronized (mLock) {
            if (mBound) {
                mContext.unbindService(mConnection);
                mBound = false;
            }
            mService = null;
            mConnectionLatch = null;
        }
    }

    private void handleServiceDeath() {
        synchronized (mLock) {
            mService = null;
            if (mConnectionLatch != null) mConnectionLatch.countDown();
        }
        mListener.onServiceDied();
    }
}
