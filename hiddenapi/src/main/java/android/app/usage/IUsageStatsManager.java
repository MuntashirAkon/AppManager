// SPDX-License-Identifier: Apache-2.0

package android.app.usage;

import android.app.PendingIntent;
import android.content.pm.ParceledListSlice;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.PersistableBundle;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

public interface IUsageStatsManager extends IInterface {
    abstract class Stub extends Binder implements IUsageStatsManager {
        public Stub() {
        }

        public static IUsageStatsManager asInterface(IBinder obj) {
            return HiddenUtil.throwUOE(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }

    /**
     * @deprecated Replaced in API 31 (Android S) by {@link #queryUsageStats(int, long, long, String, int)}
     */
    @Deprecated
    ParceledListSlice queryUsageStats(int bucketType, long beginTime, long endTime, String callingPackage)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.S)
    ParceledListSlice queryUsageStats(int bucketType, long beginTime, long endTime,
                                      String callingPackage, int userId) throws RemoteException;

    ParceledListSlice queryConfigurationStats(int bucketType, long beginTime, long endTime, String callingPackage)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    ParceledListSlice queryEventStats(int bucketType, long beginTime, long endTime, String callingPackage)
            throws RemoteException;

    UsageEvents queryEvents(long beginTime, long endTime, String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    UsageEvents queryEventsForPackage(long beginTime, long endTime, String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    UsageEvents queryEventsForUser(long beginTime, long endTime, int userId, String callingPackage)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    UsageEvents queryEventsForPackageForUser(long beginTime, long endTime, int userId, String pkg,
                                             String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // Android 14 r50
    UsageEvents queryEventsWithFilter(UsageEventsQuery query, String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    void setAppInactive(String packageName, boolean inactive, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    boolean isAppStandbyEnabled() throws RemoteException;

    /**
     * @deprecated Replaced in API 30 (Android R) by {@link #isAppInactive(String, int, String)}
     */
    @RequiresApi(Build.VERSION_CODES.M)
    @Deprecated
    boolean isAppInactive(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.R)
    boolean isAppInactive(String packageName, int userId, String callingPackage) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void whitelistAppTemporarily(String packageName, long duration, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.N)
    void onCarrierPrivilegedAppsChanged() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    void reportChooserSelection(String packageName, int userId, String contentType, String[] annotations, String action)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    int getAppStandbyBucket(String packageName, String callingPackage, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    void setAppStandbyBucket(String packageName, int bucket, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    ParceledListSlice getAppStandbyBuckets(String callingPackage, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    void setAppStandbyBuckets(ParceledListSlice appBuckets, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    int getAppMinStandbyBucket(String packageName, String callingPackage, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    void setEstimatedLaunchTime(String packageName, long estimatedLaunchTime, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    void setEstimatedLaunchTimes(ParceledListSlice appLaunchTimes, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    void registerAppUsageObserver(int observerId, String[] packages, long timeLimitMs, PendingIntent callback,
                                  String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    void unregisterAppUsageObserver(int observerId, String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    void registerUsageSessionObserver(int sessionObserverId, String[] observed, long timeLimitMs,
                                      long sessionThresholdTimeMs, PendingIntent limitReachedCallbackIntent,
                                      PendingIntent sessionEndCallbackIntent, String callingPackage)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    void unregisterUsageSessionObserver(int sessionObserverId, String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    void registerAppUsageLimitObserver(int observerId, String[] packages, long timeLimitMs, long timeUsedMs,
                                       PendingIntent callback, String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    void unregisterAppUsageLimitObserver(int observerId, String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    void reportUsageStart(IBinder activity, String token, String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    void reportPastUsageStart(IBinder activity, String token, long timeAgoMs, String callingPackage)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    void reportUsageStop(IBinder activity, String token, String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.S)
    void reportUserInteraction(String packageName, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // Android 14 r50
    void reportUserInteractionWithBundle(String packageName, int userId, PersistableBundle eventExtras) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    int getUsageSource() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    void forceUsageSourceSettingRead() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.S)
    long getLastTimeAnyComponentUsed(String packageName, String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    String getAppStandbyConstant(String key) throws RemoteException;
}
