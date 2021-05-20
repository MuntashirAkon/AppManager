// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig;

import android.content.ComponentName;
import android.content.pm.FeatureInfo;
import android.os.Build;
import android.util.ArrayMap;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

@WorkerThread
class SysConfigWrapper {
    @NonNull
    static List<SysConfigInfo> getSysConfigs(@NonNull @SysConfigType String type) {
        List<SysConfigInfo> list = new ArrayList<>();
        SystemConfig config = SystemConfig.getInstance();
        switch (type) {
            case SysConfigType.TYPE_GROUP: {
                int[] globalGids = config.getGlobalGids();
                if (globalGids != null) {
                    for (int gid : globalGids) {
                        list.add(new SysConfigInfo(SysConfigType.TYPE_GROUP, String.valueOf(gid), false));
                    }
                }
            }
            break;
            case SysConfigType.TYPE_PERMISSION: {
                Map<String, SystemConfig.PermissionEntry> permissionEntries = config.getPermissions();
                SystemConfig.PermissionEntry entry;
                for (String permission : permissionEntries.keySet()) {
                    entry = permissionEntries.get(permission);
                    if (entry == null) continue;
                    SysConfigInfo info = new SysConfigInfo(SysConfigType.TYPE_PERMISSION, permission, false);
                    info.gids = entry.gids;
                    info.perUser = entry.perUser;
                    list.add(info);
                }
            }
            break;
            case SysConfigType.TYPE_ASSIGN_PERMISSION: {
                SparseArray<Set<String>> uidAndPermissions = config.getSystemPermissions();
                Set<String> entry;
                for (int i = 0; i < uidAndPermissions.size(); ++i) {
                    int uid = uidAndPermissions.keyAt(i);
                    entry = uidAndPermissions.valueAt(i);
                    SysConfigInfo info = new SysConfigInfo(SysConfigType.TYPE_ASSIGN_PERMISSION, String.valueOf(uid), false);
                    info.permissions = entry.toArray(new String[0]);
                    list.add(info);
                }
            }
            break;
            case SysConfigType.TYPE_SPLIT_PERMISSION: {
                List<SystemConfig.SplitPermissionInfo> permissionInfoList = config.getSplitPermissions();
                for (SystemConfig.SplitPermissionInfo permissionInfo : permissionInfoList) {
                    SysConfigInfo info = new SysConfigInfo(SysConfigType.TYPE_SPLIT_PERMISSION, permissionInfo.getSplitPermission(), false);
                    info.permissions = permissionInfo.getNewPermissions().toArray(new String[0]);
                    info.targetSdk = permissionInfo.getTargetSdk();
                    list.add(info);
                }
            }
            break;
            case SysConfigType.TYPE_LIBRARY: {
                Map<String, SystemConfig.SharedLibraryEntry> sharedLibraries = config.getSharedLibraries();
                SystemConfig.SharedLibraryEntry entry;
                for (String libName : sharedLibraries.keySet()) {
                    entry = sharedLibraries.get(libName);
                    if (entry == null) continue;
                    SysConfigInfo info = new SysConfigInfo(SysConfigType.TYPE_LIBRARY, libName, false);
                    info.filename = entry.filename;
                    info.dependencies = entry.dependencies;
                    list.add(info);
                }
            }
            break;
            case SysConfigType.TYPE_FEATURE: {
                Map<String, FeatureInfo> features = config.getAvailableFeatures();
                FeatureInfo featureInfo;
                for (String feature : features.keySet()) {
                    featureInfo = features.get(feature);
                    if (featureInfo == null) continue;
                    SysConfigInfo info = new SysConfigInfo(SysConfigType.TYPE_FEATURE, feature, false);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        info.version = featureInfo.version;
                    }
                    list.add(info);
                }
            }
            break;
            case SysConfigType.TYPE_UNAVAILABLE_FEATURE:
                for (String feature : config.mUnavailableFeatures) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_UNAVAILABLE_FEATURE, feature, false));
                }
                break;
            case SysConfigType.TYPE_ALLOW_IN_POWER_SAVE_EXCEPT_IDLE:
                for (String packageName : config.getAllowInPowerSaveExceptIdle()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_ALLOW_IN_POWER_SAVE_EXCEPT_IDLE, packageName, true));
                }
                break;
            case SysConfigType.TYPE_ALLOW_IN_POWER_SAVE:
                for (String packageName : config.getAllowInPowerSave()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_ALLOW_IN_POWER_SAVE, packageName, true));
                }
                break;
            case SysConfigType.TYPE_ALLOW_IN_DATA_USAGE_SAVE:
                for (String packageName : config.getAllowInDataUsageSave()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_ALLOW_IN_DATA_USAGE_SAVE, packageName, true));
                }
                break;
            case SysConfigType.TYPE_ALLOW_UNTHROTTLED_LOCATION:
                for (String packageName : config.getAllowUnthrottledLocation()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_ALLOW_UNTHROTTLED_LOCATION, packageName, true));
                }
                break;
            case SysConfigType.TYPE_ALLOW_IGNORE_LOCATION_SETTINGS:
                for (String packageName : config.getAllowIgnoreLocationSettings()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_ALLOW_IGNORE_LOCATION_SETTINGS, packageName, true));
                }
                break;
            case SysConfigType.TYPE_ALLOW_IMPLICIT_BROADCAST: {
                for (String action : config.getAllowImplicitBroadcasts()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_ALLOW_IMPLICIT_BROADCAST, action, false));
                }
            }
            break;
            case SysConfigType.TYPE_APP_LINK:
                for (String packageName : config.getLinkedApps()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_APP_LINK, packageName, true));
                }
                break;
            case SysConfigType.TYPE_SYSTEM_USER_WHITELISTED_APP:
                for (String packageName : config.getSystemUserWhitelistedApps()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_SYSTEM_USER_WHITELISTED_APP, packageName, true));
                }
                break;
            case SysConfigType.TYPE_SYSTEM_USER_BLACKLISTED_APP:
                for (String packageName : config.getSystemUserBlacklistedApps()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_SYSTEM_USER_BLACKLISTED_APP, packageName, true));
                }
                break;
            case SysConfigType.TYPE_DEFAULT_ENABLED_VR_APP: {
                // Get components per package
                ArrayMap<String, Set<String>> packageComponentsMap = new ArrayMap<>();
                Set<String> components;
                for (ComponentName info : config.getDefaultVrComponents()) {
                    components = packageComponentsMap.get(info.getPackageName());
                    if (components == null) {
                        components = new HashSet<>();
                        packageComponentsMap.put(info.getPackageName(), components);
                    }
                    components.add(info.getClassName());
                }
                // Add them to the list
                for (String packageName : packageComponentsMap.keySet()) {
                    components = packageComponentsMap.get(packageName);
                    SysConfigInfo info = new SysConfigInfo(SysConfigType.TYPE_DEFAULT_ENABLED_VR_APP, packageName, true);
                    if (components != null) info.classNames = components.toArray(new String[0]);
                    list.add(info);
                }
            }
            break;
            case SysConfigType.TYPE_COMPONENT_OVERRIDE: {
                Map<String, ArrayMap<String, Boolean>> packageComponentEnabledState = config.mPackageComponentEnabledState;
                ArrayMap<String, Boolean> componentEnableState;
                for (String packageName : packageComponentEnabledState.keySet()) {
                    componentEnableState = packageComponentEnabledState.get(packageName);
                    SysConfigInfo info = new SysConfigInfo(SysConfigType.TYPE_COMPONENT_OVERRIDE, packageName, true);
                    if (componentEnableState != null) {
                        info.classNames = new String[componentEnableState.size()];
                        info.whitelist = new boolean[componentEnableState.size()];
                        for (int i = 0; i < componentEnableState.size(); ++i) {
                            info.classNames[i] = componentEnableState.keyAt(i);
                            info.whitelist[i] = componentEnableState.valueAt(i);
                        }
                    }
                    list.add(info);
                }
            }
            break;
            case SysConfigType.TYPE_BACKUP_TRANSPORT_WHITELISTED_SERVICE: {
                // Get components per package
                ArrayMap<String, Set<String>> packageComponentsMap = new ArrayMap<>();
                Set<String> components;
                for (ComponentName info : config.getBackupTransportWhitelist()) {
                    components = packageComponentsMap.get(info.getPackageName());
                    if (components == null) {
                        components = new HashSet<>();
                        packageComponentsMap.put(info.getPackageName(), components);
                    }
                    components.add(info.getClassName());
                }
                // Add them to the list
                for (String packageName : packageComponentsMap.keySet()) {
                    components = packageComponentsMap.get(packageName);
                    SysConfigInfo info = new SysConfigInfo(SysConfigType.TYPE_BACKUP_TRANSPORT_WHITELISTED_SERVICE, packageName, true);
                    if (components != null) info.classNames = components.toArray(new String[0]);
                    list.add(info);
                }
            }
            break;
            case SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP: {
                ArrayMap<String, List<SystemConfig.CarrierAssociatedAppEntry>> packages = config.getDisabledUntilUsedPreinstalledCarrierAssociatedApps();
                List<SystemConfig.CarrierAssociatedAppEntry> entries;
                SystemConfig.CarrierAssociatedAppEntry entry;
                for (String packageName : packages.keySet()) {
                    entries = packages.get(packageName);
                    SysConfigInfo info = new SysConfigInfo(SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP, packageName, true);
                    if (entries != null) {
                        info.packages = new String[entries.size()];
                        info.targetSdks = new int[entries.size()];
                        for (int i = 0; i < entries.size(); ++i) {
                            entry = entries.get(i);
                            info.packages[i] = entry.packageName;
                            info.targetSdks[i] = entry.addedInSdk;
                        }
                    }
                    list.add(info);
                }
            }
            break;
            case SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_APP:
                for (String packageName : config.getDisabledUntilUsedPreinstalledCarrierApps()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_APP, packageName, true));
                }
                break;
            case SysConfigType.TYPE_PRIVAPP_PERMISSIONS: {
                ArrayMap<String, ArrayMap<String, Boolean>> packagePermissionsMap = new ArrayMap<>();
                convertToMap(packagePermissionsMap, config.mVendorPrivAppPermissions, config.mVendorPrivAppDenyPermissions);
                convertToMap(packagePermissionsMap, config.mProductPrivAppPermissions, config.mProductPrivAppDenyPermissions);
                convertToMap(packagePermissionsMap, config.mSystemExtPrivAppPermissions, config.mSystemExtPrivAppDenyPermissions);
                convertToMap(packagePermissionsMap, config.mPrivAppPermissions, config.mPrivAppDenyPermissions);
                // Add permissions
                ArrayMap<String, Boolean> permissions;
                for (String packageName : packagePermissionsMap.keySet()) {
                    permissions = packagePermissionsMap.get(packageName);
                    SysConfigInfo info = new SysConfigInfo(SysConfigType.TYPE_PRIVAPP_PERMISSIONS, packageName, true);
                    if (permissions != null) {
                        info.permissions = new String[permissions.size()];
                        info.whitelist = new boolean[permissions.size()];
                        for (int i = 0; i < permissions.size(); ++i) {
                            info.permissions[i] = permissions.keyAt(i);
                            info.whitelist[i] = permissions.valueAt(i);
                        }
                    }
                    list.add(info);
                }
            }
            break;
            case SysConfigType.TYPE_OEM_PERMISSIONS: {
                Map<String, ArrayMap<String, Boolean>> packagePermissionsMap = config.mOemPermissions;
                ArrayMap<String, Boolean> permissions;
                for (String packageName : packagePermissionsMap.keySet()) {
                    permissions = packagePermissionsMap.get(packageName);
                    SysConfigInfo info = new SysConfigInfo(SysConfigType.TYPE_OEM_PERMISSIONS, packageName, true);
                    if (permissions != null) {
                        info.permissions = new String[permissions.size()];
                        info.whitelist = new boolean[permissions.size()];
                        for (int i = 0; i < permissions.size(); ++i) {
                            info.permissions[i] = permissions.keyAt(i);
                            info.whitelist[i] = permissions.valueAt(i);
                        }
                    }
                    list.add(info);
                }
            }
            break;
            case SysConfigType.TYPE_HIDDEN_API_WHITELISTED_APP:
                for (String packageName : config.getHiddenApiWhitelistedApps()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_HIDDEN_API_WHITELISTED_APP, packageName, true));
                }
                break;
            case SysConfigType.TYPE_ALLOW_ASSOCIATION: {
                ArrayMap<String, Set<String>> associations = config.getAllowedAssociations();
                for (int i = 0; i < associations.size(); ++i) {
                    SysConfigInfo info = new SysConfigInfo(SysConfigType.TYPE_ALLOW_ASSOCIATION, associations.keyAt(i), true);
                    info.packages = associations.valueAt(i).toArray(new String[0]);
                    list.add(info);
                }
            }
            break;
            case SysConfigType.TYPE_APP_DATA_ISOLATION_WHITELISTED_APP:
                for (String packageName : config.getAppDataIsolationWhitelistedApps()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_APP_DATA_ISOLATION_WHITELISTED_APP, packageName, true));
                }
                break;
            case SysConfigType.TYPE_BUGREPORT_WHITELISTED:
                for (String packageName : config.getBugreportWhitelistedPackages()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_BUGREPORT_WHITELISTED, packageName, true));
                }
                break;
            case SysConfigType.TYPE_INSTALL_IN_USER_TYPE: {
                ArrayMap<String, ArrayMap<String, Boolean>> packageUserTypesMap = new ArrayMap<>();
                convertToMap(packageUserTypesMap, config.mPackageToUserTypeWhitelist, config.mPackageToUserTypeBlacklist);
                ArrayMap<String, Boolean> userTypes;
                for (String packageName : packageUserTypesMap.keySet()) {
                    userTypes = packageUserTypesMap.get(packageName);
                    SysConfigInfo info = new SysConfigInfo(SysConfigType.TYPE_INSTALL_IN_USER_TYPE, packageName, true);
                    if (userTypes != null) {
                        info.userTypes = new String[userTypes.size()];
                        info.whitelist = new boolean[userTypes.size()];
                        for (int i = 0; i < userTypes.size(); ++i) {
                            info.userTypes[i] = userTypes.keyAt(i);
                            info.whitelist[i] = userTypes.valueAt(i);
                        }
                    }
                    list.add(info);
                }
            }
            break;
            case SysConfigType.TYPE_NAMED_ACTOR: {
                ArrayMap<String, ArrayMap<String, String>> actorMap = config.getNamedActors();
                ArrayMap<String, String> actors;
                for (int i = 0; i < actorMap.size(); ++i) {
                    SysConfigInfo info = new SysConfigInfo(SysConfigType.TYPE_NAMED_ACTOR, /* namespace */ actorMap.keyAt(i), false);
                    actors = actorMap.valueAt(i);
                    if (actors != null) {
                        info.actors = new String[actors.size()];
                        info.packages = new String[actors.size()];
                        for (int j = 0; j < actors.size(); ++i) {
                            info.actors[i] = actors.keyAt(i);
                            info.packages[i] = actors.valueAt(i);
                        }
                    }
                    list.add(info);
                }
            }
            break;
            case SysConfigType.TYPE_ROLLBACK_WHITELISTED_APP:
                for (String packageName : config.getRollbackWhitelistedPackages()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_ROLLBACK_WHITELISTED_APP, packageName, true));
                }
                break;
            case SysConfigType.TYPE_WHITELISTED_STAGED_INSTALLER:
                for (String packageName : config.getWhitelistedStagedInstallers()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_WHITELISTED_STAGED_INSTALLER, packageName, true));
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + type);
        }
        return list;
    }

    private static void convertToMap(final ArrayMap<String, ArrayMap<String, Boolean>> packagePermissionsMap,
                                     @NonNull ArrayMap<String, Set<String>> grantMap,
                                     @NonNull ArrayMap<String, Set<String>> denyMap) {
        ArrayMap<String, Boolean> perms;
        String packageName;
        for (int i = 0; i < grantMap.size(); ++i) {
            packageName = grantMap.keyAt(i);
            perms = packagePermissionsMap.get(packageName);
            if (perms == null) {
                perms = new ArrayMap<>();
                packagePermissionsMap.put(packageName, perms);
            }
            for (String permission : grantMap.valueAt(i)) {
                perms.put(permission, true);
            }
        }
        for (int i = 0; i < denyMap.size(); ++i) {
            packageName = denyMap.keyAt(i);
            perms = packagePermissionsMap.get(packageName);
            if (perms == null) {
                perms = new ArrayMap<>();
                packagePermissionsMap.put(packageName, perms);
            }
            for (String permission : denyMap.valueAt(i)) {
                perms.put(permission, false);
            }
        }
    }
}
