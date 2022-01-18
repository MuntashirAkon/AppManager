// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import android.app.ActivityManager;
import android.content.pm.ServiceInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AppDetailsServiceItem extends AppDetailsComponentItem {
    @Nullable
    private ActivityManager.RunningServiceInfo mRunningServiceInfo;

    public AppDetailsServiceItem(@NonNull ServiceInfo serviceInfo) {
        super(serviceInfo);
    }

    public void setRunningServiceInfo(@Nullable ActivityManager.RunningServiceInfo runningServiceInfo) {
        mRunningServiceInfo = runningServiceInfo;
    }

    @Nullable
    public ActivityManager.RunningServiceInfo getRunningServiceInfo() {
        return mRunningServiceInfo;
    }

    public boolean isRunning() {
        return mRunningServiceInfo != null;
    }
}
