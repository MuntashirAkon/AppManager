package io.github.muntashirakon.AppManager.usage;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

    /**
     * Calculate screen time based on the assumption that no application can be run in the middle of
     * a running application. This is a valid assumption since <code>Activity#onPause()</code> is
     * called whenever an app goes to background and <code>Activity#onResume</code> is called
     * whenever an app appears in foreground.
     * @param sort Sorting order TODO
     * @param usage_interval Usage interval
     * @return A list of package usage
     */
    public List<PackageUS> getUsageStats(int sort, @Utils.IntervalType int usage_interval) {
        List<PackageUS> screenTimeList = new ArrayList<>();
        if (mUsageStatsManager == null) return screenTimeList;
        Map<String, Long> screenTimes = new HashMap<>();
        Map<String, Long> lastUse = new HashMap<>();
        Map<String, Integer> accessCount = new HashMap<>();
        // Get events
        Tuple<Long, Long> interval = Utils.getTimeInterval(usage_interval);
        UsageEvents events = mUsageStatsManager.queryEvents(interval.getFirst(), interval.getSecond());
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
                        skip_new = true; break;
                    } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                        endTime = eventTime;
                        skip_new = false;
                        if (packageName.equals(event.getPackageName())) {
                            long time = endTime - startTime;
                            if (time > USAGE_TIME_MAX) {
                                if (screenTimes.containsKey(packageName))
                                    //noinspection ConstantConditions
                                    screenTimes.put(packageName, screenTimes.get(packageName) + time);
                                else screenTimes.put(packageName, time);
                                lastUse.put(packageName, endTime);
                                if (accessCount.containsKey(packageName)) //noinspection ConstantConditions
                                    accessCount.put(packageName, accessCount.get(packageName) + 1);
                                else accessCount.put(packageName, 1);
                            }
                        }
                        break;
                    }
                }
            }
        }
        Map<String, Tuple<Long, Long>> mobileData = new HashMap<>();
        Map<String, Tuple<Long, Long>> wifiData = new HashMap<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkStatsManager networkStatsManager = (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
            try {
                mobileData = getMobileData(networkStatsManager, usage_interval);
            } catch (Exception ignore) {}
            try {
                wifiData = getWifiData(networkStatsManager, usage_interval);
            } catch (Exception ignore) {}
        }
        for(String packageName: screenTimes.keySet()) {
            // Skip not installed packages
            if (!Utils.isInstalled(mPackageManager, packageName)) continue;
            PackageUS packageUS = new PackageUS(packageName);
            packageUS.appLabel = Utils.getPackageLabel(mPackageManager, packageName);
            packageUS.timesOpened = accessCount.get(packageName);
            packageUS.lastUsageTime = lastUse.get(packageName);
            packageUS.screenTime = screenTimes.get(packageName);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String key = "u" + Utils.getAppUid(mPackageManager, packageName);
                if (mobileData.containsKey(key))
                    packageUS.mobileData = mobileData.get(key);
                else packageUS.mobileData = new Tuple<>(0L, 0L);
                if (wifiData.containsKey(key))
                    packageUS.wifiData = wifiData.get(key);
                else packageUS.wifiData = new Tuple<>(0L, 0L);
            }
            screenTimeList.add(packageUS);
        }
