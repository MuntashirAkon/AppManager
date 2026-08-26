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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.Migrations;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class SecurityAndOpsViewModel extends AndroidViewModel implements Ops.AdbConnectionInterface {
    public static final String TAG = SecurityAndOpsViewModel.class.getSimpleName();

    private boolean mIsAuthenticating = false;
    private final MutableLiveData<Integer> mAuthenticationStatus = new MutableLiveData<>();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean mOperationPending = new AtomicBoolean(false);

    public SecurityAndOpsViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        mExecutor.shutdownNow();
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
        submitStatusOperation("Ops::init", () -> {
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
                Migrations.startMigration(lastVersion);
                // Migration is done: set this as the last version
                AppPref.set(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG, (long) BuildConfig.VERSION_CODE);
                Log.d(TAG, "End migration");
            }
            // Ops
            Log.d(TAG, "Before Ops::init");
            int status = Ops.init(getApplication(), false);
            Log.d(TAG, "After Ops::init");
            return status;
        });
    }

    @AnyThread
    @RequiresApi(Build.VERSION_CODES.R)
    public void autoConnectWirelessDebugging() {
        submitStatusOperation("Ops::autoConnectWirelessDebugging", () -> {
            Log.d(TAG, "Before Ops::autoConnectWirelessDebugging");
            int status = Ops.autoConnectWirelessDebugging(getApplication());
            Log.d(TAG, "After Ops::autoConnectWirelessDebugging");
            return status;
        });
    }

    @Override
    @AnyThread
    public void connectAdb(int port) {
        submitStatusOperation("Ops::connectAdb", () -> {
            Log.d(TAG, "Before Ops::connectAdb");
            int status = Ops.connectAdb(getApplication(), port, Ops.STATUS_FAILURE);
            Log.d(TAG, "After Ops::connectAdb");
            return status;
        });
    }

    @Override
    @AnyThread
    @RequiresApi(Build.VERSION_CODES.R)
    public void pairAdb() {
        submitStatusOperation("Ops::pairAdb", () -> {
            Log.d(TAG, "Before Ops::pairAdb");
            int status = Ops.pairAdb(getApplication());
            Log.d(TAG, "After Ops::pairAdb");
            return status;
        });
    }

    @Override
    public void onStatusReceived(int status) {
        ThreadUtils.postOnMainThread(() -> mAuthenticationStatus.setValue(status));
    }

    @AnyThread
    private void submitStatusOperation(@NonNull String name, @NonNull StatusOperation operation) {
        if (!mOperationPending.compareAndSet(false, true)) {
            Log.w(TAG, "Ignoring duplicate operation while another is pending: %s", name);
            return;
        }
        try {
            mExecutor.submit(() -> {
                int status;
                try {
                    status = operation.run();
                } catch (Throwable e) {
                    Log.e(TAG, "Unexpected failure in " + name, e);
                    try {
                        Ops.fallbackToNoRoot(getApplication());
                    } catch (Throwable cleanupError) {
                        Log.e(TAG, "Could not fall back to no-root mode", cleanupError);
                    }
                    status = Ops.STATUS_FAILURE;
                }
                int finalStatus = status;
                // Keep the operation marked pending until the new state reaches the main thread.
                // This closes the rotation window where LiveData could replay the previous action.
                ThreadUtils.postOnMainThread(() -> {
                    mOperationPending.set(false);
                    mAuthenticationStatus.setValue(finalStatus);
                });
            });
        } catch (RejectedExecutionException e) {
            mOperationPending.set(false);
            Log.w(TAG, "Ignoring operation after ViewModel was cleared: %s", name);
        }
    }

    private interface StatusOperation {
        @Ops.Status
        int run();
    }
}
