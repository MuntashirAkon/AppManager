// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.content.Context;
import android.net.INetworkPolicyManager;
import android.net.NetworkPolicyManager;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.collection.ArrayMap;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;

public final class NetworkPolicyManagerCompat {
    public static final String TAG = NetworkPolicyManagerCompat.class.getSimpleName();

    /*
     * The policies below are taken from LineageOS
     * Source: https://github.com/LineageOS/android_frameworks_base/blob/lineage-18.1/core/java/android/net/NetworkPolicyManager.java
     */
    /**
     * Reject network usage on Wi-Fi network. {@code POLICY_REJECT_ON_WLAN} up to Lineage 17.1 (Android 10)
     */
    public static final int POLICY_LOS_REJECT_WIFI = 1 << 15;
    /**
     * Reject network usage on cellular network. {@code POLICY_REJECT_ON_DATA} up to Lineage 17.1 (Android 10)
     */
    public static final int POLICY_LOS_REJECT_CELLULAR = 1 << 16;
    /**
     * Reject network usage on virtual private network. {@code POLICY_REJECT_ON_VPN} up to Lineage 17.1 (Android 10)
     */
    public static final int POLICY_LOS_REJECT_VPN = 1 << 17;
    /**
     * Reject network usage on all networks. {@code POLICY_NETWORK_ISOLATED} up to Lineage 17.1 (Android 10)
     */
    public static final int POLICY_LOS_REJECT_ALL = 1 << 18;
    // The following are taken from Motorola device (Android 12)
    public static final int POLICY_MOTO_REJECT_METERED = 1 << 1;
    public static final int POLICY_MOTO_REJECT_BACKGROUND = 1 << 5;
    public static final int POLICY_MOTO_REJECT_ALL = 1 << 6;
    // The following are taken from Samsung device (Android 10)
    public static final int POLICY_ONE_UI_ALLOW_METERED_IN_ROAMING = 1001;
    public static final int POLICY_ONE_UI_ALLOW_WHITELIST_IN_ROAMING = 1002;

    @IntDef(flag = true, value = {
            NetworkPolicyManager.POLICY_NONE,
            NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND,
            NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND,
            // Lineage OS
            POLICY_LOS_REJECT_WIFI,
            POLICY_LOS_REJECT_CELLULAR,
            POLICY_LOS_REJECT_VPN,
            POLICY_LOS_REJECT_ALL,
            // Motorola
            POLICY_MOTO_REJECT_METERED,
            POLICY_MOTO_REJECT_BACKGROUND,
            POLICY_MOTO_REJECT_ALL,
            // Samsung
            POLICY_ONE_UI_ALLOW_METERED_IN_ROAMING,
            POLICY_ONE_UI_ALLOW_WHITELIST_IN_ROAMING,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface NetPolicy {
    }

    private static final ArrayMap<Integer, String> sNetworkPolicies = new ArrayMap<Integer, String>() {
        {
            for (Field field : NetworkPolicyManager.class.getFields()) {
                if (field.getName().startsWith("POLICY_")) {
                    try {
                        put(field.getInt(null), field.getName());
                    } catch (IllegalAccessException ignore) {
                    }
                }
            }
        }
    };

    @NetPolicy
    @RequiresPermission(ManifestCompat.permission.MANAGE_NETWORK_POLICY)
    public static int getUidPolicy(int uid) throws RemoteException {
        return getNetPolicyManager().getUidPolicy(uid);
    }

    @RequiresPermission(ManifestCompat.permission.MANAGE_NETWORK_POLICY)
    public static void setUidPolicy(int uid, int policies) throws RemoteException {
        if (UserHandleHidden.isApp(uid)) {
            getNetPolicyManager().setUidPolicy(uid, policies);
        } else {
            Log.w(TAG, "Cannot set policy %d to uid %d", policies, uid);
        }
    }

    @NonNull
    public static ArrayMap<Integer, String> getReadablePolicies(@NonNull Context context, int policies) {
        ArrayMap<Integer, String> readablePolicies = new ArrayMap<>();
        if (policies == 0) {
            readablePolicies.put(NetworkPolicyManager.POLICY_NONE, context.getString(R.string.none));
            return readablePolicies;
        }
        for (int i = 0; i < sNetworkPolicies.size(); ++i) {
            int policy = sNetworkPolicies.keyAt(i);
            if (!hasPolicy(policies, policy)) {
                continue;
            }
            String policyName = sNetworkPolicies.valueAt(i);
            String readablePolicyName = getReadablePolicyName(context, policy, policyName);
            readablePolicies.put(policy, readablePolicyName);
        }
        return readablePolicies;
    }

    @NonNull
    public static ArrayMap<Integer, String> getAllReadablePolicies(@NonNull Context context) {
        ArrayMap<Integer, String> readablePolicies = new ArrayMap<>();
        for (int i = 0; i < sNetworkPolicies.size(); ++i) {
            int policy = sNetworkPolicies.keyAt(i);
            String policyName = sNetworkPolicies.valueAt(i);
            String readablePolicyName = getReadablePolicyName(context, policy, policyName);
            readablePolicies.put(policy, readablePolicyName);
        }
        return readablePolicies;
    }

    private static INetworkPolicyManager getNetPolicyManager() {
        return INetworkPolicyManager.Stub.asInterface(ProxyBinder.getService("netpolicy"));
    }

    private static boolean hasPolicy(int policies, int policy) {
        return (policies & policy) != 0;
    }

    private static String getReadablePolicyName(@NonNull Context context, int policy, @NonNull String policyName) {
        switch (policy) {
            case NetworkPolicyManager.POLICY_NONE:
                return context.getString(R.string.none);
            case NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND:
                return context.getString(R.string.netpolicy_reject_metered_background_data);
            case NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND:
                return context.getString(R.string.netpolicy_allow_metered_background_data);
            case POLICY_LOS_REJECT_WIFI:
                if (policyName.equals("POLICY_REJECT_ON_WLAN") || policyName.equals("POLICY_REJECT_WIFI")) {
                    return context.getString(R.string.netpolicy_reject_wifi_data);
                }
                break;
            case POLICY_LOS_REJECT_CELLULAR:
                if (policyName.equals("POLICY_REJECT_ON_DATA") || policyName.equals("POLICY_REJECT_CELLULAR")) {
                    return context.getString(R.string.netpolicy_reject_cellular_data);
                }
                break;
            case POLICY_LOS_REJECT_VPN:
                if (policyName.equals("POLICY_REJECT_ON_VPN") || policyName.equals("POLICY_REJECT_VPN")) {
                    return context.getString(R.string.netpolicy_reject_vpn_data);
                }
                break;
            case POLICY_LOS_REJECT_ALL:
                if (policyName.equals("POLICY_NETWORK_ISOLATED") || policyName.equals("POLICY_REJECT_ALL")) {
                    return context.getString(R.string.netpolicy_disable_network_access);
                }
                break;
            case POLICY_MOTO_REJECT_METERED:
                if (policyName.equals("POLICY_REJECT_METERED")) {
                    return context.getString(R.string.netpolicy_reject_metered_data);
                }
                break;
            case POLICY_MOTO_REJECT_BACKGROUND:
                if (policyName.equals("POLICY_REJECT_BACKGROUND")) {
                    return context.getString(R.string.netpolicy_reject_background_data);
                }
                break;
            case POLICY_MOTO_REJECT_ALL:
                if (policyName.equals("POLICY_REJECT_ALL")) {
                    return context.getString(R.string.netpolicy_disable_network_access);
                }
                break;
        }
        return context.getString(R.string.unknown_net_policy, policyName, policy);
    }
}