//        Log.d("US", getUsageStatsForPackage(context.getPackageName(), usage_interval).toString());
        return screenTimeList;
    }

    @TargetApi(23)
    @NonNull
    private Map<String, Tuple<Long, Long>> getMobileData(@NonNull NetworkStatsManager nsm, @Utils.IntervalType int usage_interval) {
        Map<String, Tuple<Long, Long>> result = new HashMap<>();
        Tuple<Long, Long> range  = Utils.getTimeInterval(usage_interval);
        Map<String, Long> txData = new HashMap<>();
        Map<String, Long> rxData = new HashMap<>();
        NetworkStats networkStats;
        try {
            networkStats = nsm.querySummary(NetworkCapabilities.TRANSPORT_CELLULAR, null, range.getFirst(), range.getSecond());
            if (networkStats != null) {
                while (networkStats.hasNextBucket()) {
                    NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                    networkStats.getNextBucket(bucket);
                    String key = "u" + bucket.getUid();
                    if (result.containsKey(key)) {
                        //noinspection ConstantConditions
                        txData.put(key, txData.get(key) + bucket.getTxBytes());
                        //noinspection ConstantConditions
                        rxData.put(key, rxData.get(key) + bucket.getRxBytes());
                    } else {
                        txData.put(key, bucket.getTxBytes());
                        rxData.put(key, bucket.getRxBytes());
                    }
                }
            }
            for (String uid: txData.keySet()) {
                result.put(uid, new Tuple<>(txData.get(uid), rxData.get(uid)));
            }
        } catch (RemoteException ignore) {}
        return result;
    }


    @TargetApi(23)
    @NonNull
    private Map<String, Tuple<Long, Long>> getWifiData(@NonNull NetworkStatsManager nsm, @Utils.IntervalType int usage_interval) {
        Map<String, Tuple<Long, Long>> result = new HashMap<>();
        Tuple<Long, Long> range  = Utils.getTimeInterval(usage_interval);
        Map<String, Long> txData = new HashMap<>();
        Map<String, Long> rxData = new HashMap<>();
        NetworkStats networkStats;
        try {
            networkStats = nsm.querySummary(NetworkCapabilities.TRANSPORT_WIFI, null, range.getFirst(), range.getSecond());
            if (networkStats != null) {
                while (networkStats.hasNextBucket()) {
                    NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                    networkStats.getNextBucket(bucket);
                    String key = "u" + bucket.getUid();
                    if (result.containsKey(key)) {
                        //noinspection ConstantConditions
                        txData.put(key, txData.get(key) + bucket.getTxBytes());
                        //noinspection ConstantConditions
                        rxData.put(key, rxData.get(key) + bucket.getRxBytes());
                    } else {
                        txData.put(key, bucket.getTxBytes());
                        rxData.put(key, bucket.getRxBytes());
                    }
                }
            }
            for (String uid: txData.keySet()) {
                result.put(uid, new Tuple<>(txData.get(uid), rxData.get(uid)));
            }
        } catch (RemoteException ignore) {}
        return result;
    }

    @TargetApi(23)
    @NonNull
    public static Tuple<Tuple<Long, Long>, Tuple<Long, Long>> getWifiMobileUsageForPackage(
            @NonNull Context context, String mPackageName, @Utils.IntervalType int usage_interval) {
        long totalWifiTx = 0;
        long totalWifiRx = 0;
        long totalMobileTx = 0;
        long totalMobileRx = 0;
        NetworkStatsManager networkStatsManager = (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
        int targetUid = Utils.getAppUid(context.getPackageManager(), mPackageName);
        Tuple<Long, Long> range = Utils.getTimeInterval(usage_interval);
        try {
            if (networkStatsManager != null) {
                NetworkStats networkStats = networkStatsManager.querySummary(NetworkCapabilities.TRANSPORT_WIFI, null, range.getFirst(), range.getSecond());
                if (networkStats != null) {
                    while (networkStats.hasNextBucket()) {
                        NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                        networkStats.getNextBucket(bucket);
                        if (bucket.getUid() == targetUid) {
                            totalWifiTx += bucket.getTxBytes();
                            totalWifiRx += bucket.getRxBytes();
                        }
                    }
                }
                NetworkStats networkStatsM = networkStatsManager.querySummary(NetworkCapabilities.TRANSPORT_CELLULAR, null, range.getFirst(), range.getSecond());
                if (networkStatsM != null) {
                    while (networkStatsM.hasNextBucket()) {
                        NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                        networkStatsM.getNextBucket(bucket);
                        if (bucket.getUid() == targetUid) {
                            totalMobileTx += bucket.getTxBytes();
                            totalMobileRx += bucket.getRxBytes();
                        }
                    }
                }
            }
        } catch (RemoteException ignore) {}
        return new Tuple<>(new Tuple<>(totalWifiTx, totalWifiRx), new Tuple<>(totalMobileTx, totalMobileRx));
    }

    public static class PackageUS implements Parcelable {
        public @NonNull String packageName;
        public String appLabel;
        public Long screenTime = 0L;
        public Long lastUsageTime = 0L;
        public Integer timesOpened = 0;
        public Tuple<Long, Long> mobileData;  // Tx, Rx
        public Tuple<Long, Long> wifiData;  // Tx, Rx
        public @Nullable List<USEntry> entries;

        public PackageUS(@NonNull String packageName) {
            this.packageName = packageName;
        }

        protected PackageUS(@NonNull Parcel in) {
            packageName = Objects.requireNonNull(in.readString());
            appLabel = in.readString();
            screenTime = in.readByte() == 0 ? 0L : in.readLong();
            lastUsageTime = in.readByte() == 0 ? 0L : in.readLong();
            timesOpened = in.readByte() == 0 ? 0 : in.readInt();
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(packageName);
            dest.writeString(appLabel);
            if (screenTime == null) {
                dest.writeByte((byte) 0);
            } else {
                dest.writeByte((byte) 1);
                dest.writeLong(screenTime);
            }
            if (lastUsageTime == null) {
                dest.writeByte((byte) 0);
            } else {
                dest.writeByte((byte) 1);
                dest.writeLong(lastUsageTime);
            }
            if (timesOpened == null) {
                dest.writeByte((byte) 0);
            } else {
                dest.writeByte((byte) 1);
                dest.writeInt(timesOpened);
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<PackageUS> CREATOR = new Creator<PackageUS>() {
            @Override
            public PackageUS createFromParcel(Parcel in) {
                return new PackageUS(in);
            }

            @Override
            public PackageUS[] newArray(int size) {
                return new PackageUS[size];
            }
        };

        public void copyOthers(@NonNull PackageUS packageUS) {
            screenTime = packageUS.screenTime;
            lastUsageTime = packageUS.lastUsageTime;
            timesOpened = packageUS.timesOpened;
            mobileData = packageUS.mobileData;
            wifiData = packageUS.wifiData;
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
                    ", txData=" + mobileData +
                    ", rxData=" + wifiData +
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
