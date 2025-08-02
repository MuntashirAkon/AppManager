// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.content.pm.ComponentInfo;
import android.content.pm.FeatureInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.InstallSourceInfoCompat;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.debloat.DebloatObject;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;

public interface IFilterableAppInfo {
    @NonNull
    String getPackageName();

    int getUserId();

    @NonNull
    String getAppLabel();

    @NonNull
    Drawable getAppIcon();

    @Nullable
    String getVersionName();

    long getVersionCode();

    long getFirstInstallTime();

    long getLastUpdateTime();

    int getTargetSdk();

    @RequiresApi(Build.VERSION_CODES.S)
    int getCompileSdk();

    @RequiresApi(Build.VERSION_CODES.N)
    int getMinSdk();

    @NonNull
    Backup[] getBackups();

    boolean isRunning();

    @NonNull
    Map<ComponentInfo, Integer> getTrackerComponents();

    @NonNull
    List<AppOpsManagerCompat.OpEntry> getAppOps();

    @NonNull
    Map<ComponentInfo, Integer> getAllComponents();

    @NonNull
    List<String> getAllPermissions();

    @NonNull
    FeatureInfo[] getAllRequestedFeatures();

    boolean isInstalled();

    boolean isFrozen();

    int getFreezeFlags();

    boolean isStopped();

    boolean isTestOnly();

    boolean isDebuggable();

    int getAppTypeFlags();

    boolean isSystemApp();

    boolean hasCode();

    boolean isPersistent();

    boolean isUpdatedSystemApp();

    boolean backupAllowed();

    boolean installedInExternalStorage();

    boolean requestedLargeHeap();

    boolean supportsRTL();

    boolean dataOnlyApp();

    @RequiresApi(Build.VERSION_CODES.M)
    boolean usesHttp();

    boolean isPrivileged();

    @Nullable
    String getSsaid();

    boolean hasDomainUrls();

    boolean hasStaticSharedLibrary();

    boolean isHidden();

    boolean isSuspended();

    boolean isEnabled();

    @Nullable
    String getSharedUserId();

    long getTotalSize();

    long getApkSize();

    long getCacheSize();

    long getDataSize();

    @NonNull
    AppUsageStatsManager.DataUsage getDataUsage();

    int getTimesOpened();

    long getTotalScreenTime();

    long getLastUsedTime();

    @Nullable
    SignerInfo fetchSignerInfo();

    @NonNull
    String[] getSignatureSubjectLines();

    @NonNull
    String[] getSignatureSha256Checksums();

    @Nullable
    InstallSourceInfoCompat getInstallerInfo();

    @Nullable
    DebloatObject getBloatwareInfo();
}
