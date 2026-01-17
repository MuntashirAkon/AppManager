// SPDX-License-Identifier: Apache-2.0

package android.net;

import android.os.Build;

import androidx.annotation.RequiresApi;

public class ConnectivityManagerHidden {
    /**
     * Firewall chain for device idle (doze mode).
     * Allowlist of apps that have network access in device idle.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static final int FIREWALL_CHAIN_DOZABLE = 1;

    /**
     * Firewall chain used for app standby.
     * Denylist of apps that do not have network access.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static final int FIREWALL_CHAIN_STANDBY = 2;

    /**
     * Firewall chain used for battery saver.
     * Allowlist of apps that have network access when battery saver is on.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static final int FIREWALL_CHAIN_POWERSAVE = 3;

    /**
     * Firewall chain used for restricted networking mode.
     * Allowlist of apps that have access in restricted networking mode.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static final int FIREWALL_CHAIN_RESTRICTED = 4;

    /**
     * Firewall chain used for low power standby.
     * Allowlist of apps that have access in low power standby.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static final int FIREWALL_CHAIN_LOW_POWER_STANDBY = 5;

    /**
     * Firewall chain used for lockdown VPN.
     * Denylist of apps that cannot receive incoming packets except on loopback because they are
     * subject to an always-on VPN which is not currently connected.
     *
     * @deprecated Removed in Android 14 (Upside Down Cake)
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static final int FIREWALL_CHAIN_LOCKDOWN_VPN = 6;

    /**
     * Firewall chain used for always-on default background restrictions.
     * Allowlist of apps that have access because either they are in the foreground or they are
     * exempted for specific situations while in the background.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public static final int FIREWALL_CHAIN_BACKGROUND = 6;

    /**
     * Firewall chain used for OEM-specific application restrictions.
     * <p>
     * Denylist of apps that will not have network access due to OEM-specific restrictions. If an
     * app UID is placed on this chain, and the chain is enabled, the app's packets will be dropped.
     * <p>
     * All the {@code FIREWALL_CHAIN_OEM_DENY_x} chains are equivalent, and each one is
     * independent of the others. The chains can be enabled and disabled independently, and apps can
     * be added and removed from each chain independently.
     *
     * @see #FIREWALL_CHAIN_OEM_DENY_2
     * @see #FIREWALL_CHAIN_OEM_DENY_3
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static final int FIREWALL_CHAIN_OEM_DENY_1 = 7;

    /**
     * Firewall chain used for OEM-specific application restrictions.
     * <p>
     * Denylist of apps that will not have network access due to OEM-specific restrictions. If an
     * app UID is placed on this chain, and the chain is enabled, the app's packets will be dropped.
     * <p>
     * All the {@code FIREWALL_CHAIN_OEM_DENY_x} chains are equivalent, and each one is
     * independent of the others. The chains can be enabled and disabled independently, and apps can
     * be added and removed from each chain independently.
     *
     * @see #FIREWALL_CHAIN_OEM_DENY_1
     * @see #FIREWALL_CHAIN_OEM_DENY_3
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static final int FIREWALL_CHAIN_OEM_DENY_2 = 8;

    /**
     * Firewall chain used for OEM-specific application restrictions.
     * <p>
     * Denylist of apps that will not have network access due to OEM-specific restrictions. If an
     * app UID is placed on this chain, and the chain is enabled, the app's packets will be dropped.
     * <p>
     * All the {@code FIREWALL_CHAIN_OEM_DENY_x} chains are equivalent, and each one is
     * independent of the others. The chains can be enabled and disabled independently, and apps can
     * be added and removed from each chain independently.
     *
     * @see #FIREWALL_CHAIN_OEM_DENY_1
     * @see #FIREWALL_CHAIN_OEM_DENY_2
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static final int FIREWALL_CHAIN_OEM_DENY_3 = 9;

    /**
     * Firewall chain for allow list on metered networks
     * <p>
     * UIDs added to this chain have access to metered networks, unless they're also in one of the
     * denylist, {@link #FIREWALL_CHAIN_METERED_DENY_USER},
     * {@link #FIREWALL_CHAIN_METERED_DENY_ADMIN}
     * <p>
     * Note that this chain is used from a separate bpf program that is triggered by iptables and
     * can not be controlled by {@link IConnectivityManager#setFirewallChainEnabled}.
     */
    // TODO: Merge this chain with data saver and support setFirewallChainEnabled
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public static final int FIREWALL_CHAIN_METERED_ALLOW = 10;

    /**
     * Firewall chain for user-set restrictions on metered networks
     * <p>
     * UIDs added to this chain do not have access to metered networks.
     * UIDs should be added to this chain based on user settings.
     * To restrict metered network based on admin configuration (e.g. enterprise policies),
     * {@link #FIREWALL_CHAIN_METERED_DENY_ADMIN} should be used.
     * This chain corresponds to {@code #BLOCKED_METERED_REASON_USER_RESTRICTED}
     * <p>
     * Note that this chain is used from a separate bpf program that is triggered by iptables and
     * can not be controlled by {@link IConnectivityManager#setFirewallChainEnabled}.
     */
    // TODO: Support setFirewallChainEnabled to control this chain
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public static final int FIREWALL_CHAIN_METERED_DENY_USER = 11;

    /**
     * Firewall chain for admin-set restrictions on metered networks
     * <p>
     * UIDs added to this chain do not have access to metered networks.
     * UIDs should be added to this chain based on admin configuration (e.g. enterprise policies).
     * To restrict metered network based on user settings, {@link #FIREWALL_CHAIN_METERED_DENY_USER}
     * should be used.
     * This chain corresponds to {@code #BLOCKED_METERED_REASON_ADMIN_DISABLED}
     * <p>
     * Note that this chain is used from a separate bpf program that is triggered by iptables and
     * can not be controlled by {@link IConnectivityManager#setFirewallChainEnabled}.
     */
    // TODO: Support setFirewallChainEnabled to control this chain
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public static final int FIREWALL_CHAIN_METERED_DENY_ADMIN = 12;

    /**
     * A firewall rule which allows or drops packets depending on existing policy.
     * Used by {@link IConnectivityManager#setUidFirewallRule(int, int, int)} to follow existing policy to handle
     * specific uid's packets in specific firewall chain.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static final int FIREWALL_RULE_DEFAULT = 0;

    /**
     * A firewall rule which allows packets. Used by {@link IConnectivityManager#setUidFirewallRule(int, int, int)} to
     * allow specific uid's packets in specific firewall chain.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static final int FIREWALL_RULE_ALLOW = 1;

    /**
     * A firewall rule which drops packets. Used by {@link IConnectivityManager#setUidFirewallRule(int, int, int)} to
     * drop specific uid's packets in specific firewall chain.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static final int FIREWALL_RULE_DENY = 2;
}
