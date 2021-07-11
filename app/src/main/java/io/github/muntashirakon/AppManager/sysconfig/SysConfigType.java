// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.StringDef;

@StringDef(value = {
        SysConfigType.TYPE_GROUP,
        SysConfigType.TYPE_PERMISSION,
        SysConfigType.TYPE_ASSIGN_PERMISSION,
        SysConfigType.TYPE_SPLIT_PERMISSION,
        SysConfigType.TYPE_LIBRARY,
        SysConfigType.TYPE_FEATURE,
        SysConfigType.TYPE_UNAVAILABLE_FEATURE,
        SysConfigType.TYPE_ALLOW_IN_POWER_SAVE_EXCEPT_IDLE,
        SysConfigType.TYPE_ALLOW_IN_POWER_SAVE,
        SysConfigType.TYPE_ALLOW_IN_DATA_USAGE_SAVE,
        SysConfigType.TYPE_ALLOW_UNTHROTTLED_LOCATION,
        SysConfigType.TYPE_ALLOW_IGNORE_LOCATION_SETTINGS,
        SysConfigType.TYPE_ALLOW_IMPLICIT_BROADCAST,
        SysConfigType.TYPE_APP_LINK,
        SysConfigType.TYPE_SYSTEM_USER_WHITELISTED_APP,
        SysConfigType.TYPE_SYSTEM_USER_BLACKLISTED_APP,
        SysConfigType.TYPE_DEFAULT_ENABLED_VR_APP,
        SysConfigType.TYPE_COMPONENT_OVERRIDE,
        SysConfigType.TYPE_BACKUP_TRANSPORT_WHITELISTED_SERVICE,
        SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP,
        SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_APP,
        SysConfigType.TYPE_PRIVAPP_PERMISSIONS,
        SysConfigType.TYPE_OEM_PERMISSIONS,
        SysConfigType.TYPE_HIDDEN_API_WHITELISTED_APP,
        SysConfigType.TYPE_ALLOW_ASSOCIATION,
        SysConfigType.TYPE_APP_DATA_ISOLATION_WHITELISTED_APP,
        SysConfigType.TYPE_BUGREPORT_WHITELISTED,
        SysConfigType.TYPE_INSTALL_IN_USER_TYPE,
        SysConfigType.TYPE_NAMED_ACTOR,
        SysConfigType.TYPE_ROLLBACK_WHITELISTED_APP,
        SysConfigType.TYPE_WHITELISTED_STAGED_INSTALLER,
})
@Retention(RetentionPolicy.SOURCE)
@interface SysConfigType {
    String TYPE_GROUP = "group";
    String TYPE_PERMISSION = "permission";
    String TYPE_ASSIGN_PERMISSION = "assign-permission";
    String TYPE_SPLIT_PERMISSION = "split-permission";
    String TYPE_LIBRARY = "library";
    String TYPE_FEATURE = "feature";  // available-feature
    String TYPE_UNAVAILABLE_FEATURE = "unavailable-feature";
    String TYPE_ALLOW_IN_POWER_SAVE_EXCEPT_IDLE = "allow-in-power-save-except-idle";
    String TYPE_ALLOW_IN_POWER_SAVE = "allow-in-power-save";
    String TYPE_ALLOW_IN_DATA_USAGE_SAVE = "allow-in-data-usage-save";
    String TYPE_ALLOW_UNTHROTTLED_LOCATION = "allow-unthrottled-location";
    String TYPE_ALLOW_IGNORE_LOCATION_SETTINGS = "allow-ignore-location-settings";
    String TYPE_ALLOW_IMPLICIT_BROADCAST = "allow-implicit-broadcast";
    String TYPE_APP_LINK = "app-link";
    String TYPE_SYSTEM_USER_WHITELISTED_APP = "system-user-whitelisted-app";
    String TYPE_SYSTEM_USER_BLACKLISTED_APP = "system-user-blacklisted-app";
    String TYPE_DEFAULT_ENABLED_VR_APP = "default-enabled-vr-app";
    String TYPE_COMPONENT_OVERRIDE = "component-override";
    String TYPE_BACKUP_TRANSPORT_WHITELISTED_SERVICE = "backup-transport-whitelisted-service";
    String TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP = "disabled-until-used-preinstalled-carrier-associated-app";
    String TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_APP = "disabled-until-used-preinstalled-carrier-app";
    String TYPE_PRIVAPP_PERMISSIONS = "privapp-permissions";
    String TYPE_OEM_PERMISSIONS = "oem-permissions";
    String TYPE_HIDDEN_API_WHITELISTED_APP = "hidden-api-whitelisted-app";
    String TYPE_ALLOW_ASSOCIATION = "allow-association";
    String TYPE_APP_DATA_ISOLATION_WHITELISTED_APP = "app-data-isolation-whitelisted-app";
    String TYPE_BUGREPORT_WHITELISTED = "bugreport-whitelisted";
    String TYPE_INSTALL_IN_USER_TYPE = "install-in-user-type";
    String TYPE_NAMED_ACTOR = "named-actor";
    String TYPE_ROLLBACK_WHITELISTED_APP = "rollback-whitelisted-app";
    String TYPE_WHITELISTED_STAGED_INSTALLER = "whitelisted-staged-installer";
}
