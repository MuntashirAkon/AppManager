// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.app.Application;
import android.os.Build;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.Migrations;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;

public class SecurityAndOpsViewModel extends AndroidViewModel implements Ops.AdbConnectionInterface {
    public static final String TAG = SecurityAndOpsViewModel.class.getSimpleName();

    private boolean mIsAuthenticating = false;
    private final MutableLiveData<Integer> mAuthenticationStatus = new MutableLiveData<>();
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
            // Migration
            long thisVersion = BuildConfig.VERSION_CODE;
            long lastVersion = AppPref.getLong(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG);
            if (lastVersion == 0) {
                // First version: set this as the last version
                AppPref.set(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG, (long) BuildConfig.VERSION_CODE);
                AppPref.set(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_LAST_VERSION_LONG, (long) BuildConfig.VERSION_CODE);
            }
            if (lastVersion < thisVersion) {
                Log.d(TAG, "Start migration");
                // App is updated
                AppPref.set(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_BOOL, true);
                Migrations.startMigration(lastVersion, thisVersion);
                // Migration is done: set this as the last version
                AppPref.set(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG, (long) BuildConfig.VERSION_CODE);
                Log.d(TAG, "End migration");
            }
            // Ops
            Log.d(TAG, "Before Ops::init");
            int status = Ops.init(getApplication(), false);
            Log.d(TAG, "After Ops::init");
            mAuthenticationStatus.postValue(status);
        });
    }

    @AnyThread
    @RequiresApi(Build.VERSION_CODES.R)
    public void autoConnectWirelessDebugging() {
        mExecutor.submit(() -> {
            Log.d(TAG, "Before Ops::autoConnectWirelessDebugging");
            int status = Ops.autoConnectWirelessDebugging(getApplication());
            Log.d(TAG, "After Ops::autoConnectWirelessDebugging");
            mAuthenticationStatus.postValue(status);
        });
    }

    @Override
    @AnyThread
    public void connectAdb(int port) {
        mExecutor.submit(() -> {
            Log.d(TAG, "Before Ops::connectAdb");
            int status = Ops.connectAdb(getApplication(), port, Ops.STATUS_FAILURE);
            Log.d(TAG, "After Ops::connectAdb");
            mAuthenticationStatus.postValue(status);
        });
    }

    @Override
    @AnyThread
    @RequiresApi(Build.VERSION_CODES.R)
    public void pairAdb() {
        mExecutor.submit(() -> {
            Log.d(TAG, "Before Ops::pairAdb");
            int status = Ops.pairAdb(getApplication());
            Log.d(TAG, "After Ops::pairAdb");
            mAuthenticationStatus.postValue(status);
        });
    }

    @Override
    public void onStatusReceived(int status) {
        mAuthenticationStatus.postValue(status);
    }
}
