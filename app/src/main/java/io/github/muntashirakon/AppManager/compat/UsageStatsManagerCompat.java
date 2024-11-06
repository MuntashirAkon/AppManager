// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.annotation.UserIdInt;
import android.app.usage.IUsageStatsManager;
import android.app.usage.UsageEvents;
import android.content.Context;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.BroadcastUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;

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

    @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
    @Nullable
    public static UsageEvents queryEvents(long beginTime, long endTime, int userId) {
        try {
            IUsageStatsManager usm = getUsageStatsManager();
            String callingPackage = SelfPermissions.getCallingPackage(Users.getSelfOrRemoteUid());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return usm.queryEventsForUser(beginTime, endTime, userId, callingPackage);
            }
            return usm.queryEvents(beginTime, endTime, callingPackage);
        } catch (RemoteException e) {
            return ExUtils.rethrowFromSystemServer(e);
        }
    }

    /**
     * Note: This method should only be used when sorted entries are required as the operations done
     * here are expensive.
     */
    @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
    @NonNull
    public static List<UsageEvents.Event> queryEventsSorted(long beginTime, long endTime, int userId, int[] filterEvents) {
        List<UsageEvents.Event> filteredEvents = new ArrayList<>();
        UsageEvents events = queryEvents(beginTime, endTime, userId);
        if (events != null) {
            while (events.hasNextEvent()) {
                UsageEvents.Event event = new UsageEvents.Event();
                events.getNextEvent(event);
                if (ArrayUtils.contains(filterEvents, event.getEventType())) {
                    filteredEvents.add(event);
                }
            }
            Collections.sort(filteredEvents, (o1, o2) -> -Long.compare(o1.getTimeStamp(), o2.getTimeStamp()));
        }
        return filteredEvents;
    }

    public static void setAppInactive(String packageName, @UserIdInt int userId, boolean inactive) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                getUsageStatsManager().setAppInactive(packageName, inactive, userId);
                if (userId != UserHandleHidden.myUserId()) {
                    BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), new String[]{packageName});
                }
            } catch (RemoteException e) {
                ExUtils.rethrowFromSystemServer(e);
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean isAppInactive(String packageName, @UserIdInt int userId) {
        IUsageStatsManager usm = getUsageStatsManager();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                String callingPackage = SelfPermissions.getCallingPackage(Users.getSelfOrRemoteUid());
                return usm.isAppInactive(packageName, userId, callingPackage);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return usm.isAppInactive(packageName, userId);
            }
        } catch (RemoteException e) {
            return ExUtils.rethrowFromSystemServer(e);
        }
        // Unsupported Android version: return false
        return false;
    }

    public static IUsageStatsManager getUsageStatsManager() {
        return IUsageStatsManager.Stub.asInterface(ProxyBinder.getService(USAGE_STATS_SERVICE_NAME));
    }
}
