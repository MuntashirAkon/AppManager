// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.GET_SIGNING_CERTIFICATES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.PackageUsageInfo;
import io.github.muntashirakon.AppManager.usage.UsageUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public final class FilteringUtils {
    @NonNull
    @WorkerThread
    public static List<FilterableAppInfo> loadFilterableAppInfo(@NonNull int[] userIds) {
        List<FilterableAppInfo> filterableAppInfoList = new ArrayList<>();
        boolean hasUsageAccess = FeatureController.isUsageAccessEnabled() && SelfPermissions.checkUsageStatsPermission();
        for (int userId : userIds) {
            if (ThreadUtils.isInterrupted()) return Collections.emptyList();

            if (!SelfPermissions.checkCrossUserPermission(userId, false)) {
                // No support for cross user
                continue;
            }

            // List packages
            List<PackageInfo> packageInfoList = PackageManagerCompat.getInstalledPackages(
                    PackageManager.GET_META_DATA | GET_SIGNING_CERTIFICATES
                            | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS
                            | PackageManager.GET_PROVIDERS | PackageManager.GET_SERVICES
                            | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_PERMISSIONS
                            | PackageManager.GET_URI_PERMISSION_PATTERNS
                            | MATCH_DISABLED_COMPONENTS | MATCH_UNINSTALLED_PACKAGES
                            | MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
            // List usages
            Map<String, PackageUsageInfo> packageUsageInfoList = new HashMap<>();
            if (hasUsageAccess) {
                List<PackageUsageInfo> usageInfoList = ExUtils.exceptionAsNull(() -> AppUsageStatsManager.getInstance()
                        .getUsageStats(UsageUtils.USAGE_WEEKLY, userId));
                if (usageInfoList != null) {
                    for (PackageUsageInfo info : usageInfoList) {
                        if (ThreadUtils.isInterrupted()) return Collections.emptyList();
                        packageUsageInfoList.put(info.packageName, info);
                    }
                }
            }
            for (PackageInfo packageInfo : packageInfoList) {
                // Interrupt thread on request
                if (ThreadUtils.isInterrupted()) return Collections.emptyList();
                filterableAppInfoList.add(new FilterableAppInfo(packageInfo, packageUsageInfoList.get(packageInfo.packageName)));
            }
        }
        return filterableAppInfoList;
    }
}
