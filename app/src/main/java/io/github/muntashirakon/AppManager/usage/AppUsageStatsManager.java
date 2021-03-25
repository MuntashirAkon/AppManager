/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.usage;

import android.annotation.SuppressLint;
import android.app.usage.IUsageStatsManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.app.usage.UsageEvents;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
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

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.NonNullUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

public class AppUsageStatsManager {
    private static final String SYS_USAGE_STATS_SERVICE = "usagestats";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            TRANSPORT_CELLULAR,
            TRANSPORT_WIFI
    })
    public @interface Transport {
    }

    public static final class DataUsage extends Pair<Long, Long> {
        public DataUsage(long tx, long rx) {
            super(tx, rx);
        }

        public long getTx() {
            return first;
        }

        public long getRx() {
            return second;
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
    private final IUsageStatsManager mUsageStatsManager;
    @NonNull
    private final Context context;
    private final PackageManager mPackageManager;

    @SuppressLint("WrongConstant")
    private AppUsageStatsManager(@NonNull Context context) {
        this.context = context;
        this.mPackageManager = context.getPackageManager();
        String usageStatsServiceName;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            usageStatsServiceName = Context.USAGE_STATS_SERVICE;
        } else {
            usageStatsServiceName = SYS_USAGE_STATS_SERVICE;
        }
        this.mUsageStatsManager = IUsageStatsManager.Stub.asInterface(ProxyBinder.getService(usageStatsServiceName));
    }

    public PackageUsageInfo getUsageStatsForPackage(@NonNull String packageName, @UsageUtils.IntervalType int usage_interval)
            throws RemoteException {
        UsageUtils.TimeInterval range = UsageUtils.getTimeInterval(usage_interval);
        PackageUsageInfo packageUsageInfo = new PackageUsageInfo(packageName);
        packageUsageInfo.appLabel = PackageUtils.getPackageLabel(mPackageManager, packageName);
        UsageEvents events = mUsageStatsManager.queryEvents(range.getStartTime(), range.getEndTime(), context.getPackageName());
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
     * @param usage_interval Usage interval
     * @return A list of package usage
     */
    public List<PackageUsageInfo> getUsageStats(@UsageUtils.IntervalType int usage_interval) throws RemoteException {
        List<PackageUsageInfo> screenTimeList = new ArrayList<>();
        Map<String, Long> screenTimes = new HashMap<>();
        Map<String, Long> lastUse = new HashMap<>();
        Map<String, Integer> accessCount = new HashMap<>();
        // Get events
        UsageUtils.TimeInterval interval = UsageUtils.getTimeInterval(usage_interval);
        UsageEvents events = mUsageStatsManager.queryEvents(interval.getStartTime(), interval.getEndTime(), context.getPackageName());
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
            NetworkStatsManager networkStatsManager = (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
            try {
                mobileData.putAll(getMobileData(networkStatsManager, usage_interval));
            } catch (Exception ignore) {
            }
            try {
                wifiData.putAll(getWifiData(networkStatsManager, usage_interval));
            } catch (Exception ignore) {
            }
        }
        for (String packageName : screenTimes.keySet()) {
            // Skip not installed packages
            if (!PackageUtils.isInstalled(mPackageManager, packageName)) continue;
            PackageUsageInfo packageUS = new PackageUsageInfo(packageName);
            packageUS.appLabel = PackageUtils.getPackageLabel(mPackageManager, packageName);
            packageUS.timesOpened = accessCount.get(packageName);
            packageUS.lastUsageTime = lastUse.get(packageName);
            packageUS.screenTime = screenTimes.get(packageName);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int uid = PackageUtils.getAppUid(mPackageManager, packageName);
                if (mobileData.containsKey(uid))
                    packageUS.mobileData = mobileData.get(uid);
                else packageUS.mobileData = new DataUsage(0L, 0L);
                if (wifiData.containsKey(uid))
                    packageUS.wifiData = wifiData.get(uid);
                else packageUS.wifiData = new DataUsage(0L, 0L);
            }
            screenTimeList.add(packageUS);
        }
//        Log.d("US", getUsageStatsForPackage(context.getPackageName(), usage_interval).toString());
        return screenTimeList;
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
        NetworkStats networkStats;
        try {
            networkStats = nsm.querySummary(networkType, null, range.getStartTime(), range.getEndTime());
            if (networkStats != null) {
                while (networkStats.hasNextBucket()) {
                    NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                    networkStats.getNextBucket(bucket);
                    Log.d("Data usage", "UID:" + bucket.getUid());
                    dataUsageSparseArray.put(bucket.getUid(), new DataUsage(bucket.getTxBytes(), bucket.getRxBytes()));
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
        long totalTx = 0;
        long totalRx = 0;
        NetworkStatsManager networkStatsManager = (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
        UsageUtils.TimeInterval range = UsageUtils.getTimeInterval(intervalType);
        try {
            if (networkStatsManager != null) {
                for (int networkId = 0; networkId < 2; ++networkId) {
                    NetworkStats networkStats = networkStatsManager.querySummary(networkId, null,
                            range.getStartTime(), range.getEndTime());
                    if (networkStats != null) {
                        while (networkStats.hasNextBucket()) {
                            NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                            networkStats.getNextBucket(bucket);
                            if (bucket.getUid() == uid) {
                                totalTx += bucket.getTxBytes();
                                totalRx += bucket.getRxBytes();
                            }
                        }
                    }
                }
            }
        } catch (RemoteException ignore) {
        }
        return new DataUsage(totalTx, totalRx);
    }
}
