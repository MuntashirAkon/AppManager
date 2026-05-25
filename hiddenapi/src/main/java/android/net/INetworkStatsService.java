// SPDX-License-Identifier: Apache-2.0

package android.net;

import android.net.netstats.StatsResult;
import android.os.Build;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

public interface INetworkStatsService extends IInterface {
    /**
     * Start a statistics query session. In Android Lollipop, this requires the permission
     * {@code android.permission.READ_NETWORK_USAGE_HISTORY}
     */
    INetworkStatsSession openSession() throws RemoteException;

    /**
     * Start a statistics query session. If calling package is profile or device owner then it is
     * granted automatic access if apiLevel is NetworkStatsManager.API_LEVEL_DPC_ALLOWED. If
     * apiLevel is at least NetworkStatsManager.API_LEVEL_REQUIRES_PACKAGE_USAGE_STATS then
     * PACKAGE_USAGE_STATS permission is always checked. If PACKAGE_USAGE_STATS is not granted
     * READ_NETWORK_USAGE_STATS is checked for.
     *
     * @deprecated Replaced in SDK 27 (Android O_MR1) with {@link #openSessionForUsageStats(int, String)}
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    INetworkStatsSession openSessionForUsageStats(String callingPackage) throws RemoteException;

    /**
     * Start a statistics query session. If calling package is profile or device owner then it is
     * granted automatic access if apiLevel is NetworkStatsManager.API_LEVEL_DPC_ALLOWED. If
     * apiLevel is at least NetworkStatsManager.API_LEVEL_REQUIRES_PACKAGE_USAGE_STATS then
     * PACKAGE_USAGE_STATS permission is always checked. If PACKAGE_USAGE_STATS is not granted
     * READ_NETWORK_USAGE_STATS is checked for.
     */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    INetworkStatsSession openSessionForUsageStats(int flags, String callingPackage) throws RemoteException;

    /**
     * Get the uid stats information since boot
     *
     * @deprecated Replaced in API 35 (Android 15 r20) with {@link #getUidStats(int)}
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.P)
    long getUidStats(int uid, int type) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM) // 15 r20
    StatsResult getUidStats(int uid) throws RemoteException;

    abstract class Stub {
        public static INetworkStatsService asInterface(android.os.IBinder obj) {
            return HiddenUtil.throwUOE(obj);
        }
    }
}
