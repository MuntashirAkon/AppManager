// SPDX-License-Identifier: Apache-2.0

package android.net;

/**
 * Manager for creating and modifying network policy rules.
 */
public class NetworkPolicyManager {
    /* POLICY_* are masks and can be ORed, although currently they are not. */
    /**
     * No specific network policy, use system default.
     */
    public static final int POLICY_NONE = 0;
    /**
     * Reject network usage on metered networks when application in background.
     */
    public static final int POLICY_REJECT_METERED_BACKGROUND = 1;
    /**
     * Allow metered network use in the background even when in data usage save mode.
     */
    public static final int POLICY_ALLOW_METERED_BACKGROUND = 1 << 2;
}