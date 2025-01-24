// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import androidx.annotation.NonNull;

public final class FilterOptions {
    @NonNull
    public static FilterOption create(@NonNull String filterName) {
        switch (filterName) {
            case "apk_size": return new ApkSizeOption();
            case "app_label": return new AppLabelOption();
            case "app_type": return new AppTypeOption();
            case "backup": return new BackupOption();
            case "bloatware": return new BloatwareOption();
            case "cache_size": return new CacheSizeOption();
            case "compile_sdk": return new CompileSdkOption();
            case "components": return new ComponentsOption();
            case "data_size": return new DataSizeOption();
            case "data_usage": return new DataUsageOption();
            case "freeze_unfreeze": return new FreezeOption();
            case "installed": return new InstalledOption();
            case "installer": return new InstallerOption();
            case "last_update": return new LastUpdateOption();
            case "min_sdk": return new MinSdkOption();
            case "permissions": return new PermissionsOption();
            case "pkg_name": return new PackageNameOption();
            case "running_apps": return new RunningAppsOption();
            case "screen_time": return new ScreenTimeOption();
            case "shared_uid": return new SharedUidOption();
            case "signature": return new SignatureOption();
            case "target_sdk": return new TargetSdkOption();
            case "times_opened": return new TimesOpenedOption();
            case "total_size": return new TotalSizeOption();
            case "trackers": return new TrackersOption();
            case "version_name": return new VersionNameOption();
        }
        throw new IllegalArgumentException("Invalid filter: " + filterName);
    }
}
