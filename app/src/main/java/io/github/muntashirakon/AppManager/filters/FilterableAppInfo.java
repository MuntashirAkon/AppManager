// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.app.ActivityManager;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.pm.PackageInfoCompat;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import aosp.libcore.util.EmptyArray;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.InstallSourceInfoCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.debloat.DebloatObject;
import io.github.muntashirakon.AppManager.filters.options.ComponentsOption;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.ssaid.SsaidSettings;
import io.github.muntashirakon.AppManager.types.PackageSizeInfo;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.PackageUsageInfo;
import io.github.muntashirakon.AppManager.usage.UsageUtils;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class FilterableAppInfo {
    private final PackageInfo mPackageInfo;
    private final ApplicationInfo mApplicationInfo;
    private final int mUserId;
    private final PackageManager mPm;

    private String mAppLabel;
    @Nullable
    private String mSsaid;
    private InstallSourceInfoCompat mInstallerInfo;
    @Nullable
    private SignerInfo mSignerInfo;
    private String[] mSignatureSubjectLines;
    private String[] mSignatureSha256Checksums;
    private Map<ComponentInfo, Integer> mAllComponents;
    private Map<ComponentInfo, Integer> mTrackerComponents;
    private String[] mUsedPermissions;
    private Backup[] mBackups;
    private List<AppOpsManagerCompat.OpEntry> mAppOpEntries;
    @Nullable
    private PackageSizeInfo mPackageSizeInfo;
    private PackageUsageInfo mPackageUsageInfo;
    private AppUsageStatsManager.DataUsage mDataUsage;
    private DebloatObject mBloatwareInfo;

    public FilterableAppInfo(@NonNull PackageInfo packageInfo) {
        mPackageInfo = packageInfo;
        mApplicationInfo = packageInfo.applicationInfo;
        mUserId = UserHandleHidden.getUserId(mApplicationInfo.uid);
        mPm = ContextUtils.getContext().getPackageManager();
    }

    @NonNull
    public PackageInfo getPackageInfo() {
        return mPackageInfo;
    }

    @NonNull
    public ApplicationInfo getApplicationInfo() {
        return mApplicationInfo;
    }

    @NonNull
    public String getPackageName() {
        return mPackageInfo.packageName;
    }

    @NonNull
    public String getAppLabel() {
        if (mAppLabel == null) {
            mAppLabel = mApplicationInfo.loadLabel(mPm).toString();
        }
        return mAppLabel;
    }

    public String getVersionName() {
        return mPackageInfo.versionName;
    }

    public long getVersionCode() {
        return PackageInfoCompat.getLongVersionCode(mPackageInfo);
    }

    public long getFirstInstallTime() {
        return mPackageInfo.firstInstallTime;
    }

    public long getLastUpdateTime() {
        return mPackageInfo.lastUpdateTime;
    }

    public int getTargetSdk() {
        return mApplicationInfo.targetSdkVersion;
    }

    @RequiresApi(Build.VERSION_CODES.S)
    public int getCompileSdk() {
        return mApplicationInfo.compileSdkVersion;
    }

    @RequiresApi(Build.VERSION_CODES.N)
    public int getMinSdk() {
        return mApplicationInfo.minSdkVersion;
    }

    public Backup[] getBackups() {
        if (mBackups == null) {
            mBackups = BackupUtils.getBackupMetadataFromDbNoLockValidate(getPackageName()).toArray(new Backup[0]);
        }
        return mBackups;
    }

    public boolean isRunning() {
        for (ActivityManager.RunningAppProcessInfo info : ActivityManagerCompat.getRunningAppProcesses()) {
            if (ArrayUtils.contains(info.pkgList, mPackageInfo.packageName)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
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

    public List<AppOpsManagerCompat.OpEntry> getAppOps() {
        if (mAppOpEntries == null && isInstalled()) {
            List<AppOpsManagerCompat.PackageOps> packageOps = ExUtils.exceptionAsNull(() -> new AppOpsManagerCompat().getOpsForPackage(mApplicationInfo.uid, getPackageName(), null));
            if (packageOps != null && packageOps.size() == 1) {
                mAppOpEntries = packageOps.get(0).getOps();
            }
        } else mAppOpEntries = Collections.emptyList();
        return mAppOpEntries;
    }

    public Map<ComponentInfo, Integer> getAllComponents() {
        if (mAllComponents == null) {
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
    public String[] getAllPermissions() {
        if (mUsedPermissions == null) {
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
            mUsedPermissions = usedPermissions.toArray(new String[0]);
        }
        return mUsedPermissions;
    }

    @NonNull
    public FeatureInfo[] getAllRequestedFeatures() {
        return ArrayUtils.defeatNullable(FeatureInfo.class, mPackageInfo.reqFeatures);
    }

    public boolean isInstalled() {
        return ApplicationInfoCompat.isInstalled(mApplicationInfo);
    }

    public boolean isFrozen() {
        return !isEnabled() || isSuspended() || isHidden();
    }

    public boolean isStopped() {
        return ApplicationInfoCompat.isStopped(mApplicationInfo);
    }

    public boolean isTestOnly() {
        return ApplicationInfoCompat.isTestOnly(mApplicationInfo);
    }

    public boolean isDebuggable() {
        return (mApplicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    public boolean isSystemApp() {
        return ApplicationInfoCompat.isSystemApp(mApplicationInfo);
    }

    public boolean hasCode() {
        return (mApplicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) != 0;
    }

    public boolean isPersistent() {
        return (mApplicationInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0;
    }

    public boolean isUpdatedSystemApp() {
        return (mApplicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    public boolean backupAllowed() {
        return (mApplicationInfo.flags & ApplicationInfo.FLAG_ALLOW_BACKUP) != 0;
    }

    public boolean installedInExternalStorage() {
        return (mApplicationInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0;
    }

    public boolean requestedLargeHeap() {
        return (mApplicationInfo.flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0;
    }

    public boolean supportsRTL() {
        return (mApplicationInfo.flags & ApplicationInfo.FLAG_SUPPORTS_RTL) != 0;
    }

    public boolean dataOnlyApp() {
        return (mApplicationInfo.flags & ApplicationInfo.FLAG_IS_DATA_ONLY) != 0;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    public boolean usesHttp() {
        return (mApplicationInfo.flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0;
    }

    public boolean isPrivileged() {
        return ApplicationInfoCompat.isPrivileged(mApplicationInfo);
    }

    @NonNull
    public String getSsaid() {
        if (mSsaid == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                mSsaid = new SsaidSettings(mUserId).getSsaid(getPackageName(), mApplicationInfo.uid);
            } catch (IOException ignore) {
            }
        }
        if (mSsaid == null) {
            mSsaid = "";
        }
        return mSsaid;
    }

    public boolean hasDomainUrls() {
        return ApplicationInfoCompat.hasDomainUrls(mApplicationInfo);
    }

    public boolean hasStaticSharedLibrary() {
        return ApplicationInfoCompat.isStaticSharedLibrary(mApplicationInfo);
    }

    public boolean isHidden() {
        return ApplicationInfoCompat.isHidden(mApplicationInfo);
    }

    public boolean isSuspended() {
        return ApplicationInfoCompat.isSuspended(mApplicationInfo);
    }

    public boolean isEnabled() {
        return mApplicationInfo.enabled;
    }

    public String getSharedUserId() {
        return mPackageInfo.sharedUserId;
    }

    private void fetchPackageSizeInfo() {
        if (mPackageSizeInfo == null && isInstalled()) {
            mPackageSizeInfo = PackageUtils.getPackageSizeInfo(ContextUtils.getContext(), getPackageName(), mUserId, null);
        }
    }

    public long getTotalSize() {
        fetchPackageSizeInfo();
        return mPackageSizeInfo != null ? mPackageSizeInfo.getTotalSize() : 0;
    }

    public long getApkSize() {
        fetchPackageSizeInfo();
        return mPackageSizeInfo != null ? (mPackageSizeInfo.codeSize + mPackageSizeInfo.obbSize) : 0;
    }

    public long getCacheSize() {
        fetchPackageSizeInfo();
        return mPackageSizeInfo != null ? mPackageSizeInfo.cacheSize : 0;
    }

    public long getDataSize() {
        fetchPackageSizeInfo();
        return mPackageSizeInfo != null ? (mPackageSizeInfo.dataSize + mPackageSizeInfo.mediaSize + mPackageSizeInfo.cacheSize) : 0;
    }

    public AppUsageStatsManager.DataUsage getDataUsage() {
        if (mDataUsage == null && isInstalled()) {
            mDataUsage = AppUsageStatsManager.getDataUsageForPackage(ContextUtils.getContext(), mApplicationInfo.uid, UsageUtils.USAGE_WEEKLY);
        } else mDataUsage = new AppUsageStatsManager.DataUsage(0, 0);
        return mDataUsage;
    }

    private void fetchPackageUsageInfo() {
        if (mPackageUsageInfo == null && isInstalled()) {
            try {
                mPackageUsageInfo = AppUsageStatsManager.getInstance().getUsageStatsForPackage(getPackageName(), UsageUtils.USAGE_WEEKLY, mUserId);
            } catch (Exception ignore) {
            }
        }
    }

    public int getTimesOpened() {
        fetchPackageUsageInfo();
        return mPackageUsageInfo != null ? mPackageUsageInfo.timesOpened : 0;
    }

    public long getTotalScreenTime() {
        fetchPackageUsageInfo();
        return mPackageUsageInfo != null ? mPackageUsageInfo.screenTime : 0;
    }

    @Nullable
    public SignerInfo fetchSignerInfo() {
        if (mSignerInfo == null) {
            mSignerInfo = PackageUtils.getSignerInfo(mPackageInfo, !isInstalled());
        }
        return mSignerInfo;
    }

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

    @Nullable
    public InstallSourceInfoCompat getInstallerInfo() {
        if (mInstallerInfo == null && isInstalled()) {
            try {
                mInstallerInfo = PackageManagerCompat.getInstallSourceInfo(getPackageName(), mUserId);
            } catch (RemoteException ignore) {
            }
        }
        return mInstallerInfo;
    }

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
