// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig;

import androidx.annotation.NonNull;

class SysConfigInfo {
    @SysConfigType
    final String type;
    /**
     * Name of the config. The value is usually a package name but could be other name as well, such as
     * <ul>
     * <li> gid for {@link SysConfigType#TYPE_GROUP}
     * <li> permission name for {@link SysConfigType#TYPE_PERMISSION} and {@link SysConfigType#TYPE_SPLIT_PERMISSION}
     * <li> uid for {@link SysConfigType#TYPE_ASSIGN_PERMISSION}
     * <li> library name for {@link SysConfigType#TYPE_LIBRARY}
     * <li> feature name for {@link SysConfigType#TYPE_FEATURE} and {@link SysConfigType#TYPE_UNAVAILABLE_FEATURE}
     * <li> action name for {@link SysConfigType#TYPE_ALLOW_IMPLICIT_BROADCAST}
     * <li> namespace for {@link SysConfigType#TYPE_NAMED_ACTOR}
     *
     * @see #isPackage
     */
    @NonNull
    final String name;
    final boolean isPackage;

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
     * Packages associated with a carrier package (for {@link SysConfigType#TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP}),
     * packages associate with certain actors under a namespace (for {@link SysConfigType#TYPE_NAMED_ACTOR})
     * or packages associated with another package (for {@link SysConfigType#TYPE_ALLOW_ASSOCIATION}).
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
     * FIXME(20/10/20): Find documentation regarding perUser
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
