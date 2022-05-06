// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.annotation.UserIdInt;
import android.app.usage.IUsageStatsManager;
import android.app.usage.UsageEvents;
import android.content.Context;
import android.os.Build;
import android.os.RemoteException;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;

public final class UsageStatsManagerCompat {
    private static final String SYS_USAGE_STATS_SERVICE = "usagestats";

    private static final String USAGE_STATS_SERVICE_NAME;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            USAGE_STATS_SERVICE_NAME = Context.USAGE_STATS_SERVICE;
        } else {
            USAGE_STATS_SERVICE_NAME = SYS_USAGE_STATS_SERVICE;
        }
    }

    public static UsageEvents queryEvents(long beginTime, long endTime, int userId) throws RemoteException {
        IUsageStatsManager usm = getUsageStatsManager();
        String callingPackage = AppManager.getContext().getPackageName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return usm.queryEventsForUser(beginTime, endTime, userId, callingPackage);
        }
        return usm.queryEvents(beginTime, endTime, callingPackage);
    }

    public static void setAppInactive(String packageName, @UserIdInt int userId, boolean inactive)
            throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getUsageStatsManager().setAppInactive(packageName, inactive, userId);
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean isAppInactive(String packageName, @UserIdInt int userId) throws RemoteException {
        IUsageStatsManager usm = getUsageStatsManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return usm.isAppInactive(packageName, userId, AppManager.getContext().getPackageName());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return usm.isAppInactive(packageName, userId);
        }
        // Unsupported Android version: return false
        return false;
    }

    public static IUsageStatsManager getUsageStatsManager() {
        return IUsageStatsManager.Stub.asInterface(ProxyBinder.getService(USAGE_STATS_SERVICE_NAME));
    }
}
