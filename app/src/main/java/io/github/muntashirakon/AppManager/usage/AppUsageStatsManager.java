// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.app.usage.UsageEvents;
import android.content.Context;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManagerHidden;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.collection.SparseArrayCompat;
import androidx.core.util.Pair;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.rikka.tools.refine.Refine;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.servermanager.UsageStatsManagerCompat;
import io.github.muntashirakon.AppManager.utils.NonNullUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

public class AppUsageStatsManager {
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

    @SuppressLint("StaticFieldLeak")
    private static AppUsageStatsManager appUsageStatsManager;

    public static AppUsageStatsManager getInstance(@NonNull Context context) {
        if (appUsageStatsManager == null)
            appUsageStatsManager = new AppUsageStatsManager(context.getApplicationContext());
        return appUsageStatsManager;
    }

    @NonNull
    private final Context context;

    @SuppressLint("WrongConstant")
    private AppUsageStatsManager(@NonNull Context context) {
        this.context = context;
    }

    public PackageUsageInfo getUsageStatsForPackage(@NonNull String packageName,
                                                    @UsageUtils.IntervalType int usageInterval,
                                                    @UserIdInt int userId)
            throws RemoteException {
        UsageUtils.TimeInterval range = UsageUtils.getTimeInterval(usageInterval);
        PackageUsageInfo packageUsageInfo = new PackageUsageInfo(context, packageName, userId,
                PackageUtils.getApplicationInfo(packageName, userId));
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
    public List<PackageUsageInfo> getUsageStats(@UsageUtils.IntervalType int usageInterval, @UserIdInt int userId)
            throws RemoteException {
        List<PackageUsageInfo> screenTimeList = new ArrayList<>();
        Map<String, Long> screenTimes = new HashMap<>();
        Map<String, Long> lastUse = new HashMap<>();
        Map<String, Integer> accessCount = new HashMap<>();
        // Get events
        UsageUtils.TimeInterval interval = UsageUtils.getTimeInterval(usageInterval);
        UsageEvents events = UsageStatsManagerCompat.queryEvents(interval.getStartTime(), interval.getEndTime(), userId);
        if (events == null) return Collections.emptyList();
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // FIXME: 18/9/21 Get data usage for other users
            NetworkStatsManager nsm = (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
            try {
                mobileData.putAll(getMobileData(nsm, usageInterval));
            } catch (Exception ignore) {
            }
            try {
                wifiData.putAll(getWifiData(nsm, usageInterval));
            } catch (Exception ignore) {
            }
        }
        for (String packageName : screenTimes.keySet()) {
            // Skip uninstalled packages?
            PackageUsageInfo packageUsageInfo = new PackageUsageInfo(context, packageName, userId,
                    PackageUtils.getApplicationInfo(packageName, userId));
            packageUsageInfo.timesOpened = NonNullUtils.defeatNullable(accessCount.get(packageName));
            packageUsageInfo.lastUsageTime = NonNullUtils.defeatNullable(lastUse.get(packageName));
            packageUsageInfo.screenTime = NonNullUtils.defeatNullable(screenTimes.get(packageName));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int uid = PackageUtils.getAppUid(packageUsageInfo.applicationInfo);
                if (mobileData.containsKey(uid)) {
                    packageUsageInfo.mobileData = mobileData.get(uid);
                } else packageUsageInfo.mobileData = DataUsage.EMPTY;
                if (wifiData.containsKey(uid)) {
                    packageUsageInfo.wifiData = wifiData.get(uid);
                } else packageUsageInfo.wifiData = DataUsage.EMPTY;
            }
            screenTimeList.add(packageUsageInfo);
        }
        return screenTimeList;
    }

