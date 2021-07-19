// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.android.internal.util.TextUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.details.AppDetailsViewModel;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.types.PackageSizeInfo;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.UsageUtils;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.MagiskUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;
import io.github.muntashirakon.AppManager.utils.SsaidSettings;
import io.github.muntashirakon.io.ProxyFile;

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
        HashMap<String, RuleType> trackerComponents = ComponentUtils.getTrackerComponentsForPackageInfo(packageInfo);
        tagCloud.trackerComponents = new ArrayList<>(trackerComponents.size());
        for (String component : trackerComponents.keySet()) {
            tagCloud.trackerComponents.add(new ComponentRule(packageName, component, trackerComponents.get(component),
                    ComponentRule.COMPONENT_TO_BE_BLOCKED));
        }
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
        tagCloud.runningServices = PackageUtils.getRunningServicesForPackage(packageName, mainModel.getUserHandle());
        tagCloud.isForceStopped = (applicationInfo.flags & ApplicationInfo.FLAG_STOPPED) != 0;
        tagCloud.isAppEnabled = applicationInfo.enabled;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tagCloud.isAppSuspended = (applicationInfo.flags & ApplicationInfo.FLAG_SUSPENDED) != 0;
        }
        int privateFlags = ApplicationInfoCompat.getPrivateFlags(applicationInfo);
        tagCloud.isAppHidden = (privateFlags & ApplicationInfoCompat.PRIVATE_FLAG_HIDDEN) != 0;
        tagCloud.isMagiskHideEnabled = !mainModel.getIsExternalApk() && AppPref.isRootEnabled() && MagiskUtils.isHidden(packageName);
        tagCloud.hasKeyStoreItems = KeyStoreUtils.hasKeyStore(applicationInfo.uid);
        tagCloud.hasMasterKeyInKeyStore = KeyStoreUtils.hasMasterKey(applicationInfo.uid);
        try {
            MetadataManager.Metadata[] metadata = MetadataManager.getMetadata(packageName);
            CharSequence[] readableBackupNames = new CharSequence[metadata.length];
            for (int i = 0; i < metadata.length; ++i) {
                readableBackupNames[i] = metadata[i].toLocalizedString(getApplication());
            }
            tagCloud.readableBackupNames = readableBackupNames;
        } catch (IOException e) {
            tagCloud.readableBackupNames = ArrayUtils.emptyArray(CharSequence.class);
        }
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
        if (AppPref.isRootEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                tagCloud.ssaid = new SsaidSettings(packageName, applicationInfo.uid).getSsaid();
                if (TextUtils.isEmpty(tagCloud.ssaid)) tagCloud.ssaid = null;
            } catch (IOException ignore) {
            }
        }
        if (AppPref.isRootEnabled()) {
            List<UriManager.UriGrant> uriGrants = new UriManager().getGrantedUris(packageName);
            if (uriGrants != null) {
                Iterator<UriManager.UriGrant> uriGrantIterator = uriGrants.listIterator();
                UriManager.UriGrant uriGrant;
                while (uriGrantIterator.hasNext()) {
                    uriGrant = uriGrantIterator.next();
                    if (uriGrant.targetUserId != mainModel.getUserHandle()) {
                        uriGrantIterator.remove();
                    }
                }
                tagCloud.uriGrants = uriGrants;
            }
        }
        this.tagCloud.postValue(tagCloud);
    }

    @WorkerThread
    public void loadAppInfo() {
        if (mainModel == null) return;
        PackageInfo packageInfo = mainModel.getPackageInfo();
        String packageName = packageInfo.packageName;
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        int userHandle = Users.getUserId(applicationInfo.uid);
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
                    if (FeatureController.isUsageAccessEnabled()) {
                        appInfo.dataUsage = AppUsageStatsManager.getDataUsageForPackage(getApplication(),
                                applicationInfo.uid, UsageUtils.USAGE_LAST_BOOT);
                    }
                } else {
                    appInfo.dataUsage = getNetStats(applicationInfo.uid);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            // Set sizes
            if (PermissionUtils.hasUsageStatsPermission(getApplication())) {
                appInfo.sizeInfo = PackageUtils.getPackageSizeInfo(getApplication(), packageName, userHandle,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? applicationInfo.storageUuid : null);
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
    private static final String UID_STATS_TX = "tcp_snd";
    private static final String UID_STATS_RX = "tcp_rcv";

    /**
     * Get network stats.
     *
     * @param uid Application UID
     * @return A tuple consisting of transmitted and received data
     */
    @NonNull
    private AppUsageStatsManager.DataUsage getNetStats(int uid) {
        long tx = 0L;
        long rx = 0L;
        File uidStatsDir = new ProxyFile(UID_STATS_PATH + uid);
        if (uidStatsDir.isDirectory()) {
            File txFile = new ProxyFile(uidStatsDir, UID_STATS_TX);
            File rxFile = new ProxyFile(uidStatsDir, UID_STATS_RX);
            if (txFile.exists()) {
                tx = Long.parseLong(FileUtils.getFileContent(txFile, "0").trim());
            }
            if (rxFile.exists()) {
                rx = Long.parseLong(FileUtils.getFileContent(rxFile, "0").trim());
            }
        }
        return new AppUsageStatsManager.DataUsage(tx, rx);
    }

    public static class TagCloud {
        public List<ComponentRule> trackerComponents;
        public boolean isSystemApp;
        public boolean isSystemlessPath;
        public boolean isUpdatedSystemApp;
        public int splitCount;
        public boolean isDebuggable;
        public boolean isTestOnly;
        public boolean hasCode;
        public boolean hasRequestedLargeHeap;
        public List<ComponentName> runningServices;
        public boolean isForceStopped;
        public boolean isAppEnabled;
        public boolean isAppHidden;
        public boolean isAppSuspended;
        public boolean isMagiskHideEnabled;
        public boolean hasKeyStoreItems;
        public boolean hasMasterKeyInKeyStore;
        public CharSequence[] readableBackupNames;
        public boolean isBatteryOptimized;
        public int netPolicies;
        @Nullable
        public String ssaid;
        @Nullable
        public List<UriManager.UriGrant> uriGrants;
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
        @Nullable
        public AppUsageStatsManager.DataUsage dataUsage;
        @Nullable
        public PackageSizeInfo sizeInfo;
        // More info
        @Nullable
        public String installerApp;
        @Nullable
        public Intent mainActivity;
    }
}
