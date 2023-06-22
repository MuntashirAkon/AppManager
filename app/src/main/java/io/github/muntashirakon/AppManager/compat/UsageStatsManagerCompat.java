// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.annotation.UserIdInt;
import android.app.usage.IUsageStatsManager;
import android.app.usage.UsageEvents;
import android.content.Context;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.BroadcastUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

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
        String callingPackage = SelfPermissions.getCallingPackage(Users.getSelfOrRemoteUid());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return usm.queryEventsForUser(beginTime, endTime, userId, callingPackage);
        }
        return usm.queryEvents(beginTime, endTime, callingPackage);
    }

    public static void setAppInactive(String packageName, @UserIdInt int userId, boolean inactive)
            throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getUsageStatsManager().setAppInactive(packageName, inactive, userId);
            if (userId != UserHandleHidden.myUserId()) {
                BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), new String[]{packageName});
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean isAppInactive(String packageName, @UserIdInt int userId) throws RemoteException {
        IUsageStatsManager usm = getUsageStatsManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            String callingPackage = SelfPermissions.getCallingPackage(Users.getSelfOrRemoteUid());
            return usm.isAppInactive(packageName, userId, callingPackage);
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
