/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.sysconfig;

class SysConfigInfo {
    @SysConfigType
    final String type;
    final String name;  // permission, split-permission, library, feature, unavailable-feature,
    // allow-implicit-broadcast, default-enabled-vr-app, component-override,
    // backup-transport-whitelisted-service, disabled-until-used-preinstalled-carrier-associate-app,
    // privapp-permissions, oem-permissions, allow-association,
    // install-in-user-type, named-actor, whitelisted-staged-installer
    final boolean isPackage;
    String[] associations;  // allow-association
    String actor;  // named-actor
    String namespace;  // named-actor
    String className;  // default-enabled-vr-app, component-override, backup-transport-whitelisted-service
    boolean enabled;  // component-override
    String pkgEntry;  // disabled-until-used-preinstalled-carrier-associated-app
    String fileName;  // library
    int[] gids;  // permission
    String[] permissions;  // assign-permission, split-permission, privapp-permissions, oem-permissions
    String[] deniedPerms;  // privapp-permissions, oem-permissions
    String[] dependencies;  // library
    String[] blacklist;  // install-in-user-type
    String[] whitelist;  // install-in-user-type
    boolean perUser;  // permission
    int targetSdk;  // split-permission, disabled-until-used-preinstalled-carrier-associated-app
    int uid;  // assign-permission
    int version;  // feature

    SysConfigInfo(@SysConfigType String type, String name, boolean isPackage) {
        this.type = type;
        this.name = name;
        this.isPackage = isPackage;
    }
}
