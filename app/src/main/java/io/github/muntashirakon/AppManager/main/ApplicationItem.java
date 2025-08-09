// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.GET_SIGNING_CERTIFICATES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.Manifest;
import android.app.ActivityManager;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

import java.io.InputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import aosp.libcore.util.EmptyArray;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.backup.BackupManager;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.DeviceIdleManagerCompat;
import io.github.muntashirakon.AppManager.compat.InstallSourceInfoCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.compat.SensorServiceCompat;
import io.github.muntashirakon.AppManager.compat.UsageStatsManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.debloat.DebloatObject;
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.filters.options.ComponentsOption;
import io.github.muntashirakon.AppManager.filters.options.FreezeOption;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.types.PackageSizeInfo;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.PackageUsageInfo;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.io.Path;

/**
 * Stores an application info
 */
public class ApplicationItem extends PackageItemInfo implements IFilterableAppInfo {
    /**
     * Version name
     */
    public String versionName;
    /**
     * Version code
     */
    public long versionCode;
    /**
     * Backup info
     */
    @Nullable
    public Backup backup;
    /**
     * Application flags.
     * See {@link android.content.pm.ApplicationInfo#flags}
     */
    public int flags = 0;
    /**
     * Kernel user id.
     * See {@link android.content.pm.ApplicationInfo#uid}
     */
    public int uid = 0;
    /**
     * Shared user id name.
     * See {@link android.content.pm.PackageInfo#sharedUserId}
     */
    @Nullable
    public String sharedUserId;
    /**
     * Application label (or name)
     */
    public String label;
    /**
     * True if debuggable, false otherwise
     */
    public boolean debuggable = false;
    /**
     * First install time
     */
    public long firstInstallTime = 0L;
    /**
     * Last update time
     */
    public Long lastUpdateTime = 0L;
    /**
     * Target SDK version
     */
    public Integer targetSdk;
    /**
     * Issuer and signature
     */
    @Nullable
    public Pair<String, String> sha;
    /**
     * Blocked components count
     */
    public Integer blockedCount = 0;
    public Integer trackerCount = 0;
    public Long lastActionTime = 0L;
    public Long dataUsage = 0L;
    public Long totalSize = 0L;
    public int openCount = 0;
    public Long screenTime = 0L;
    public Long lastUsageTime = 0L;
    /**
     * Whether the item is a user app (or system app)
     */
    public boolean isUser;
    /**
     * Whether the app is disabled
     */
    public boolean isDisabled;
    /**
     * Whether the app is currently running
     */
    public boolean isRunning = false;
    /**
     * Whether the app is installed
     */
    public boolean isInstalled = true;
    /**
     * Whether the app has any activities
     */
    public boolean hasActivities = false;
    /**
     * Whether the app has any splits
     */
    public boolean hasSplits = false;
    public boolean hasKeystore = false;
    public boolean usesSaf = false;
    public String ssaid = null;
    /**
     * Whether the item is selected
     */
    public boolean isSelected = false;

    @NonNull
    public int[] userIds = EmptyArray.INT;

    // Other info
    public boolean isStopped;
    public boolean isSystem;
    public boolean isPersistent;
    public boolean usesCleartextTraffic;
    public boolean canReadLogs;
    public boolean allowClearingUserData;
    public boolean isAppInactive;
    public String uidOrAppIds;
    public String issuerShortName;
    public String versionTag;
    public String appTypePostfix;
    public String sdkString;
    public long diffInstallUpdateInDays;
    public long lastBackupDays;
    public StringBuilder backupFlagsStr;

    // Fields below only required for filters, hence, loaded dynamically
    @Nullable
    private PackageInfo mPackageInfo;
    @Nullable
    private PackageUsageInfo mPackageUsageInfo;
    @Nullable
    private ApplicationInfo mApplicationInfo;
    private InstallSourceInfoCompat mInstallerInfo;
    @Nullable
    private SignerInfo mSignerInfo;
    private String[] mSignatureSubjectLines;
    private String[] mSignatureSha256Checksums;
    private Map<ComponentInfo, Integer> mAllComponents;
    private Map<ComponentInfo, Integer> mTrackerComponents;
    private List<String> mUsedPermissions;
    private Backup[] mBackups;
    private List<AppOpsManagerCompat.OpEntry> mAppOpEntries;
    @Nullable
    private PackageSizeInfo mPackageSizeInfo;
    @Nullable
    private AppUsageStatsManager.DataUsage mDataUsage;
    @Nullable
    private DebloatObject mBloatwareInfo;
    private Integer mFreezeFlags = null;
    private Integer mUserId = null;
    private Boolean mUsesSensors = null;
    private Boolean mBatteryOptEnabled = null;
    private Boolean mHasKeystoreItems = null;
    private Integer mRulesCount = null;

