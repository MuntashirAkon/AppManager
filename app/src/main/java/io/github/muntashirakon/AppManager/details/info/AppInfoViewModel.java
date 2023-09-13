// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.verify.domain.DomainVerificationUserState;
import android.os.Build;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerService;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.DeviceIdleManagerCompat;
import io.github.muntashirakon.AppManager.compat.DomainVerificationManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.debloat.DebloatObject;
import io.github.muntashirakon.AppManager.details.AppDetailsViewModel;
import io.github.muntashirakon.AppManager.magisk.MagiskDenyList;
import io.github.muntashirakon.AppManager.magisk.MagiskHide;
import io.github.muntashirakon.AppManager.magisk.MagiskProcess;
import io.github.muntashirakon.AppManager.magisk.MagiskUtils;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.misc.XposedModuleInfo;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.ssaid.SsaidSettings;
import io.github.muntashirakon.AppManager.types.PackageSizeInfo;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.UsageUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class AppInfoViewModel extends AndroidViewModel {
    private final MutableLiveData<CharSequence> mAppLabel = new MutableLiveData<>();
    private final MutableLiveData<TagCloud> mTagCloud = new MutableLiveData<>();
    private final MutableLiveData<AppInfo> mAppInfo = new MutableLiveData<>();
    private final MutableLiveData<Pair<Integer, CharSequence>> mInstallExistingResult = new MutableLiveData<>();
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(4);
    private Future<?> mTagCloudFuture;
    private Future<?> mAppInfoFuture;
    @Nullable
    private AppDetailsViewModel mMainModel;

    public AppInfoViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        if (mTagCloudFuture != null) {
            mTagCloudFuture.cancel(true);
        }
        if (mAppInfoFuture != null) {
            mAppInfoFuture.cancel(true);
        }
        super.onCleared();
    }

    public void setMainModel(@NonNull AppDetailsViewModel mainModel) {
        mMainModel = mainModel;
    }

    public LiveData<CharSequence> getAppLabel() {
        return mAppLabel;
    }

    public LiveData<TagCloud> getTagCloud() {
        return mTagCloud;
    }

    public LiveData<AppInfo> getAppInfo() {
        return mAppInfo;
    }

    public LiveData<Pair<Integer, CharSequence>> getInstallExistingResult() {
        return mInstallExistingResult;
    }

    @AnyThread
    public void loadAppLabel(@NonNull ApplicationInfo applicationInfo) {
        ThreadUtils.postOnBackgroundThread(() -> {
            CharSequence appLabel = applicationInfo.loadLabel(getApplication().getPackageManager());
            mAppLabel.postValue(appLabel);
        });
    }

    @AnyThread
    public void loadTagCloud(@NonNull PackageInfo packageInfo, boolean isExternalApk) {
        if (mTagCloudFuture != null) {
            mTagCloudFuture.cancel(true);
        }
        mTagCloudFuture = ThreadUtils.postOnBackgroundThread(() -> loadTagCloudInternal(packageInfo, isExternalApk));
    }

    @WorkerThread
    private void loadTagCloudInternal(@NonNull PackageInfo packageInfo, boolean isExternalApk) {
        if (mMainModel == null) return;
        String packageName = packageInfo.packageName;
        int userId = mMainModel.getUserId();
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        TagCloud tagCloud = new TagCloud();
        try {
            HashMap<String, RuleType> trackerComponents = ComponentUtils.getTrackerComponentsForPackage(packageInfo);
            tagCloud.trackerComponents = new ArrayList<>(trackerComponents.size());
            for (String component : trackerComponents.keySet()) {
                ComponentRule componentRule = mMainModel.getComponentRule(component);
                if (componentRule == null) {
                    componentRule = new ComponentRule(packageName, component, trackerComponents.get(component),
                            Prefs.Blocking.getDefaultBlockingMethod());
                }
                tagCloud.trackerComponents.add(componentRule);
                tagCloud.areAllTrackersBlocked &= componentRule.isBlocked();
            }
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            tagCloud.isSystemApp = ApplicationInfoCompat.isSystemApp(applicationInfo);
            tagCloud.isUpdatedSystemApp = (applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            String codePath = PackageUtils.getHiddenCodePathOrDefault(packageName, applicationInfo.publicSourceDir);
            tagCloud.isSystemlessPath = !isExternalApk && MagiskUtils.isSystemlessPath(codePath);
            if (!isExternalApk && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                DomainVerificationUserState userState = DomainVerificationManagerCompat
                        .getDomainVerificationUserState(packageName, userId);
                if (userState != null) {
                    tagCloud.canOpenLinks = userState.isLinkHandlingAllowed();
                    if (!userState.getHostToStateMap().isEmpty()) {
                        tagCloud.hostsToOpen = userState.getHostToStateMap();
                    }
                }
            }
            tagCloud.splitCount = mMainModel.getSplitCount();
            tagCloud.isDebuggable = (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            tagCloud.isTestOnly = ApplicationInfoCompat.isTestOnly(applicationInfo);
            tagCloud.hasCode = (applicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) != 0;
            tagCloud.hasRequestedLargeHeap = (applicationInfo.flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0;
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            tagCloud.runningServices = ActivityManagerCompat.getRunningServices(packageName, userId);
            tagCloud.isForceStopped = ApplicationInfoCompat.isStopped(applicationInfo);
            tagCloud.isAppEnabled = applicationInfo.enabled;
            tagCloud.isAppSuspended = ApplicationInfoCompat.isSuspended(applicationInfo);
            tagCloud.isAppHidden = ApplicationInfoCompat.isHidden(applicationInfo);
            if (ThreadUtils.isInterrupted()) {
                return;
            }
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
            tagCloud.isMagiskHideEnabled = !isExternalApk && magiskHideEnabled;
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
            tagCloud.isMagiskDenyListEnabled = !isExternalApk && magiskDenyListEnabled;
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            List<DebloatObject> debloatObjects = StaticDataset.getDebloatObjects();
            for (DebloatObject debloatObject : debloatObjects) {
                if (packageName.equals(debloatObject.packageName)) {
                    tagCloud.isBloatware = true;
                    break;
                }
            }
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            try (ZipFile zipFile = new ZipFile(applicationInfo.publicSourceDir)) {
                Boolean isXposedModule = XposedModuleInfo.isXposedModule(applicationInfo, zipFile);
                if (!Boolean.FALSE.equals(isXposedModule)) {
                    tagCloud.xposedModuleInfo = new XposedModuleInfo(applicationInfo, isXposedModule == null ? null : zipFile);
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
            tagCloud.canWriteAndExecute = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && applicationInfo.targetSdkVersion < Build.VERSION_CODES.Q;
            tagCloud.hasKeyStoreItems = KeyStoreUtils.hasKeyStore(applicationInfo.uid);
            tagCloud.hasMasterKeyInKeyStore = KeyStoreUtils.hasMasterKey(applicationInfo.uid);
            tagCloud.usesPlayAppSigning = PackageUtils.usesPlayAppSigning(applicationInfo);
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            tagCloud.backups = BackupUtils.getBackupMetadataFromDbNoLockValidate(packageName);
            if (!isExternalApk) {
                tagCloud.isBatteryOptimized = DeviceIdleManagerCompat.isBatteryOptimizedApp(packageName);
            } else {
                tagCloud.isBatteryOptimized = true;
            }
            if (!isExternalApk && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_NETWORK_POLICY)) {
                tagCloud.netPolicies = ExUtils.requireNonNullElse(() -> NetworkPolicyManagerCompat.getUidPolicy(applicationInfo.uid), 0);
            } else {
                tagCloud.netPolicies = 0;
            }
            if (!isExternalApk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    tagCloud.ssaid = new SsaidSettings(userId)
                            .getSsaid(packageName, applicationInfo.uid);
                    if (TextUtils.isEmpty(tagCloud.ssaid)) tagCloud.ssaid = null;
                } catch (IOException ignore) {
                }
            }
            if (!isExternalApk) {
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
                List<UriManager.UriGrant> uriGrants = new UriManager().getGrantedUris(packageName);
                if (uriGrants != null) {
                    Iterator<UriManager.UriGrant> uriGrantIterator = uriGrants.listIterator();
                    UriManager.UriGrant uriGrant;
                    while (uriGrantIterator.hasNext()) {
                        uriGrant = uriGrantIterator.next();
                        if (uriGrant.targetUserId != userId) {
                            uriGrantIterator.remove();
                        }
                    }
                    tagCloud.uriGrants = uriGrants;
                }
            }
            if (ApplicationInfoCompat.isStaticSharedLibrary(applicationInfo)) {
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
                List<String> staticSharedLibraryNames = new ArrayList<>();
                // Check for packages by the same packagename
                List<ApplicationInfo> appList;
                try {
                    appList = PackageManagerCompat.getInstalledApplications(PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
                    for (ApplicationInfo info : appList) {
                        if (info.packageName.equals(packageName)) {
                            staticSharedLibraryNames.add(info.processName);
                        }
                    }
                } catch (Throwable ignore) {
                    staticSharedLibraryNames.add(applicationInfo.processName);
                }
                tagCloud.staticSharedLibraryNames = staticSharedLibraryNames.toArray(new String[0]);
            }
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            mTagCloud.postValue(tagCloud);
        } catch (Throwable th) {
            // Unknown behaviour
            ThreadUtils.postOnMainThread(() -> {
                // Throw Runtime exception in main thread to crash the app
                throw new RuntimeException(th);
            });
        }
    }

    @AnyThread
    public void loadAppInfo(@NonNull PackageInfo packageInfo, boolean isExternalApk) {
        if (mAppInfoFuture != null) {
            mAppInfoFuture.cancel(true);
        }
        mAppInfoFuture = ThreadUtils.postOnBackgroundThread(() -> loadAppInfoInternal(packageInfo, isExternalApk));
    }

    @WorkerThread
    private void loadAppInfoInternal(@NonNull PackageInfo packageInfo, boolean isExternalApk) {
        String packageName = packageInfo.packageName;
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        int userId = UserHandleHidden.getUserId(applicationInfo.uid);
        PackageManager pm = getApplication().getPackageManager();
        AppInfo appInfo = new AppInfo();
        try {
            if (!isExternalApk) {
                // Set source dir
                appInfo.sourceDir = new File(applicationInfo.publicSourceDir).getParent();
                // Set data dirs
                appInfo.dataDir = applicationInfo.dataDir;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appInfo.dataDeDir = applicationInfo.deviceProtectedDataDir;
                }
                // Set directories
                appInfo.extDataDirs = new ArrayList<>();
                OsEnvironment.UserEnvironment ue = OsEnvironment.getUserEnvironment(userId);
                Path[] externalDataDirs = ue.buildExternalStorageAppDataDirs(packageName);
                for (Path externalDataDir : externalDataDirs) {
                    Path accessiblePath = Paths.getAccessiblePath(externalDataDir);
                    if (accessiblePath.exists()) {
                        appInfo.extDataDirs.add(Objects.requireNonNull(accessiblePath.getFilePath()));
                    }
                }
                // Set JNI dir
                if (Paths.exists(applicationInfo.nativeLibraryDir)) {
                    appInfo.jniDir = applicationInfo.nativeLibraryDir;
                }
                boolean hasUsageAccess = FeatureController.isUsageAccessEnabled() && SelfPermissions.checkUsageStatsPermission();
                if (hasUsageAccess) {
                    // Net statistics
                    appInfo.dataUsage = AppUsageStatsManager.getDataUsageForPackage(getApplication(),
                            applicationInfo.uid, UsageUtils.USAGE_LAST_BOOT);
                    // Set sizes
                    appInfo.sizeInfo = PackageUtils.getPackageSizeInfo(getApplication(), packageName, userId,
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? applicationInfo.storageUuid : null);
                }
                // Set installer app
                String installerPackageName = PackageManagerCompat.getInstallerPackageName(packageName, userId);
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
            mAppInfo.postValue(appInfo);
        } catch (Throwable th) {
            // Unknown behaviour
            ThreadUtils.postOnMainThread(() -> {
                // Throw Runtime exception in main thread to crash the app
                throw new RuntimeException(th);
            });
        }
    }

    public void installExisting(@NonNull String packageName, @UserIdInt int userId) {
        mExecutor.submit(() -> {
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
                            getAppLabel().getValue(), blockingPackage));
                    if (statusMessage != null) {
                        sb.append("\n\n").append(statusMessage);
                    }
                    mInstallExistingResult.postValue(new Pair<>(result, sb));
                }
            });
            installer.installExisting(packageName, userId);
        });
    }

    public static class TagCloud {
        public List<ComponentRule> trackerComponents;
        public boolean areAllTrackersBlocked = true;
        public boolean isSystemApp;
        public boolean isSystemlessPath;
        public boolean isUpdatedSystemApp;
        public boolean canOpenLinks;
        /**
         * Hosts that can be opened by the app (Android 12+). State is one of {@link DomainVerificationUserState#DOMAIN_STATE_NONE},
         * {@link DomainVerificationUserState#DOMAIN_STATE_SELECTED}, {@link DomainVerificationUserState#DOMAIN_STATE_VERIFIED}.
         */
        public Map<String, Integer> hostsToOpen;
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
        public boolean isBloatware;
        @Nullable
        public XposedModuleInfo xposedModuleInfo;
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
        @Nullable
        public String[] staticSharedLibraryNames;
    }

    public static class AppInfo {
        // Paths & dirs
        @Nullable
        public String sourceDir;
        @Nullable
        public String dataDir;
        @Nullable
        public String dataDeDir;
        public List<String> extDataDirs = Collections.emptyList();
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
