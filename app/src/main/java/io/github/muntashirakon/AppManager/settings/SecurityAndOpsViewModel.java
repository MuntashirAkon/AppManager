// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.app.Application;
import android.os.Build;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.adb.android.AdbMdns;

public class SecurityAndOpsViewModel extends AndroidViewModel implements Ops.AdbConnectionInterface {
    public static final String TAG = SecurityAndOpsViewModel.class.getSimpleName();

    private boolean mIsAuthenticating = false;
    private final MutableLiveData<Integer> mAuthenticationStatus = new MutableLiveData<>();
    private final MutableLiveData<Integer> mAdbPairingPort = new MutableLiveData<>();
    private final MultithreadedExecutor mExecutor = MultithreadedExecutor.getNewInstance();

    public SecurityAndOpsViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        mExecutor.shutdown();
        super.onCleared();
    }

    public boolean isAuthenticating() {
        return mIsAuthenticating;
    }

    public void setAuthenticating(boolean authenticating) {
        mIsAuthenticating = authenticating;
    }

    public LiveData<Integer> authenticationStatus() {
        return mAuthenticationStatus;
    }

    @AnyThread
    public void setModeOfOps() {
        mExecutor.submit(() -> {
            Log.d(TAG, "Before Ops::init");
            int status = Ops.init(getApplication(), false);
            Log.d(TAG, "After Ops::init");
            mAuthenticationStatus.postValue(status);
        });
    }

    @AnyThread
    @RequiresApi(Build.VERSION_CODES.R)
    public void autoConnectAdb(int returnCodeOnFailure) {
        mExecutor.submit(() -> {
            Log.d(TAG, "Before Ops::autoConnectAdb");
            int status = Ops.autoConnectAdb(getApplication(), returnCodeOnFailure);
            Log.d(TAG, "After Ops::autoConnectAdb");
            mAuthenticationStatus.postValue(status);
        });
    }

    @Override
    @AnyThread
    public void connectAdb(int port) {
        mExecutor.submit(() -> {
            Log.d(TAG, "Before Ops::connectAdb");
            int status = Ops.connectAdb(port, Ops.STATUS_FAILURE);
            Log.d(TAG, "After Ops::connectAdb");
            mAuthenticationStatus.postValue(status);
        });
    }

    @Override
    @AnyThread
    @RequiresApi(Build.VERSION_CODES.R)
    public void pairAdb(@Nullable String pairingCode, int port) {
        mExecutor.submit(() -> {
            Log.d(TAG, "Before Ops::pairAdb");
            int status = Ops.pairAdb(getApplication(), pairingCode, port);
            Log.d(TAG, "After Ops::pairAdb");
            mAuthenticationStatus.postValue(status);
        });
    }

    @Override
    public void onStatusReceived(int status) {
        mAuthenticationStatus.postValue(status);
    }

    private AdbMdns mAdbMdnsPairing;

    @NonNull
    @Override
    public LiveData<Integer> startObservingAdbPairingPort() {
        mExecutor.submit(() -> {
            if (mAdbMdnsPairing == null) {
                mAdbMdnsPairing = new AdbMdns(getApplication(), AdbMdns.SERVICE_TYPE_TLS_PAIRING, (hostAddress, port) -> {
                    if (port != -1) {
                        mAdbPairingPort.postValue(port);
                    }
                });
            }
            mAdbMdnsPairing.start();
        });
        return mAdbPairingPort;
    }

    @Override
    public void stopObservingAdbPairingPort() {
        if (mAdbMdnsPairing != null) {
            mAdbMdnsPairing.stop();
        }
    }
}
