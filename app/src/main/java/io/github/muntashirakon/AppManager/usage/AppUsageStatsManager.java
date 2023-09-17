// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.usage.UsageEvents;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.NetworkCapabilities;
import android.net.NetworkStats;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.telephony.SubscriptionInfo;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.collection.SparseArrayCompat;
import androidx.core.util.Pair;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.NetworkStatsCompat;
import io.github.muntashirakon.AppManager.compat.NetworkStatsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.SubscriptionManagerCompat;
import io.github.muntashirakon.AppManager.compat.UsageStatsManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.NonNullUtils;
import io.github.muntashirakon.proc.ProcFs;
import io.github.muntashirakon.proc.ProcUidNetStat;

public class AppUsageStatsManager {
    public static final String TAG = AppUsageStatsManager.class.getSimpleName();

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            TRANSPORT_CELLULAR,
            TRANSPORT_WIFI
    })
    public @interface Transport {
    }

    public static final class DataUsage extends Pair<Long, Long> implements Parcelable, Comparable<DataUsage> {
        public static final DataUsage EMPTY = new DataUsage(0, 0);

        private final long mTotal;

        public DataUsage(long tx, long rx) {
            super(tx, rx);
            mTotal = tx + rx;
        }

        private DataUsage(@NonNull Parcel in) {
            super(in.readLong(), in.readLong());
            mTotal = first + second;
        }

        public static final Creator<DataUsage> CREATOR = new Creator<DataUsage>() {
            @NonNull
            @Override
            public DataUsage createFromParcel(Parcel in) {
                return new DataUsage(in);
            }

            @NonNull
            @Override
            public DataUsage[] newArray(int size) {
                return new DataUsage[size];
            }
        };

        public long getTx() {
            return first;
        }

        public long getRx() {
            return second;
        }

        public long getTotal() {
            return mTotal;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(first);
            dest.writeLong(second);
        }

        @Override
        public int compareTo(@Nullable DataUsage o) {
            if (o == null) return 1;
            return Long.compare(mTotal, o.mTotal);
        }
    }

    public static boolean requireReadPhoneStatePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return !SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.READ_PHONE_STATE);
        }
        return false;
    }

    @SuppressLint("StaticFieldLeak")
    private static AppUsageStatsManager appUsageStatsManager;

    public static AppUsageStatsManager getInstance() {
        if (appUsageStatsManager == null)
            appUsageStatsManager = new AppUsageStatsManager();
        return appUsageStatsManager;
    }

    @NonNull
    private final Context mContext;

    @SuppressLint("WrongConstant")
    private AppUsageStatsManager() {
        mContext = ContextUtils.getContext();
    }

    /**
     * Calculate screen time based on the assumption that no application can be run in the middle of
     * a running application. This is a valid assumption since <code>Activity#onPause()</code> is
     * called whenever an app goes to background and <code>Activity#onResume</code> is called
     * whenever an app appears in foreground.
     *
     * @param usageInterval Usage interval
     * @return A list of package usage
     * @throws SecurityException If usage stats permission is not available for the user
     * @throws RemoteException   If usage stats cannot be retrieved due to transaction error
     */
    @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
    @NonNull
    public List<PackageUsageInfo> getUsageStats(@UsageUtils.IntervalType int usageInterval, @UserIdInt int userId)
            throws RemoteException, SecurityException {
        List<PackageUsageInfo> packageUsageInfoList = new ArrayList<>();
        int _try = 5; // try to get usage stats at most 5 times
        RemoteException re;
        do {
            try {
                packageUsageInfoList.addAll(getUsageStatsInternal(usageInterval, userId));
                re = null;
            } catch (RemoteException e) {
                re = e;
            }
        } while (0 != --_try && packageUsageInfoList.isEmpty());
        if (re != null) {
            throw re;
        }
        return packageUsageInfoList;
    }

    @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
    @NonNull
    public PackageUsageInfo getUsageStatsForPackage(@NonNull String packageName,
                                                    @UsageUtils.IntervalType int usageInterval,
                                                    @UserIdInt int userId)
            throws RemoteException, PackageManager.NameNotFoundException {
        UsageUtils.TimeInterval range = UsageUtils.getTimeInterval(usageInterval);
        ApplicationInfo applicationInfo = PackageManagerCompat.getApplicationInfo(packageName, MATCH_UNINSTALLED_PACKAGES
                | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
        PackageUsageInfo packageUsageInfo = new PackageUsageInfo(mContext, packageName, userId, applicationInfo);
        UsageEvents events = UsageStatsManagerCompat.queryEvents(range.getStartTime(), range.getEndTime(), userId);
        if (events == null) return packageUsageInfo;
        UsageEvents.Event event = new UsageEvents.Event();
        List<PackageUsageInfo.Entry> usEntries = new ArrayList<>();
        long startTime = 0;
        long endTime = 0;
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            String currentPackageName = event.getPackageName();
            int eventType = event.getEventType();
            long eventTime = event.getTimeStamp();
            if (currentPackageName.equals(packageName)) {
                if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    if (startTime == 0) startTime = eventTime;
                } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                    if (startTime > 0) endTime = eventTime;
                }
            } else if (startTime > 0 && endTime > 0) {
                usEntries.add(new PackageUsageInfo.Entry(startTime, endTime));
                startTime = 0;
                endTime = 0;
            }
        }
        packageUsageInfo.entries = usEntries;
        return packageUsageInfo;
    }

    /**
     * Calculate screen time based on the assumption that no application can be run in the middle of
     * a running application. This is a valid assumption since <code>Activity#onPause()</code> is
     * called whenever an app goes to background and <code>Activity#onResume</code> is called
     * whenever an app appears in foreground.
     *
     * @param usageInterval Usage interval
     * @return A list of package usage
     */
    @NonNull
    private List<PackageUsageInfo> getUsageStatsInternal(@UsageUtils.IntervalType int usageInterval,
                                                         @UserIdInt int userId)
            throws RemoteException {
        List<PackageUsageInfo> screenTimeList = new ArrayList<>();
        Map<String, Long> screenTimes = new HashMap<>();
        Map<String, Long> lastUse = new HashMap<>();
        Map<String, Integer> accessCount = new HashMap<>();
        // Get events
        UsageUtils.TimeInterval interval = UsageUtils.getTimeInterval(usageInterval);
        UsageEvents events = UsageStatsManagerCompat.queryEvents(interval.getStartTime(), interval.getEndTime(), userId);
        if (events == null) {
            return Collections.emptyList();
        }
        UsageEvents.Event event = new UsageEvents.Event();
        long startTime;
        long endTime;
        boolean skip_new = false;
        while (events.hasNextEvent()) {
            if (!skip_new) events.getNextEvent(event);
            int eventType = event.getEventType();
            long eventTime = event.getTimeStamp();
            String packageName = event.getPackageName();
            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {  // App opened: MOVE_TO_FOREGROUND
                startTime = eventTime;
                while (events.hasNextEvent()) {
                    events.getNextEvent(event);
                    eventType = event.getEventType();
                    eventTime = event.getTimeStamp();
                    if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        skip_new = true;
                        break;
                    } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                        endTime = eventTime;
                        skip_new = false;
                        if (packageName.equals(event.getPackageName())) {
                            long time = endTime - startTime + 1;
                            if (screenTimes.containsKey(packageName)) {
                                screenTimes.put(packageName, NonNullUtils.defeatNullable(screenTimes
                                        .get(packageName)) + time);
                            } else screenTimes.put(packageName, time);
                            lastUse.put(packageName, endTime);
                            if (accessCount.containsKey(packageName)) {
                                accessCount.put(packageName, NonNullUtils.defeatNullable(accessCount
                                        .get(packageName)) + 1);
                            } else accessCount.put(packageName, 1);
                        }
                        break;
                    }
                }
            }
        }
        SparseArrayCompat<DataUsage> mobileData = new SparseArrayCompat<>();
        SparseArrayCompat<DataUsage> wifiData = new SparseArrayCompat<>();
        mobileData.putAll(getMobileData(interval));
        wifiData.putAll(getWifiData(interval));
        for (String packageName : screenTimes.keySet()) {
            // Skip uninstalled packages?
            ApplicationInfo applicationInfo = ExUtils.exceptionAsNull(() -> PackageManagerCompat
                    .getApplicationInfo(packageName, MATCH_UNINSTALLED_PACKAGES
                            | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId));
            PackageUsageInfo packageUsageInfo = new PackageUsageInfo(mContext, packageName, userId, applicationInfo);
            packageUsageInfo.timesOpened = NonNullUtils.defeatNullable(accessCount.get(packageName));
            packageUsageInfo.lastUsageTime = NonNullUtils.defeatNullable(lastUse.get(packageName));
            packageUsageInfo.screenTime = NonNullUtils.defeatNullable(screenTimes.get(packageName));
            int uid = applicationInfo != null ? applicationInfo.uid : 0;
            if (mobileData.containsKey(uid)) {
                packageUsageInfo.mobileData = mobileData.get(uid);
            } else packageUsageInfo.mobileData = DataUsage.EMPTY;
            if (wifiData.containsKey(uid)) {
                packageUsageInfo.wifiData = wifiData.get(uid);
            } else packageUsageInfo.wifiData = DataUsage.EMPTY;
            screenTimeList.add(packageUsageInfo);
        }
        return screenTimeList;
    }

    @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
    public static long getLastActivityTime(String packageName, @NonNull UsageUtils.TimeInterval interval) {
        try {
            UsageEvents events = UsageStatsManagerCompat.queryEvents(interval.getStartTime(), interval.getEndTime(),
                    UserHandleHidden.myUserId());
            if (events == null) return 0L;
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getPackageName().equals(packageName)) {
                    return event.getTimeStamp();
                }
            }
        } catch (RemoteException ignore) {
        }
        return 0L;
    }

    @NonNull
    private SparseArrayCompat<DataUsage> getMobileData(@NonNull UsageUtils.TimeInterval interval) {
        return getDataUsageForNetwork(TRANSPORT_CELLULAR, interval);
    }


    @NonNull
    private SparseArrayCompat<DataUsage> getWifiData(@NonNull UsageUtils.TimeInterval interval) {
        return getDataUsageForNetwork(TRANSPORT_WIFI, interval);
    }

    @NonNull
    private SparseArrayCompat<DataUsage> getDataUsageForNetwork(@Transport int networkType,
                                                                @NonNull UsageUtils.TimeInterval interval) {
        SparseArrayCompat<DataUsage> dataUsageSparseArray = new SparseArrayCompat<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            @SuppressWarnings("deprecation")
            List<ProcUidNetStat> netStats = ProcFs.getInstance().getAllUidNetStat();
            for (ProcUidNetStat netStat : netStats) {
                dataUsageSparseArray.put(netStat.uid, new DataUsage(netStat.txBytes, netStat.rxBytes));
            }
            return dataUsageSparseArray;
        }
        List<String> subscriberIds = getSubscriberIds(mContext, networkType);
        for (String subscriberId : subscriberIds) {
            try (NetworkStatsCompat networkStats = NetworkStatsManagerCompat.querySummary(networkType, subscriberId,
                    interval.getStartTime(), interval.getEndTime())) {
                while (networkStats.hasNextEntry()) {
                    NetworkStats.Entry entry = networkStats.getNextEntry(true);
                    if (entry == null) {
                        continue;
                    }
                    DataUsage dataUsage = dataUsageSparseArray.get(entry.uid);
                    if (dataUsage != null) {
                        dataUsage = new DataUsage(entry.txBytes + dataUsage.getTx(), entry.rxBytes + dataUsage.getRx());
                    } else {
                        dataUsage = new DataUsage(entry.txBytes, entry.rxBytes);
                    }
                    dataUsageSparseArray.put(entry.uid, dataUsage);
                }
            } catch (RemoteException | SecurityException | IllegalStateException e) {
                e.printStackTrace();
            }
        }

        return dataUsageSparseArray;
    }

    @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
    @NonNull
    public static DataUsage getDataUsageForPackage(@NonNull Context context, int uid,
                                                   @UsageUtils.IntervalType int intervalType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            @SuppressWarnings("deprecation")
            ProcUidNetStat netStat = ProcFs.getInstance().getUidNetStat(uid);
            return netStat != null ? new DataUsage(netStat.txBytes, netStat.rxBytes) : DataUsage.EMPTY;
        }
        UsageUtils.TimeInterval range = UsageUtils.getTimeInterval(intervalType);
        List<String> subscriberIds;
        long totalTx = 0;
        long totalRx = 0;
        for (int networkId = 0; networkId < 2; ++networkId) {
            subscriberIds = getSubscriberIds(context, networkId);
            for (String subscriberId : subscriberIds) {
                try (NetworkStatsCompat networkStats = NetworkStatsManagerCompat.querySummary(networkId, subscriberId,
                        range.getStartTime(), range.getEndTime())) {
                    while (networkStats.hasNextEntry()) {
                        NetworkStats.Entry entry = networkStats.getNextEntry(true);
                        if (entry != null && entry.uid == uid) {
                            totalTx += entry.txBytes;
                            totalRx += entry.rxBytes;
                        }
                    }
                } catch (RemoteException | SecurityException | IllegalStateException e) {
                    e.printStackTrace();
                }
            }
        }
        return new DataUsage(totalTx, totalRx);
    }

    /**
     * @return A list of subscriber IDs if networkType is {@link NetworkCapabilities#TRANSPORT_CELLULAR}, or
     * a singleton array with {@code null} being the only element.
     */
    @SuppressLint({"HardwareIds", "MissingPermission"})
    @RequiresApi(Build.VERSION_CODES.M) // LOLLIPOP_MR1, but we don't need it for API < 23
    @NonNull
    private static List<String> getSubscriberIds(@NonNull Context context, @Transport int networkType) {
        if (networkType != TRANSPORT_CELLULAR) {
            // Unsupported API
            return Collections.singletonList(null);
        }
        PackageManager pm = context.getPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)) {
            Log.i(TAG, "No such feature: %s", PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION);
            return Collections.emptyList();
        } else if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.i(TAG, "No such feature: %s", PackageManager.FEATURE_TELEPHONY);
            return Collections.emptyList();
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.READ_PHONE_STATE)) {
            Log.w(TAG, "Missing required permission: %s", Manifest.permission.READ_PHONE_STATE);
            return Collections.emptyList();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.READ_PRIVILEGED_PHONE_STATE)) {
            Log.w(TAG, "Missing required permission: %s", ManifestCompat.permission.READ_PRIVILEGED_PHONE_STATE);
            return Collections.singletonList(null);
        }
        List<SubscriptionInfo> subscriptionInfoList = SubscriptionManagerCompat.getActiveSubscriptionInfoList();
        if (subscriptionInfoList == null) {
            Log.i(TAG, "No subscriptions found.");
            return Collections.singletonList(null);
        }
        List<String> subscriberIds = new ArrayList<>();
        for (SubscriptionInfo info : subscriptionInfoList) {
            int subscriptionId = info.getSubscriptionId();
            try {
                String subscriberId = SubscriptionManagerCompat.getSubscriberIdForSubscriber(subscriptionId);
                subscriberIds.add(subscriberId);
            } catch (SecurityException ignore) {
            }
        }
        return subscriberIds.isEmpty() ? Collections.singletonList(null) : subscriberIds;
    }
}
