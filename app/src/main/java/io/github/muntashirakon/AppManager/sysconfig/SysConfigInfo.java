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

import androidx.annotation.NonNull;

class SysConfigInfo {
    @SysConfigType
    final String type;
    @NonNull
    final String name;
    final boolean isPackage;

    /**
     * Associated packages, applicable for {@link SysConfigType#TYPE_ALLOW_ASSOCIATION}.
     */
    String[] associations;
    /**
     * Actors for a certain namespace, applicable for {@link SysConfigType#TYPE_NAMED_ACTOR}.
     *
     * @see #packages
     */
    String[] actors;
    /**
     * Component names of a package, applicable for {@link SysConfigType#TYPE_COMPONENT_OVERRIDE},
     * {@link SysConfigType#TYPE_DEFAULT_ENABLED_VR_APP} and
     * {@link SysConfigType#TYPE_BACKUP_TRANSPORT_WHITELISTED_SERVICE}.
     *
     * @see #whitelist
     */
    String[] classNames;
    /**
     * Denotes enable/disable state for {@link SysConfigType#TYPE_COMPONENT_OVERRIDE}, grant/revoke
     * for {@link SysConfigType#TYPE_PRIVAPP_PERMISSIONS} and {@link SysConfigType#TYPE_OEM_PERMISSIONS},
     * and whitelist/blacklist {@link SysConfigType#TYPE_INSTALL_IN_USER_TYPE}.
     *
     * @see #classNames
     * @see #permissions
     * @see #userTypes
     */
    boolean[] whitelist;
    /**
     * Packages associated with a carrier package (for {@link SysConfigType#TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP})
     * or packages associate with certain actors under a namespace (for {@link SysConfigType#TYPE_NAMED_ACTOR}).
     *
     * @see #actors
     * @see #targetSdks
     */
    String[] packages;
    /**
     * Library filename, applicable for {@link SysConfigType#TYPE_LIBRARY}.
     *
     * @see #dependencies
     */
    String filename;
    /**
     * FIXME(20/10/20): Find documentation regarding GIDs
     * Applicable for {@link SysConfigType#TYPE_PERMISSION}.
     *
     * @see #perUser
     */
    int[] gids;
    /**
     * Permissions belonging certain uid for {@link SysConfigType#TYPE_ASSIGN_PERMISSION}, split
     * permissions for {@link SysConfigType#TYPE_SPLIT_PERMISSION}, and permissions for
     * {@link SysConfigType#TYPE_PRIVAPP_PERMISSIONS} and {@link SysConfigType#TYPE_OEM_PERMISSIONS}.
     *
     * @see #whitelist
     * @see #targetSdk
     */
    String[] permissions;
    /**
     * Library dependencies, applicable for {@link SysConfigType#TYPE_LIBRARY}.
     *
     * @see #filename
     */
    String[] dependencies;
    /**
     * User types for certain package, applicable for {@link SysConfigType#TYPE_INSTALL_IN_USER_TYPE}
     *
     * @see #whitelist
     */
    String[] userTypes;
    /**
     * FIXME(20/10/20): Find documentation regarding GIDs
     * Applicable for {@link SysConfigType#TYPE_PERMISSION}.
     *
     * @see #gids
     */
    boolean perUser;
    /**
     * Target SDK for {@link SysConfigType#TYPE_SPLIT_PERMISSION}.
     *
     * @see #permissions
     */
    int targetSdk;
    /**
     * Target SDKs for {@link #permissions}. Applicable for
     * {@link SysConfigType#TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP}.
     *
     * @see #permissions
     */
    int[] targetSdks;
    /**
     * Feature version (Android O or later), applicable for {@link SysConfigType#TYPE_FEATURE}.
     */
    int version;

    SysConfigInfo(@SysConfigType String type, @NonNull String name, boolean isPackage) {
        this.type = type;
        this.name = name;
        this.isPackage = isPackage;
    }
}
