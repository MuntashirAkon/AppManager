/*
 * Copyright (c) 2021 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.details.info;

import android.app.Application;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.*;
import android.os.Build;
import android.os.Process;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.details.AppDetailsViewModel;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.UsageUtils;
import io.github.muntashirakon.AppManager.utils.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppInfoViewModel extends AndroidViewModel {
    private final MutableLiveData<CharSequence> packageLabel = new MutableLiveData<>();
    private final MutableLiveData<TagCloud> tagCloud = new MutableLiveData<>();
    private final MutableLiveData<AppInfo> appInfo = new MutableLiveData<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    @Nullable
    private AppDetailsViewModel mainModel;

    public AppInfoViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
        super.onCleared();
    }

    public void setMainModel(@NonNull AppDetailsViewModel mainModel) {
        this.mainModel = mainModel;
    }

    public MutableLiveData<CharSequence> getPackageLabel() {
        return packageLabel;
    }

    public MutableLiveData<TagCloud> getTagCloud() {
        return tagCloud;
    }

    public MutableLiveData<AppInfo> getAppInfo() {
        return appInfo;
    }

    @WorkerThread
    public void loadPackageLabel() {
        if (mainModel != null) {
            packageLabel.postValue(mainModel.getPackageInfo().applicationInfo.loadLabel(getApplication()
                    .getPackageManager()));
        }
    }

    @WorkerThread
    public void loadTagCloud() {
        if (mainModel == null) return;
        PackageInfo packageInfo = mainModel.getPackageInfo();
        String packageName = packageInfo.packageName;
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        TagCloud tagCloud = new TagCloud();
        tagCloud.trackerComponents = ComponentUtils.getTrackerComponentsForPackageInfo(packageInfo);
        tagCloud.isSystemApp = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        tagCloud.isSystemlessPath = !mainModel.getIsExternalApk() && AppPref.isRootEnabled()
                && MagiskUtils.isSystemlessPath(PackageUtils.getHiddenCodePathOrDefault(packageName,
                applicationInfo.publicSourceDir));
        tagCloud.isUpdatedSystemApp = (applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        tagCloud.splitCount = mainModel.getSplitCount();
        tagCloud.isDebuggable = (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        tagCloud.isTestOnly = (applicationInfo.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0;
        tagCloud.hasCode = (applicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) != 0;
        tagCloud.hasRequestedLargeHeap = (applicationInfo.flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0;
        tagCloud.isRunning = !mainModel.getIsExternalApk() && PackageUtils.hasRunningServices(packageName);
        tagCloud.isForceStopped = (applicationInfo.flags & ApplicationInfo.FLAG_STOPPED) != 0;
        tagCloud.isAppEnabled = applicationInfo.enabled;
        tagCloud.isMagiskHideEnabled = !mainModel.getIsExternalApk() && AppPref.isRootEnabled() && MagiskUtils.isHidden(packageName);
        tagCloud.hasKeyStoreItems = KeyStoreUtils.hasKeyStore(applicationInfo.uid);
        tagCloud.hasMasterKeyInKeyStore = KeyStoreUtils.hasMasterKey(applicationInfo.uid);
        MetadataManager.Metadata[] metadata = MetadataManager.getMetadata(packageName);
        String[] readableBackupNames = new String[metadata.length];
        for (int i = 0; i < metadata.length; ++i) {
            String backupName = BackupUtils.getShortBackupName(metadata[i].backupName);
            int userHandle = metadata[i].userHandle;
            readableBackupNames[i] = backupName == null ? "Base backup for user " + userHandle : backupName + " for user " + userHandle;
        }
        tagCloud.readableBackupNames = readableBackupNames;
        if (!mainModel.getIsExternalApk() && PermissionUtils.hasDumpPermission()) {
            String targetString = "user," + packageName + "," + applicationInfo.uid;
            Runner.Result result = Runner.runCommand(new String[]{"dumpsys", "deviceidle", "whitelist"});
            tagCloud.isBatteryOptimized = !result.isSuccessful() || !result.getOutput().contains(targetString);
        } else {
            tagCloud.isBatteryOptimized = true;
        }
        if (!mainModel.getIsExternalApk() && LocalServer.isAMServiceAlive()) {
            tagCloud.netPolicies = NetworkPolicyManagerCompat.getUidPolicy(applicationInfo.uid);
        } else {
            tagCloud.netPolicies = 0;
        }
        this.tagCloud.postValue(tagCloud);
    }

    @WorkerThread
    public void loadAppInfo() {
        if (mainModel == null) return;
        PackageInfo packageInfo = mainModel.getPackageInfo();
        String packageName = packageInfo.packageName;
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        PackageManager pm = getApplication().getPackageManager();
        boolean isExternalApk = mainModel.getIsExternalApk();
        AppInfo appInfo = new AppInfo();
        // Set source dir
        if (!isExternalApk) {
            appInfo.sourceDir = new File(applicationInfo.publicSourceDir).getParent();
        }
        // Set split entries
        ApkFile apkFile = ApkFile.getInstance(mainModel.getApkFileKey());
        int countSplits = apkFile.getEntries().size() - 1;
        appInfo.splitEntries = new ArrayList<>(countSplits);
        // Base.apk is always on top, so count from 1
        for (int i = 1; i <= countSplits; ++i) {
            appInfo.splitEntries.add(apkFile.getEntries().get(i));
        }
        // Set data dirs
        if (!isExternalApk) {
            appInfo.dataDir = applicationInfo.dataDir;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appInfo.dataDeDir = applicationInfo.deviceProtectedDataDir;
            }
        }
        appInfo.extDataDirs = new ArrayList<>();
        if (!isExternalApk) {
            File[] dataDirs = getApplication().getExternalCacheDirs();
            if (dataDirs != null) {
                String tmpDataDir;
                for (File dataDir : dataDirs) {
                    if (dataDir == null) continue;
                    tmpDataDir = dataDir.getParent();
                    if (tmpDataDir != null) {
                        tmpDataDir = new File(tmpDataDir).getParent();
                    }
                    if (tmpDataDir != null) {
                        tmpDataDir = tmpDataDir + File.separatorChar + packageName;
                        if (new File(tmpDataDir).exists()) {
                            appInfo.extDataDirs.add(tmpDataDir);
                        }
                    }
                }
            }
        }
        // Set JNI dir
        if (!isExternalApk && new File(applicationInfo.nativeLibraryDir).exists()) {
            appInfo.jniDir = applicationInfo.nativeLibraryDir;
        }
        // Net statistics
        if (!isExternalApk) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if ((Boolean) AppPref.get(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL)) {
                        final Pair<Pair<Long, Long>, Pair<Long, Long>> dataUsage;
                        dataUsage = AppUsageStatsManager.getWifiMobileUsageForPackage(getApplication(), packageName,
                                UsageUtils.USAGE_LAST_BOOT);
                        appInfo.dataTx = dataUsage.first.first + dataUsage.second.first;
                        appInfo.dataRx = dataUsage.first.second + dataUsage.second.second;
                    }
                } else {
                    final Pair<Long, Long> uidNetStats = getNetStats(applicationInfo.uid);
                    appInfo.dataTx = uidNetStats.first;
                    appInfo.dataRx = uidNetStats.second;
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            // Set sizes
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                try {
                    Method getPackageSizeInfo = pm.getClass().getMethod("getPackageSizeInfo", String.class,
                            IPackageStatsObserver.class);
                    getPackageSizeInfo.invoke(pm, packageName, new IPackageStatsObserver.Stub() {
                        @SuppressWarnings("deprecation")
                        @Override
                        public void onGetStatsCompleted(final PackageStats pStats, boolean succeeded) {
                            appInfo.codeSize = pStats.codeSize + pStats.externalCodeSize;
                            appInfo.dataSize = pStats.dataSize + pStats.externalDataSize;
                            appInfo.cacheSize = pStats.cacheSize + pStats.externalCacheSize;
                            appInfo.obbSize = pStats.externalObbSize;
                            appInfo.mediaSize = pStats.externalMediaSize;
                        }
                    });
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            } else {
                if (Utils.hasUsageStatsPermission(getApplication())) {
                    try {
                        StorageStatsManager storageStatsManager = (StorageStatsManager) getApplication().getSystemService(Context.STORAGE_STATS_SERVICE);
                        StorageStats storageStats = storageStatsManager.queryStatsForPackage(applicationInfo.storageUuid, packageName, Process.myUserHandle());
                        appInfo.cacheSize = storageStats.getCacheBytes();
                        appInfo.codeSize = storageStats.getAppBytes();
                        appInfo.dataSize = storageStats.getDataBytes() - appInfo.cacheSize;
                        // TODO(24/1/21): List obb and media size
                    } catch (IOException | PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
            // Set installer app
            try {
                @SuppressWarnings("deprecation")
                String installerPackageName = pm.getInstallerPackageName(packageName);
                if (installerPackageName != null) {
                    String applicationLabel;
                    try {
                        applicationLabel = pm.getApplicationInfo(installerPackageName, 0).loadLabel(pm).toString();
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                        applicationLabel = installerPackageName;
                    }
                    appInfo.installerApp = applicationLabel;
                }
            } catch (IllegalArgumentException ignore) {
            }
            // Set main activity
            appInfo.mainActivity = pm.getLaunchIntentForPackage(packageName);
        }
        this.appInfo.postValue(appInfo);
    }


    private static final String UID_STATS_PATH = "/proc/uid_stat/";
    private static final String UID_STATS_TX = "tcp_rcv";
    private static final String UID_STATS_RX = "tcp_snd";

    /**
     * Get network stats.
     *
     * @param uid Application UID
     * @return A tuple consisting of transmitted and received data
     */
    @NonNull
    private Pair<Long, Long> getNetStats(int uid) {
        long tx = 0;
        long rx = 0;
        File uidStatsDir = new File(UID_STATS_PATH + uid);
        if (uidStatsDir.exists() && uidStatsDir.isDirectory()) {
            for (File child : Objects.requireNonNull(uidStatsDir.listFiles())) {
                if (child.getName().equals(UID_STATS_TX))
                    tx = Long.parseLong(IOUtils.getFileContent(child, "-1").trim());
                else if (child.getName().equals(UID_STATS_RX))
                    rx = Long.parseLong(IOUtils.getFileContent(child, "-1").trim());
            }
        }
        return new Pair<>(tx, rx);
    }

    public static class TagCloud {
        public HashMap<String, RulesStorageManager.Type> trackerComponents;
        public boolean isSystemApp;
        public boolean isSystemlessPath;
        public boolean isUpdatedSystemApp;
        public int splitCount;
        public boolean isDebuggable;
        public boolean isTestOnly;
        public boolean hasCode;
        public boolean hasRequestedLargeHeap;
        public boolean isRunning;
        public boolean isForceStopped;
        public boolean isAppEnabled;
        public boolean isMagiskHideEnabled;
        public boolean hasKeyStoreItems;
        public boolean hasMasterKeyInKeyStore;
        public String[] readableBackupNames;
        public boolean isBatteryOptimized;
        public int netPolicies;
    }

    public static class AppInfo {
        // Paths & dirs
        @Nullable
        public String sourceDir;
        public List<ApkFile.Entry> splitEntries;
        @Nullable
        public String dataDir;
        @Nullable
        public String dataDeDir;
        public List<String> extDataDirs;
        @Nullable
        public String jniDir;
        // Data usage
        public long dataTx;
        public long dataRx;
        // Size info
        public long codeSize;
        public long dataSize;
        public long cacheSize;
        public long obbSize;
        public long mediaSize;
        // More info
        @Nullable
        public String installerApp;
        @Nullable
        public Intent mainActivity;
    }
}