    public ApplicationItem() {
        super();
    }

    public void generateOtherInfo() {
        isStopped = (flags & ApplicationInfo.FLAG_STOPPED) != 0;
        isSystem = (flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        isPersistent = (flags & ApplicationInfo.FLAG_PERSISTENT) != 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            usesCleartextTraffic = (flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0;
        }
        for (int userId : userIds) {
            canReadLogs |= (PermissionCompat.checkPermission(Manifest.permission.READ_LOGS, packageName, userId) == PackageManager.PERMISSION_GRANTED);
            isAppInactive |= UsageStatsManagerCompat.isAppInactive(packageName, userId);
        }
        allowClearingUserData = (flags & ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA) != 0;
        // UID
        if (userIds.length > 1) {
            int appId = UserHandleHidden.getAppId(uid);
            uidOrAppIds = userIds.length + "+" + appId;
        } else if (userIds.length == 1) {
            uidOrAppIds = String.valueOf(uid);
        } else uidOrAppIds = "";
        // Cert short name
        if (sha != null) {
            try {
                issuerShortName = "CN=" + (sha.first).split("CN=", 2)[1];
            } catch (ArrayIndexOutOfBoundsException e) {
                issuerShortName = sha.first;
            }
            if (TextUtils.isEmpty(sha.second)) {
                sha = null;
            }
        }
        // Version info
        versionTag = versionName;
        if (isInstalled && (flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) == 0)
            versionTag = "_" + versionTag;
        if ((flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) versionTag = "debug" + versionTag;
        if ((flags & ApplicationInfo.FLAG_TEST_ONLY) != 0) versionTag = "~" + versionTag;
        // App type flags
        appTypePostfix = "";
        if ((flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0) appTypePostfix += "#";
        if ((flags & ApplicationInfo.FLAG_SUSPENDED) != 0) appTypePostfix += "°";
        if ((flags & ApplicationInfo.FLAG_MULTIARCH) != 0) appTypePostfix += "X";
        if ((flags & ApplicationInfo.FLAG_HAS_CODE) == 0) appTypePostfix += "0";
        if ((flags & ApplicationInfo.FLAG_VM_SAFE_MODE) != 0) appTypePostfix += "?";
        // Sdk
        if (targetSdk != null && targetSdk > 0) {
            sdkString = "SDK " + targetSdk;
        }
        diffInstallUpdateInDays = TimeUnit.DAYS.convert(lastUpdateTime - firstInstallTime, TimeUnit.MILLISECONDS);
        // Backup
        if (backup != null) {
            lastBackupDays = TimeUnit.DAYS.convert(System.currentTimeMillis() - backup.backupTime, TimeUnit.MILLISECONDS);
            backupFlagsStr = new StringBuilder();
            if (backup.getFlags().backupApkFiles()) backupFlagsStr.append("apk");
            if (backup.getFlags().backupData()) {
                if (backupFlagsStr.length() > 0) backupFlagsStr.append("+");
                backupFlagsStr.append("data");
            }
            if (backup.hasRules) {
                if (backupFlagsStr.length() > 0) backupFlagsStr.append("+");
                backupFlagsStr.append("rules");
            }
        }
    }

    @WorkerThread
    @Override
    public Drawable loadIcon(PackageManager pm) {
        fetchPackageInfo();
        if (mApplicationInfo != null) {
            return mApplicationInfo.loadIcon(pm);
        }
        // App not installed
        if (backup != null) {
            try {
                Path iconFile = backup.getBackupPath().findFile(BackupManager.ICON_FILE);
                if (iconFile.exists()) {
                    try (InputStream is = iconFile.openInputStream()) {
                        Drawable drawable = Drawable.createFromStream(is, name);
                        if (drawable != null) {
                            return drawable;
                        }
                    }
                }
            } catch (Throwable ignore) {
            }
        }
        return pm.getDefaultActivityIcon();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApplicationItem)) return false;
        ApplicationItem item = (ApplicationItem) o;
        return packageName.equals(item.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName);
    }

    @NonNull
    @Override
    public String getPackageName() {
        return packageName;
    }

    @Override
    public int getUserId() {
        if (mUserId != null) {
            return mUserId;
        }
        if (userIds.length > 0) {
            int myUserId = UserHandleHidden.myUserId();
            for (int userId : userIds) {
                // Prefer current user
                if (userId == myUserId) {
                    mUserId = myUserId;
                    break;
                }
            }
            if (mUserId == null) {
                // Failed, assign the first user
                mUserId = userIds[0];
            }
            return mUserId;
        }
        return -1;
    }

    public void setPackageUsageInfo(@Nullable PackageUsageInfo packageUsageInfo) {
        mPackageUsageInfo = packageUsageInfo;
    }

    private void fetchPackageInfo() {
        int userId = getUserId();
        if (userId >= 0 && mPackageInfo == null) {
            try {
                mPackageInfo = PackageManagerCompat.getPackageInfo(packageName,
                        PackageManager.GET_META_DATA | GET_SIGNING_CERTIFICATES
                                | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS
                                | PackageManager.GET_PROVIDERS | PackageManager.GET_SERVICES
                                | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_PERMISSIONS
                                | PackageManager.GET_URI_PERMISSION_PATTERNS
                                | MATCH_DISABLED_COMPONENTS | MATCH_UNINSTALLED_PACKAGES
                                | MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
                mApplicationInfo = Objects.requireNonNull(mPackageInfo.applicationInfo);
            } catch (RemoteException | PackageManager.NameNotFoundException ignore) {
            }
        }
    }

    @NonNull
    @Override
    public String getAppLabel() {
        return label;
    }

    @NonNull
    @Override
    public Drawable getAppIcon() {
        return loadIcon(ContextUtils.getContext().getPackageManager());
    }

    @Nullable
    @Override
    public String getVersionName() {
        return versionName;
    }

    @Override
    public long getVersionCode() {
        return versionCode;
    }

    @Override
    public long getFirstInstallTime() {
        return firstInstallTime;
    }

    @Override
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    @Override
    public int getTargetSdk() {
        return targetSdk;
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.S)
    public int getCompileSdk() {
        fetchPackageInfo();
        if (mApplicationInfo != null) {
            return mApplicationInfo.compileSdkVersion;
        }
        return targetSdk;
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.N)
    public int getMinSdk() {
        fetchPackageInfo();
        if (mApplicationInfo != null) {
            return mApplicationInfo.minSdkVersion;
        }
        return 0;
    }

    @NonNull
    @Override
    public Backup[] getBackups() {
        if (mBackups == null) {
            mBackups = BackupUtils.getBackupMetadataFromDbNoLockValidate(getPackageName()).toArray(new Backup[0]);
        }
        return mBackups;
    }

    @Override
    public boolean isRunning() {
        for (ActivityManager.RunningAppProcessInfo info : ActivityManagerCompat.getRunningAppProcesses()) {
            if (ArrayUtils.contains(info.pkgList, getPackageName())) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    @Override
    public Map<ComponentInfo, Integer> getTrackerComponents() {
        if (mTrackerComponents == null) {
            Map<ComponentInfo, Integer> allComponents = getAllComponents();
            Map<ComponentInfo, Integer> trackerComponents = new LinkedHashMap<>();
            for (ComponentInfo itemInfo : allComponents.keySet()) {
                if (ComponentUtils.isTracker(itemInfo.name)) {
                    trackerComponents.put(itemInfo, allComponents.get(itemInfo));
                }
            }
            mTrackerComponents = trackerComponents;
        }
        return mTrackerComponents;
    }

    @NonNull
    @Override
    public List<AppOpsManagerCompat.OpEntry> getAppOps() {
        fetchPackageInfo();
        if (mApplicationInfo != null && mAppOpEntries == null && isInstalled()) {
            List<AppOpsManagerCompat.PackageOps> packageOps = ExUtils.exceptionAsNull(() -> new AppOpsManagerCompat().getOpsForPackage(mApplicationInfo.uid, getPackageName(), null));
            if (packageOps != null && packageOps.size() == 1) {
                mAppOpEntries = packageOps.get(0).getOps();
            }
        } else mAppOpEntries = Collections.emptyList();
        return mAppOpEntries;
    }

    @NonNull
    @Override
    public Map<ComponentInfo, Integer> getAllComponents() {
        fetchPackageInfo();
        if (mPackageInfo != null && mAllComponents == null) {
            Map<ComponentInfo, Integer> components = new LinkedHashMap<>();
            if (mPackageInfo.activities != null) {
                for (ActivityInfo info : mPackageInfo.activities) {
                    components.put(info, ComponentsOption.COMPONENT_TYPE_ACTIVITY);
                }
            }
            if (mPackageInfo.services != null) {
                for (ServiceInfo info : mPackageInfo.services) {
                    components.put(info, ComponentsOption.COMPONENT_TYPE_SERVICE);
                }
            }
            if (mPackageInfo.receivers != null) {
                for (ActivityInfo info : mPackageInfo.receivers) {
                    components.put(info, ComponentsOption.COMPONENT_TYPE_RECEIVER);
                }
            }
            if (mPackageInfo.providers != null) {
                for (ProviderInfo info : mPackageInfo.providers) {
                    components.put(info, ComponentsOption.COMPONENT_TYPE_PROVIDER);
                }
            }
            mAllComponents = components;
        }
        return mAllComponents;
    }

    @NonNull
    @Override
    public List<String> getAllPermissions() {
        fetchPackageInfo();
        if (mPackageInfo != null && mUsedPermissions == null) {
            Set<String> usedPermissions = new HashSet<>();
            if (mPackageInfo.requestedPermissions != null) {
                Collections.addAll(usedPermissions, mPackageInfo.requestedPermissions);
            }
            if (mPackageInfo.permissions != null) {
                for (PermissionInfo perm : mPackageInfo.permissions) {
                    usedPermissions.add(perm.name);
                }
            }
            if (mPackageInfo.activities != null) {
                for (ActivityInfo info : mPackageInfo.activities) {
                    if (info.permission != null) {
                        usedPermissions.add(info.permission);
                    }
                }
            }
            if (mPackageInfo.services != null) {
                for (ServiceInfo info : mPackageInfo.services) {
                    if (info.permission != null) {
                        usedPermissions.add(info.permission);
                    }
                }
            }
            if (mPackageInfo.receivers != null) {
                for (ActivityInfo info : mPackageInfo.receivers) {
                    if (info.permission != null) {
                        usedPermissions.add(info.permission);
                    }
                }
            }
            mUsedPermissions = new ArrayList<>(usedPermissions);
        }
        return mUsedPermissions;
    }

    @NonNull
    @Override
    public FeatureInfo[] getAllRequestedFeatures() {
        fetchPackageInfo();
        if (mPackageInfo != null) {
            return ArrayUtils.defeatNullable(FeatureInfo.class, mPackageInfo.reqFeatures);
        }
        return new FeatureInfo[0];
    }

    @Override
    public boolean isInstalled() {
        return isInstalled;
    }

    @Override
    public boolean isFrozen() {
        return !isEnabled() || isSuspended() || isHidden();
    }

    @Override
    public int getFreezeFlags() {
        if (mFreezeFlags != null) {
            return mFreezeFlags;
        }
        mFreezeFlags = 0;
        if (!isEnabled()) {
            mFreezeFlags |= FreezeOption.FREEZE_TYPE_DISABLED;
        }
        if (isHidden()) {
            mFreezeFlags |= FreezeOption.FREEZE_TYPE_HIDDEN;
        }
        if (isSuspended()) {
            mFreezeFlags |= FreezeOption.FREEZE_TYPE_SUSPENDED;
        }
        return mFreezeFlags;
    }

    @Override
    public boolean isStopped() {
        return isStopped;
    }

    @Override
    public boolean isTestOnly() {
        return (flags & ApplicationInfo.FLAG_TEST_ONLY) != 0;
    }

    @Override
    public boolean isDebuggable() {
        return debuggable;
    }

    @Override
    public boolean isSystemApp() {
        return isSystem;
    }

    @Override
    public boolean hasCode() {
        return (flags & ApplicationInfo.FLAG_HAS_CODE) != 0;
    }

    @Override
    public boolean isPersistent() {
        return isPersistent;
    }

    @Override
    public boolean isUpdatedSystemApp() {
        return (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    @Override
    public boolean backupAllowed() {
        return (flags & ApplicationInfo.FLAG_ALLOW_BACKUP) != 0;
    }

    @Override
    public boolean installedInExternalStorage() {
        return (flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0;
    }

    @Override
    public boolean requestedLargeHeap() {
        return (flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0;
    }

    @Override
    public boolean supportsRTL() {
        return (flags & ApplicationInfo.FLAG_SUPPORTS_RTL) != 0;
    }

    @Override
    public boolean dataOnlyApp() {
        return (flags & ApplicationInfo.FLAG_IS_DATA_ONLY) != 0;
    }

    @Override
    public boolean usesHttp() {
        return usesCleartextTraffic;
    }

    @Override
    public boolean isPrivileged() {
        fetchPackageInfo();
        if (mApplicationInfo != null) {
            return ApplicationInfoCompat.isPrivileged(mApplicationInfo);
        }
        return false;
    }

    @RequiresApi(Build.VERSION_CODES.P)
    public boolean usesSensors() {
        if (mUsesSensors == null) {
            if (isInstalled() && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_SENSORS)) {
                mUsesSensors = SensorServiceCompat.isSensorEnabled(getPackageName(), getUserId());
            } else mUsesSensors = isInstalled(); // Worse case: always true
        }
        return mUsesSensors;
    }

    @Override
    public boolean isBatteryOptEnabled() {
        if (mBatteryOptEnabled == null) {
            if (isInstalled()) {
                mBatteryOptEnabled = DeviceIdleManagerCompat.isBatteryOptimizedApp(getPackageName());
            } else mBatteryOptEnabled = true;
        }
        return mBatteryOptEnabled;
    }

    @Override
    public boolean hasKeyStoreItems() {
        fetchPackageInfo();
        if (mHasKeystoreItems == null) {
            if (mApplicationInfo != null && isInstalled()) {
                mHasKeystoreItems = KeyStoreUtils.hasKeyStore(mApplicationInfo.uid);
            } else mHasKeystoreItems = false;
        }
        return mHasKeystoreItems;
    }

    @Override
    public int getRuleCount() {
        if (mRulesCount == null) {
            mRulesCount = 0;
            for (int userId : userIds) {
                try (ComponentsBlocker cb = ComponentsBlocker.getInstance(getPackageName(), userId, false)) {
                    mRulesCount += cb.entryCount();
                }
            }
        }
        return mRulesCount;
    }

    @Nullable
    @Override
    public String getSsaid() {
        return ssaid;
    }

    @Override
    public boolean hasDomainUrls() {
        fetchPackageInfo();
        if (mApplicationInfo != null) {
            return ApplicationInfoCompat.hasDomainUrls(mApplicationInfo);
        }
        return false;
    }

    @Override
    public boolean hasStaticSharedLibrary() {
        fetchPackageInfo();
        if (mApplicationInfo != null) {
            return ApplicationInfoCompat.isStaticSharedLibrary(mApplicationInfo);
        }
        return false;
    }

    @Override
    public boolean isHidden() {
        fetchPackageInfo();
        if (mApplicationInfo != null) {
            return ApplicationInfoCompat.isHidden(mApplicationInfo);
        }
        return false;
    }

    @Override
    public boolean isSuspended() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return (flags & ApplicationInfo.FLAG_SUSPENDED) != 0;
        }
        // Not supported
        return false;
    }

    @Override
    public boolean isEnabled() {
        return !isDisabled;
    }

    @Nullable
    @Override
    public String getSharedUserId() {
        fetchPackageInfo();
        return mPackageInfo != null ? mPackageInfo.sharedUserId : sharedUserId;
    }

    private void fetchPackageSizeInfo() {
        int userId = getUserId();
        if (userId >= 0 && mPackageSizeInfo == null && isInstalled()) {
            mPackageSizeInfo = PackageUtils.getPackageSizeInfo(ContextUtils.getContext(), getPackageName(), userId, null);
        }
    }

    @Override
    public long getTotalSize() {
        fetchPackageSizeInfo();
        return mPackageSizeInfo != null ? mPackageSizeInfo.getTotalSize() : 0;
    }

    @Override
    public long getApkSize() {
        fetchPackageSizeInfo();
        return mPackageSizeInfo != null ? (mPackageSizeInfo.codeSize + mPackageSizeInfo.obbSize) : 0;
    }

    @Override
    public long getCacheSize() {
        fetchPackageSizeInfo();
        return mPackageSizeInfo != null ? mPackageSizeInfo.cacheSize : 0;
    }

    @Override
    public long getDataSize() {
        fetchPackageSizeInfo();
        return mPackageSizeInfo != null ? (mPackageSizeInfo.dataSize + mPackageSizeInfo.mediaSize + mPackageSizeInfo.cacheSize) : 0;
    }

    @Override
    @NonNull
    public AppUsageStatsManager.DataUsage getDataUsage() {
        if (mDataUsage == null && isInstalled()) {
            if (mPackageUsageInfo != null) {
                mDataUsage = AppUsageStatsManager.DataUsage.fromDataUsage(mPackageUsageInfo.mobileData, mPackageUsageInfo.wifiData);
            }
        }
        if (mDataUsage == null) {
            mDataUsage = AppUsageStatsManager.DataUsage.EMPTY;
        }
        return mDataUsage;
    }

    @Override
    public int getTimesOpened() {
        fetchPackageInfo();
        return mPackageUsageInfo != null ? mPackageUsageInfo.timesOpened : openCount;
    }

    @Override
    public long getTotalScreenTime() {
        fetchPackageInfo();
        return mPackageUsageInfo != null ? mPackageUsageInfo.screenTime : screenTime;
    }

    @Override
    public long getLastUsedTime() {
        fetchPackageInfo();
        return mPackageUsageInfo != null ? mPackageUsageInfo.lastUsageTime : lastUsageTime;
    }

    @Override
    @Nullable
    public SignerInfo fetchSignerInfo() {
        fetchPackageInfo();
        if (mPackageInfo != null && mSignerInfo == null) {
            mSignerInfo = PackageUtils.getSignerInfo(mPackageInfo, !isInstalled());
        }
        return mSignerInfo;
    }

    @Override
    @NonNull
    public String[] getSignatureSubjectLines() {
        fetchSignerInfo();
        if (mSignerInfo != null && mSignatureSubjectLines == null) {
            X509Certificate[] signatures = mSignerInfo.getAllSignerCerts();
            if (signatures != null) {
                mSignatureSubjectLines = new String[signatures.length];
                for (int i = 0; i < signatures.length; ++i) {
                    mSignatureSubjectLines[i] = signatures[i].getSubjectX500Principal().getName();
                }
            }
        }
        return mSignatureSubjectLines != null ? mSignatureSubjectLines : EmptyArray.STRING;
    }

    @Override
    @NonNull
    public String[] getSignatureSha256Checksums() {
        fetchSignerInfo();
        if (mSignerInfo != null && mSignatureSha256Checksums == null) {
            X509Certificate[] signatures = mSignerInfo.getAllSignerCerts();
            if (signatures != null) {
                mSignatureSha256Checksums = new String[signatures.length];
                for (int i = 0; i < signatures.length; ++i) {
                    try {
                        mSignatureSha256Checksums[i] = DigestUtils.getHexDigest(DigestUtils.SHA_256, signatures[i].getEncoded());
                    } catch (CertificateEncodingException e) {
                        mSignatureSha256Checksums[i] = "";
                    }
                }
            }
        }
        return mSignatureSha256Checksums != null ? mSignatureSha256Checksums : EmptyArray.STRING;
    }

    @Override
    @Nullable
    public InstallSourceInfoCompat getInstallerInfo() {
        int userId = getUserId();
        if (userId >= 0 && mInstallerInfo == null && isInstalled()) {
            try {
                mInstallerInfo = PackageManagerCompat.getInstallSourceInfo(getPackageName(), userId);
            } catch (RemoteException ignore) {
            }
        }
        return mInstallerInfo;
    }

    @Override
    @Nullable
    public DebloatObject getBloatwareInfo() {
        if (mBloatwareInfo == null) {
            for (DebloatObject debloatObject : StaticDataset.getDebloatObjects()) {
                if (getPackageName().equals(debloatObject.packageName)) {
                    mBloatwareInfo = debloatObject;
                    break;
                }
            }
        }
        return mBloatwareInfo;
    }
}
