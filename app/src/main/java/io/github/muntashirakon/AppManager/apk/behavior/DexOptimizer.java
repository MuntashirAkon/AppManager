// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import android.content.pm.IPackageManager;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.N)
public class DexOptimizer {
    @NonNull
    private final IPackageManager mPm;
    private final String mPackageName;

    @Nullable
    private Exception lastError;

    public DexOptimizer(@NonNull IPackageManager pm, @NonNull String packageName) {
        mPm = pm;
        mPackageName = packageName;
    }

    @Nullable
    public Exception getLastError() {
        try {
            return lastError;
        } finally {
            lastError = null;
        }
    }

    @SuppressWarnings("deprecation")
    public boolean performDexOptMode(boolean checkProfiles, @NonNull String targetCompilerFilter, boolean force,
                                     boolean bootComplete, @Nullable String splitName) {
        try {
            // Allowed for root/system/shell and installer app
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                return mPm.performDexOptMode(mPackageName, checkProfiles, targetCompilerFilter, force, bootComplete, splitName);
            } else {
                return mPm.performDexOptMode(mPackageName, checkProfiles, targetCompilerFilter, force);
            }
        } catch (RemoteException | SecurityException e) {
            lastError = e;
        }
        return false;
    }

    public boolean clearApplicationProfileData() {
        try {
            // Allowed for only root/system
            mPm.clearApplicationProfileData(mPackageName);
            return true;
        } catch (RemoteException | SecurityException e) {
            lastError = e;
        }
        return false;
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    public boolean compileLayouts() {
        try {
            return mPm.compileLayouts(mPackageName);
        } catch (RemoteException | SecurityException e) {
            lastError = e;
        }
        return false;
    }

    public boolean forceDexOpt() {
        try {
            // Allowed for only root/system
            mPm.forceDexOpt(mPackageName);
            return true;
        } catch (RemoteException | SecurityException e) {
            lastError = e;
        }
        return false;
    }
}