    public static long getLastActivityTime(String packageName, @NonNull UsageUtils.TimeInterval interval) {
        try {
            UsageEvents events = UsageStatsManagerCompat.getUsageStatsManager().queryEvents(interval.getStartTime(),
                    interval.getEndTime(), AppManager.getContext().getPackageName());
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

    @RequiresApi(Build.VERSION_CODES.M)
    @NonNull
    private SparseArrayCompat<DataUsage> getMobileData(@NonNull NetworkStatsManager nsm,
                                                       @UsageUtils.IntervalType int intervalType) {
        return getDataUsageForNetwork(nsm, TRANSPORT_CELLULAR, intervalType);
    }


    @RequiresApi(Build.VERSION_CODES.M)
    @NonNull
    private SparseArrayCompat<DataUsage> getWifiData(@NonNull NetworkStatsManager nsm,
                                                     @UsageUtils.IntervalType int intervalType) {
        return getDataUsageForNetwork(nsm, TRANSPORT_WIFI, intervalType);
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @NonNull
    private SparseArrayCompat<DataUsage> getDataUsageForNetwork(@NonNull NetworkStatsManager nsm,
                                                                @Transport int networkType,
                                                                @UsageUtils.IntervalType int intervalType) {
        SparseArrayCompat<DataUsage> dataUsageSparseArray = new SparseArrayCompat<>();
        UsageUtils.TimeInterval range = UsageUtils.getTimeInterval(intervalType);
        List<String> subscriberIds = getSubscriberIds(context, networkType);
        NetworkStats.Bucket bucket = new NetworkStats.Bucket();
        NetworkStats networkStats;
        try {
            for (String subscriberId : subscriberIds) {
                networkStats = nsm.querySummary(networkType, subscriberId, range.getStartTime(), range.getEndTime());
                if (networkStats != null) {
                    while (networkStats.hasNextBucket()) {
                        networkStats.getNextBucket(bucket);
                        DataUsage dataUsage = dataUsageSparseArray.get(bucket.getUid());
                        if (dataUsage != null) {
                            dataUsage = new DataUsage(bucket.getTxBytes() + dataUsage.getTx(),
                                    bucket.getRxBytes() + dataUsage.getRx());
                        } else {
                            dataUsage = new DataUsage(bucket.getTxBytes(), bucket.getRxBytes());
                        }
                        dataUsageSparseArray.put(bucket.getUid(), dataUsage);
                    }
                }
            }
        } catch (RemoteException e) {
            Log.e("AppUsage", e);
        }
        return dataUsageSparseArray;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @NonNull
    public static DataUsage getDataUsageForPackage(@NonNull Context context, int uid,
                                                   @UsageUtils.IntervalType int intervalType) {
        NetworkStatsManager nsm = (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
        if (nsm == null) return DataUsage.EMPTY;
        UsageUtils.TimeInterval range = UsageUtils.getTimeInterval(intervalType);
        List<String> subscriberIds;
        NetworkStats.Bucket bucket = new NetworkStats.Bucket();
        long totalTx = 0;
        long totalRx = 0;
        for (int networkId = 0; networkId < 2; ++networkId) {
            subscriberIds = getSubscriberIds(context, networkId);
            for (String subscriberId : subscriberIds) {
                try {
                    NetworkStats networkStats = nsm.querySummary(networkId, subscriberId,
                            range.getStartTime(), range.getEndTime());
                    if (networkStats == null) continue;
                    while (networkStats.hasNextBucket()) {
                        networkStats.getNextBucket(bucket);
                        if (bucket.getUid() == uid) {
                            totalTx += bucket.getTxBytes();
                            totalRx += bucket.getRxBytes();
                        }
                    }
                } catch (RemoteException | IllegalStateException ignore) {
                }
            }
        }
        return new DataUsage(totalTx, totalRx);
    }

    /**
     * @return A list of subscriber IDs if networkType is {@link android.net.NetworkCapabilities#TRANSPORT_CELLULAR}, or
     * a singleton array with {@code null} being the only element.
     * @deprecated Requires {@code android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE} from Android 10 (API 29)
     */
    @SuppressLint({"HardwareIds", "MissingPermission"})
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    @Deprecated
    @NonNull
    private static List<String> getSubscriberIds(@NonNull Context context, @Transport int networkType) {
        if (networkType != TRANSPORT_CELLULAR || !PermissionUtils.hasPermission(context,
                Manifest.permission.READ_PHONE_STATE)) {
            return Collections.singletonList(null);
        }
        // FIXME: 24/4/21 Consider using Binder to fetch subscriber info
        try {
            SubscriptionManager sm = (SubscriptionManager) context.getSystemService(Context
                    .TELEPHONY_SUBSCRIPTION_SERVICE);
            TelephonyManager tm = (TelephonyManager) Objects.requireNonNull(context.getSystemService(Context
                    .TELEPHONY_SERVICE));

            List<SubscriptionInfo> subscriptionInfoList = sm.getActiveSubscriptionInfoList();
            if (subscriptionInfoList == null) {
                // No telephony services
                return Collections.singletonList(null);
            }
            List<String> subscriberIds = new ArrayList<>();
            for (SubscriptionInfo info : subscriptionInfoList) {
                int subscriptionId = info.getSubscriptionId();
                try {
                    String subscriberId = Refine.<TelephonyManagerHidden>unsafeCast(tm).getSubscriberId(subscriptionId);
                    subscriberIds.add(subscriberId);
                } catch (Exception e) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            subscriberIds.add(tm.createForSubscriptionId(subscriptionId).getSubscriberId());
                        }
                    } catch (Exception e2) {
                        subscriberIds.add(tm.getSubscriberId());
                    }
                }
            }
            return subscriberIds.size() == 0 ? Collections.singletonList(null) : subscriberIds;
        } catch (SecurityException e) {
            return Collections.singletonList(null);
        }
    }
}
