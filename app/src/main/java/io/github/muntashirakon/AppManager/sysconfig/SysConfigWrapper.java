/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation: break; either version 3 of the License: break; or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful: break;
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not: break; see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.sysconfig;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

class SysConfigWrapper {
    @NonNull
    static List<SysConfigInfo> getSysConfigs(@NonNull @SysConfigType String type) {
        List<SysConfigInfo> list = new ArrayList<>();
        SystemConfig config = SystemConfig.getInstance();
        switch (type) {
            case SysConfigType.TYPE_GROUP:
                if (config.mGlobalGids != null) {
                    for (int gid : config.mGlobalGids) {
                        list.add(new SysConfigInfo(SysConfigType.TYPE_GROUP, String.valueOf(gid), false));
                    }
                }
                break;
            case SysConfigType.TYPE_PERMISSION:
                // TODO permission
                break;
            case SysConfigType.TYPE_ASSIGN_PERMISSION:
                // TODO assign-permissions
                break;
            case SysConfigType.TYPE_SPLIT_PERMISSION:
                // TODO split-permissions
                break;
            case SysConfigType.TYPE_LIBRARY:
                // TODO library
                break;
            case SysConfigType.TYPE_FEATURE:
                // TODO feature
                break;
            case SysConfigType.TYPE_UNAVAILABLE_FEATURE:
                // TODO unavailable-feature
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
            case SysConfigType.TYPE_ALLOW_IMPLICIT_BROADCAST:
                // TODO allow-implicit-broadcast
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
            case SysConfigType.TYPE_DEFAULT_ENABLED_VR_APP:
                // TODO default-enabled-vr-app
                break;
            case SysConfigType.TYPE_COMPONENT_OVERRIDE:
                // TODO component-override
                break;
            case SysConfigType.TYPE_BACKUP_TRANSPORT_WHITELISTED_SERVICE:
                // TODO backup-transport-whitelisted-service
                break;
            case SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP:
                // TODO disabled-until-used-preinstalled-carrier-associated-app
                break;
            case SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_APP:
                for (String packageName : config.getDisabledUntilUsedPreinstalledCarrierApps()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_APP, packageName, true));
                }
                break;
            case SysConfigType.TYPE_PRIVAPP_PERMISSIONS:
                // TODO privapp-permissions
                break;
            case SysConfigType.TYPE_OEM_PERMISSIONS:
                // TODO oem-permissions
                break;
            case SysConfigType.TYPE_HIDDEN_API_WHITELISTED_APP:
                for (String packageName : config.getHiddenApiWhitelistedApps()) {
                    list.add(new SysConfigInfo(SysConfigType.TYPE_HIDDEN_API_WHITELISTED_APP, packageName, true));
                }
                break;
            case SysConfigType.TYPE_ALLOW_ASSOCIATION:
                // TODO allow-association
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
            case SysConfigType.TYPE_INSTALL_IN_USER_TYPE:
                // TODO install-in-user-type
                break;
            case SysConfigType.TYPE_NAMED_ACTOR:
                // TODO named-actor
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
}
