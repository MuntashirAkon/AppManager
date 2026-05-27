// SPDX-License-Identifier: Apache-2.0

package android.app;

import android.os.Build;
import android.os.IInterface;

import androidx.annotation.RequiresApi;

public interface IProcessObserver extends IInterface {
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // 14.0.0_r50
    void onProcessStarted(int pid, int processUid, int packageUid,
                          String packageName, String processName);

    void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities);

    /**
     * @deprecated Removed in API 26 (Android 8.0)
     */
    @Deprecated
    void onProcessStateChanged(int pid, int uid, int procState);

    @RequiresApi(Build.VERSION_CODES.Q)
    void onForegroundServicesChanged(int pid, int uid, int serviceTypes);

    void onProcessDied(int pid, int uid);
}