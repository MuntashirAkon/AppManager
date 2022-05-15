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

public class SecurityAndOpsViewModel extends AndroidViewModel implements Ops.AdbConnectionInterface {
    public static final String TAG = SecurityAndOpsViewModel.class.getSimpleName();

    private boolean mIsAuthenticating = false;
    private final MutableLiveData<Integer> mAuthenticationStatus = new MutableLiveData<>();
    private final MultithreadedExecutor executor = MultithreadedExecutor.getNewInstance();

    public SecurityAndOpsViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        executor.shutdown();
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
        executor.submit(() -> {
            Log.d(TAG, "Before Ops::init");
            int status = Ops.init(getApplication(), false);
            Log.d(TAG, "After Ops::init");
            mAuthenticationStatus.postValue(status);
        });
    }

    @AnyThread
    @RequiresApi(Build.VERSION_CODES.R)
    public void autoConnectAdb(int returnCodeOnFailure) {
        executor.submit(() -> {
            Log.d(TAG, "Before Ops::autoConnectAdb");
            int status = Ops.autoConnectAdb(getApplication(), returnCodeOnFailure);
            Log.d(TAG, "After Ops::autoConnectAdb");
            mAuthenticationStatus.postValue(status);
        });
    }

    @Override
    @AnyThread
    public void connectAdb(int port) {
        executor.submit(() -> {
            Log.d(TAG, "Before Ops::connectAdb");
            int status = Ops.connectAdb(port, Ops.STATUS_FAILED);
            Log.d(TAG, "After Ops::connectAdb");
            mAuthenticationStatus.postValue(status);
        });
    }

    @Override
    @AnyThread
    @RequiresApi(Build.VERSION_CODES.R)
    public void pairAdb(@Nullable String pairingCode, int port) {
        executor.submit(() -> {
            Log.d(TAG, "Before Ops::pairAdb");
            int status = Ops.pairAdb(getApplication(), pairingCode, port);
            Log.d(TAG, "After Ops::pairAdb");
            mAuthenticationStatus.postValue(status);
        });
    }
}
