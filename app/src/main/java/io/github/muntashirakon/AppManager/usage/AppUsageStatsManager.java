package io.github.muntashirakon.AppManager.usage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import io.github.muntashirakon.AppManager.utils.Tuple;

public class AppUsageStatsManager {
    public static final int USAGE_TIME_MAX = 5000;
    private static final String SYS_USAGE_STATS_SERVICE = "usagestats";

    @SuppressLint("StaticFieldLeak")
    private static AppUsageStatsManager appUsageStatsManager;
    public static AppUsageStatsManager getInstance(@NonNull Context context) {
        if (appUsageStatsManager == null) appUsageStatsManager = new AppUsageStatsManager(context.getApplicationContext());
        return appUsageStatsManager;
    }

    private UsageStatsManager mUsageStatsManager;
    private Context context;
    private PackageManager mPackageManager;

    @SuppressLint("WrongConstant")
    private AppUsageStatsManager(@NonNull Context context) {
        this.context = context;
        this.mPackageManager = context.getPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            mUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        } else {
            mUsageStatsManager = (UsageStatsManager) context.getSystemService(SYS_USAGE_STATS_SERVICE);
        }
    }

    public PackageUS getUsageStatsForPackage(@NonNull String packageName, @Utils.IntervalType int usage_interval) {
        PackageUS packageUS = new PackageUS(packageName);
        packageUS.appLabel = Utils.getPackageLabel(mPackageManager, packageName);
        if (mUsageStatsManager == null) return packageUS;

        Tuple<Long, Long> range = Utils.getTimeInterval(usage_interval);
        UsageEvents events = mUsageStatsManager.queryEvents(range.getFirst(), range.getSecond());
        UsageEvents.Event event = new UsageEvents.Event();
        List<USEntry> usEntries = new ArrayList<>();
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
                usEntries.add(new USEntry(startTime, endTime));
                startTime = 0;
                endTime = 0;
            }
        }
        packageUS.entries = usEntries;
        return packageUS;
    }

    public List<PackageUS> getUsageStats(int sort, @Utils.IntervalType int usage_interval) {
        List<PackageUS> screenTimeList = new ArrayList<>();
        if (mUsageStatsManager == null) return screenTimeList;
        String prevPackageName = "";
        Map<String, Long> openingTimes = new HashMap<>();
        Map<String, Long> closingTimes = new HashMap<>();
        Map<String, Long> screenTimes = new HashMap<>();
        Map<String, Long> lastUse = new HashMap<>();
        Map<String, Integer> accessCount = new HashMap<>();
        // Get events
        Tuple<Long, Long> interval = Utils.getTimeInterval(usage_interval);
        UsageEvents events = mUsageStatsManager.queryEvents(interval.getFirst(), interval.getSecond());
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int eventType = event.getEventType();
            long eventTime = event.getTimeStamp();
            String packageName = event.getPackageName();
            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {  // App opened: MOVE_TO_FOREGROUND
                if (!openingTimes.containsKey(packageName)) {
                    openingTimes.put(packageName, eventTime);
                }
            } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED) {  // App closed: MOVE_TO_BACKGROUND
                if (openingTimes.containsKey(packageName)) {
                    closingTimes.put(packageName, eventTime);
                }
            }
            // Calculate usage time based on the assumption that no application can be run in
            // the middle of a running application. This is a valid assumption since onPause
            // is called whenever an app goes to background and onResume is called whenever an
            // app appears in foreground.
            if (TextUtils.isEmpty(prevPackageName)) prevPackageName = packageName;  // Initial value
            if (!prevPackageName.equals(packageName)) {  // Application switched, store duration
                if (openingTimes.containsKey(prevPackageName) && closingTimes.containsKey(prevPackageName)) {
                    //noinspection ConstantConditions
                    long time = closingTimes.get(prevPackageName) - openingTimes.get(prevPackageName);
                    if (screenTimes.containsKey(prevPackageName))
                        //noinspection ConstantConditions
                        screenTimes.put(prevPackageName, screenTimes.get(prevPackageName) + time);
                    else screenTimes.put(prevPackageName, time);
                    lastUse.put(prevPackageName, closingTimes.get(prevPackageName));
                    if (time > USAGE_TIME_MAX) {
                        if (accessCount.containsKey(prevPackageName))
                            accessCount.put(prevPackageName, accessCount.get(prevPackageName));
                        else accessCount.put(prevPackageName, 1);
                    }
                    openingTimes.remove(prevPackageName);
                    closingTimes.remove(prevPackageName);
                }
                prevPackageName = packageName;
            }
        }
        Map<String, Long> mobileData = new HashMap<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkStatsManager networkStatsManager = (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            mobileData = getMobileData(telephonyManager, networkStatsManager, usage_interval);
        }
        for(String packageName: screenTimes.keySet()) {
            // Skip not installed packages
            if (!Utils.isInstalled(mPackageManager, packageName)) continue;
            PackageUS packageUS = new PackageUS(packageName);
            packageUS.appLabel = Utils.getPackageLabel(mPackageManager, packageName);
            packageUS.timesOpened = accessCount.get(packageName);
            packageUS.lastUsageTime = lastUse.get(packageName);
            packageUS.screenTime = screenTimes.get(packageName);
            String key = "u" + Utils.getAppUid(mPackageManager, packageName);
            packageUS.mobileData = 0L;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (mobileData.containsKey(key))
                    packageUS.mobileData = mobileData.get(key);
            }
            screenTimeList.add(packageUS);
        }
        // TODO: Move them to AppUsageActivity
        // Sort by usage time
        if (sort == 0) {
            Collections.sort(screenTimeList, (o1, o2) -> (int) (o2.screenTime - o1.screenTime));
        } else if (sort == 1) {
            Collections.sort(screenTimeList, (o1, o2) -> (int) (o2.lastUsageTime - o1.lastUsageTime));
        } else if (sort == 2) {
            Collections.sort(screenTimeList, (o1, o2) -> o2.timesOpened - o1.timesOpened);
        } else {
            Collections.sort(screenTimeList, (o1, o2) -> (int) (o2.mobileData - o1.mobileData));
        }
