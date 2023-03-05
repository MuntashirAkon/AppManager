// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerService;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.details.AppDetailsViewModel;
import io.github.muntashirakon.AppManager.magisk.MagiskDenyList;
import io.github.muntashirakon.AppManager.magisk.MagiskHide;
import io.github.muntashirakon.AppManager.magisk.MagiskModuleInfo;
import io.github.muntashirakon.AppManager.magisk.MagiskProcess;
import io.github.muntashirakon.AppManager.magisk.MagiskUtils;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.ssaid.SsaidSettings;
import io.github.muntashirakon.AppManager.types.PackageSizeInfo;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.UsageUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.AppManager.utils.UiThreadHandler;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class AppInfoViewModel extends AndroidViewModel {
    private final MutableLiveData<CharSequence> packageLabel = new MutableLiveData<>();
    private final MutableLiveData<TagCloud> tagCloud = new MutableLiveData<>();
    private final MutableLiveData<AppInfo> appInfo = new MutableLiveData<>();
    private final MutableLiveData<Pair<Integer, CharSequence>> installExistingResult = new MutableLiveData<>();
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

    public LiveData<CharSequence> getPackageLabel() {
        return packageLabel;
    }

    public LiveData<TagCloud> getTagCloud() {
        return tagCloud;
    }

    public LiveData<AppInfo> getAppInfo() {
        return appInfo;
    }

    public LiveData<Pair<Integer, CharSequence>> getInstallExistingResult() {
        return installExistingResult;
    }

    @WorkerThread
    public void loadPackageLabel() {
        if (mainModel != null) {
            PackageInfo pi = mainModel.getPackageInfo();
            if (pi != null) {
                packageLabel.postValue(pi.applicationInfo.loadLabel(getApplication().getPackageManager()));
            }
        }
    }

    @WorkerThread
    public void loadTagCloud() {
        if (mainModel == null) return;
        PackageInfo packageInfo = mainModel.getPackageInfo();
        if (packageInfo == null) return;
        String packageName = packageInfo.packageName;
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        TagCloud tagCloud = new TagCloud();
        try {
            HashMap<String, RuleType> trackerComponents = ComponentUtils
                    .getTrackerComponentsForPackageInfo(packageInfo);
            tagCloud.trackerComponents = new ArrayList<>(trackerComponents.size());
            for (String component : trackerComponents.keySet()) {
                ComponentRule componentRule = mainModel.getComponentRule(component);
                if (componentRule == null) {
                    componentRule = new ComponentRule(packageName, component, trackerComponents.get(component),
                            Prefs.Blocking.getDefaultBlockingMethod());
                }
                tagCloud.trackerComponents.add(componentRule);
                tagCloud.areAllTrackersBlocked &= componentRule.isBlocked();
            }
            tagCloud.isSystemApp = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!mainModel.getIsExternalApk() && Ops.isRoot()) {
                String codePath = PackageUtils.getHiddenCodePathOrDefault(applicationInfo);
                if (codePath != null) {
                    tagCloud.systemlessPathInfo = MagiskUtils.getSystemlessPathInfo(codePath);
                }
            }
            tagCloud.isUpdatedSystemApp = (applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            tagCloud.splitCount = mainModel.getSplitCount();
            tagCloud.isDebuggable = (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            tagCloud.isTestOnly = (applicationInfo.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0;
            tagCloud.hasCode = (applicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) != 0;
            tagCloud.hasRequestedLargeHeap = (applicationInfo.flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0;
            tagCloud.runningServices = ActivityManagerCompat.getRunningServices(packageName, mainModel.getUserHandle());
            tagCloud.isForceStopped = (applicationInfo.flags & ApplicationInfo.FLAG_STOPPED) != 0;
            tagCloud.isAppEnabled = applicationInfo.enabled;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tagCloud.isAppSuspended = (applicationInfo.flags & ApplicationInfo.FLAG_SUSPENDED) != 0;
            }
            int privateFlags = ApplicationInfoCompat.getPrivateFlags(applicationInfo);
            tagCloud.isAppHidden = (privateFlags & ApplicationInfoCompat.PRIVATE_FLAG_HIDDEN) != 0;
            tagCloud.magiskHiddenProcesses = MagiskHide.getProcesses(packageInfo);
            boolean magiskHideEnabled = false;
            for (MagiskProcess magiskProcess : tagCloud.magiskHiddenProcesses) {
                magiskHideEnabled |= magiskProcess.isEnabled();
                for (ActivityManager.RunningServiceInfo info : tagCloud.runningServices) {
                    if (info.process.startsWith(magiskProcess.name)) {
                        magiskProcess.setRunning(true);
                    }
                }
            }
            tagCloud.isMagiskHideEnabled = !mainModel.getIsExternalApk() && magiskHideEnabled;
            tagCloud.magiskDeniedProcesses = MagiskDenyList.getProcesses(packageInfo);
            boolean magiskDenyListEnabled = false;
            for (MagiskProcess magiskProcess : tagCloud.magiskDeniedProcesses) {
                magiskDenyListEnabled |= magiskProcess.isEnabled();
                for (ActivityManager.RunningServiceInfo info : tagCloud.runningServices) {
                    if (info.process.startsWith(magiskProcess.name)) {
                        magiskProcess.setRunning(true);
                    }
                }
            }
            tagCloud.isMagiskDenyListEnabled = !mainModel.getIsExternalApk() && magiskDenyListEnabled;
            tagCloud.canWriteAndExecute = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && applicationInfo.targetSdkVersion < Build.VERSION_CODES.Q;
            tagCloud.hasKeyStoreItems = KeyStoreUtils.hasKeyStore(applicationInfo.uid);
            tagCloud.hasMasterKeyInKeyStore = KeyStoreUtils.hasMasterKey(applicationInfo.uid);
            tagCloud.usesPlayAppSigning = PackageUtils.usesPlayAppSigning(applicationInfo);
            tagCloud.backups = BackupUtils.getBackupMetadataFromDbNoLockValidate(packageName);
            if (!mainModel.getIsExternalApk() && PermissionUtils.hasDumpPermission()) {
                String targetString = "user," + packageName + "," + applicationInfo.uid;
                Runner.Result result = Runner.runCommand(new String[]{"dumpsys", "deviceidle", "whitelist"});
                tagCloud.isBatteryOptimized = !result.isSuccessful() || !result.getOutput().contains(targetString);
            } else {
                tagCloud.isBatteryOptimized = true;
            }
            if (!mainModel.getIsExternalApk() && Ops.isPrivileged()) {
                tagCloud.netPolicies = NetworkPolicyManagerCompat.getUidPolicy(applicationInfo.uid);
            } else {
                tagCloud.netPolicies = 0;
            }
            if (Ops.isRoot() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    tagCloud.ssaid = new SsaidSettings(mainModel.getUserHandle())
                            .getSsaid(packageName, applicationInfo.uid);
                    if (TextUtilsCompat.isEmpty(tagCloud.ssaid)) tagCloud.ssaid = null;
                } catch (IOException ignore) {
                }
            }
            if (Ops.isRoot()) {
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
        } catch (Throwable th) {
            // Unknown behaviour
            UiThreadHandler.run(() -> {
                // Throw Runtime exception in main thread to crash the app
                throw new RuntimeException(th);
            });
        }
    }

    @WorkerThread
    public void loadAppInfo() {
        if (mainModel == null) return;
        PackageInfo packageInfo = mainModel.getPackageInfo();
        if (packageInfo == null) return;
        String packageName = packageInfo.packageName;
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        int userId = UserHandleHidden.getUserId(applicationInfo.uid);
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
            OsEnvironment.UserEnvironment ue = OsEnvironment.getUserEnvironment(userId);
            Path[] externalDataDirs = ue.buildExternalStorageAppDataDirs(packageName);
            for (Path externalDataDir : externalDataDirs) {
                Path accessiblePath = Paths.getAccessiblePath(externalDataDir);
                if (accessiblePath.exists()) {
                    appInfo.extDataDirs.add(Objects.requireNonNull(accessiblePath.getFilePath()));
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
            appInfo.sizeInfo = PackageUtils.getPackageSizeInfo(getApplication(), packageName, userId,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? applicationInfo.storageUuid : null);
            // Set installer app
            try {
                String installerPackageName = PackageManagerCompat.getInstallerPackageName(packageName);
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
            } catch (RemoteException ignore) {
            }
            // Set main activity
            appInfo.mainActivity = pm.getLaunchIntentForPackage(packageName);
            // SELinux
            appInfo.seInfo = ApplicationInfoCompat.getSeInfo(applicationInfo);
            // Primary ABI
            appInfo.primaryCpuAbi = ApplicationInfoCompat.getPrimaryCpuAbi(applicationInfo);
            // zygotePreloadName
            appInfo.zygotePreloadName = ApplicationInfoCompat.getZygotePreloadName(applicationInfo);
            // hiddenApiEnforcementPolicy
            appInfo.hiddenApiEnforcementPolicy = ApplicationInfoCompat.getHiddenApiEnforcementPolicy(applicationInfo);
        }
        this.appInfo.postValue(appInfo);
    }

    public void installExisting(@UserIdInt int userId) {
        if (mainModel == null) return;
        executor.submit(() -> {
            PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
            installer.setOnInstallListener(new PackageInstallerCompat.OnInstallListener() {
                @Override
                public void onStartInstall(int sessionId, String packageName) {
                }

                @Override
                public void onFinishedInstall(int sessionId, String packageName, int result,
                                              @Nullable String blockingPackage, @Nullable String statusMessage) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(PackageInstallerService.getStringFromStatus(getApplication(), result,
                            getPackageLabel().getValue(), blockingPackage));
                    if (statusMessage != null) {
                        sb.append("\n\n").append(statusMessage);
                    }
                    installExistingResult.postValue(new Pair<>(result, sb));
                }
            });
            installer.installExisting(Objects.requireNonNull(mainModel.getPackageName()), userId);
        });
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
        Path uidStatsDir = Paths.get(UID_STATS_PATH + uid);
        if (uidStatsDir.isDirectory()) {
            try {
                Path txFile = uidStatsDir.findFile(UID_STATS_TX);
                Path rxFile = uidStatsDir.findFile(UID_STATS_RX);
                tx = Long.parseLong(txFile.getContentAsString("0").trim());
                rx = Long.parseLong(rxFile.getContentAsString("0").trim());
            } catch (FileNotFoundException ignore) {
            }
        }
        return new AppUsageStatsManager.DataUsage(tx, rx);
    }

    public static class TagCloud {
        public List<ComponentRule> trackerComponents;
        public boolean areAllTrackersBlocked = true;
        public boolean isSystemApp;
        @Nullable
        public MagiskModuleInfo systemlessPathInfo;
        public boolean isUpdatedSystemApp;
        public int splitCount;
        public boolean isDebuggable;
        public boolean isTestOnly;
        public boolean hasCode;
        public boolean hasRequestedLargeHeap;
        public List<ActivityManager.RunningServiceInfo> runningServices;
        public List<MagiskProcess> magiskHiddenProcesses;
        public List<MagiskProcess> magiskDeniedProcesses;
        public boolean isForceStopped;
        public boolean isAppEnabled;
        public boolean isAppHidden;
        public boolean isAppSuspended;
        public boolean isMagiskHideEnabled;
        public boolean isMagiskDenyListEnabled;
        public boolean canWriteAndExecute;
        public boolean hasKeyStoreItems;
        public boolean hasMasterKeyInKeyStore;
        public boolean usesPlayAppSigning;
        public List<Backup> backups;
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
        // Data usage
        @Nullable
        public AppUsageStatsManager.DataUsage dataUsage;
        @Nullable
        public PackageSizeInfo sizeInfo;
        // More info
        @Nullable
        public String installerApp;
        @Nullable
        public Intent mainActivity;
        @Nullable
        public String seInfo;
        @Nullable
        public String primaryCpuAbi;
        @Nullable
        public String zygotePreloadName;
        public int hiddenApiEnforcementPolicy;
    }
}