//        Log.d("US", getUsageStatsForPackage(context.getPackageName(), usage_interval).toString());
        return screenTimeList;
    }

    @NonNull
    private Map<String, Long> getMobileData(TelephonyManager tm, NetworkStatsManager nsm, @Utils.IntervalType int usage_interval) {
        Map<String, Long> result = new HashMap<>();
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            Tuple<Long, Long> range = Utils.getTimeInterval(usage_interval);
            NetworkStats networkStatsM;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    networkStatsM = nsm.querySummary(ConnectivityManager.TYPE_MOBILE, null, range.getFirst(), range.getSecond());
                    if (networkStatsM != null) {
                        while (networkStatsM.hasNextBucket()) {
                            NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                            networkStatsM.getNextBucket(bucket);
                            String key = "u" + bucket.getUid();
                            Log.d("******", key + " " + bucket.getTxBytes() + " " + bucket.getRxBytes());
                            if (result.containsKey(key)) {
                                result.put(key, result.get(key) + bucket.getTxBytes() + bucket.getRxBytes());
                            } else {
                                result.put(key, bucket.getTxBytes() + bucket.getRxBytes());
                            }
                        }
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    protected Long[] getWifiMobileUsageForPackage(String mPackageName, @Utils.IntervalType int usage_interval) {
        long totalWifi = 0;
        long totalMobile = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkStatsManager networkStatsManager = (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
            int targetUid = Utils.getAppUid(mPackageManager, mPackageName);
            Tuple<Long, Long> range = Utils.getTimeInterval(usage_interval);
            try {
                if (networkStatsManager != null) {
                    NetworkStats networkStats = networkStatsManager.querySummary(ConnectivityManager.TYPE_WIFI, "", range.getFirst(), range.getSecond());
                    if (networkStats != null) {
                        while (networkStats.hasNextBucket()) {
                            NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                            networkStats.getNextBucket(bucket);
                            if (bucket.getUid() == targetUid) {
                                totalWifi += bucket.getTxBytes() + bucket.getRxBytes();
                            }
                        }
                    }
                    TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                        NetworkStats networkStatsM = networkStatsManager.querySummary(ConnectivityManager.TYPE_MOBILE, null, range.getFirst(), range.getSecond());
                        if (networkStatsM != null) {
                            while (networkStatsM.hasNextBucket()) {
                                NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                                networkStatsM.getNextBucket(bucket);
                                if (bucket.getUid() == targetUid) {
                                    totalMobile += bucket.getTxBytes() + bucket.getRxBytes();
                                }
                            }
                        }
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return new Long[]{totalWifi, totalMobile};
    }

    public static class PackageUS {
        public @NonNull String packageName;
        public String appLabel;
        public Long screenTime;
        public Long lastUsageTime;
        public Integer timesOpened;
        public Integer notificationReceived;
        public Long mobileData;
        public Long wifiData;
        public Long txData;
        public Long rxData;
        public @Nullable List<USEntry> entries;

        public PackageUS(@NonNull String packageName) {
            this.packageName = packageName;
        }

        @NonNull
        @Override
        public String toString() {
            return "PackageUS{" +
                    "packageName='" + packageName + '\'' +
                    ", appLabel='" + appLabel + '\'' +
                    ", screenTime=" + screenTime +
                    ", lastUsageTime=" + lastUsageTime +
                    ", timesOpened=" + timesOpened +
                    ", notificationReceived=" + notificationReceived +
                    ", mobileData=" + mobileData +
                    ", wifiData=" + wifiData +
                    ", txData=" + txData +
                    ", rxData=" + rxData +
                    ", entries=" + entries +
                    '}';
        }
    }

    public static class USEntry {
        public final long startTime;
        public final long endTime;

        public USEntry(long startTime, long endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public long getDuration() {
            return endTime - startTime;
        }

        @NonNull
        @Override
        public String toString() {
            return "USEntry{" +
                    "startTime=" + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(new Date(startTime)) +
                    ", endTime=" + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(new Date(endTime)) +
                    '}';
        }
    }
}
