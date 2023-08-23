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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
     * Reject network usage on wifi network. {@code POLICY_REJECT_ON_WLAN} up to Lineage 17.1 (Android 10)
     */
    public static final int POLICY_REJECT_WIFI = 1 << 15;
    /**
     * Reject network usage on cellular network. {@code POLICY_REJECT_ON_DATA} up to Lineage 17.1 (Android 10)
     */
    public static final int POLICY_REJECT_CELLULAR = 1 << 16;
    /**
     * Reject network usage on virtual private network. {@code POLICY_REJECT_ON_VPN} up to Lineage 17.1 (Android 10)
     */
    public static final int POLICY_REJECT_VPN = 1 << 17;
    /**
     * Reject network usage on all networks. {@code POLICY_NETWORK_ISOLATED} up to Lineage 17.1 (Android 10)
     */
    public static final int POLICY_REJECT_ALL = 1 << 18;
    // The following are taken from Samsung device (Android 10)
    public static final int POLICY_ALLOW_METERED_IN_ROAMING = 1001;
    public static final int POLICY_ALLOW_WHITELIST_IN_ROAMING = 1002;

    @IntDef(flag = true, value = {
            NetworkPolicyManager.POLICY_NONE,
            NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND,
            NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND,
            // Lineage OS
            POLICY_REJECT_CELLULAR,
            POLICY_REJECT_VPN,
            POLICY_REJECT_WIFI,
            POLICY_REJECT_ALL,
            // Samsung
            POLICY_ALLOW_METERED_IN_ROAMING,
            POLICY_ALLOW_WHITELIST_IN_ROAMING,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface NetPolicy {}

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
        if (policies > 0) {
            for (int policy : sNetworkPolicies.keySet()) {
                if ((policies & policy) != 0) {
                    readablePolicies.put(policy, context.getString(R.string.unknown_net_policy,
                            sNetworkPolicies.get(policy), policy));
                }
            }
            // Put known policies
            if ((policies & NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND) != 0) {
                readablePolicies.put(NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND,
                        context.getString(R.string.netpolicy_reject_background_data));
            }
            if ((policies & NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND) != 0) {
                readablePolicies.put(NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND,
                        context.getString(R.string.netpolicy_allow_background_data));
            }
            if ((policies & POLICY_REJECT_CELLULAR) != 0) {
                readablePolicies.put(POLICY_REJECT_CELLULAR, context.getString(R.string.netpolicy_reject_cellular_data));
            }
            if ((policies & POLICY_REJECT_VPN) != 0) {
                readablePolicies.put(POLICY_REJECT_VPN, context.getString(R.string.netpolicy_reject_vpn_data));
            }
            if ((policies & POLICY_REJECT_WIFI) != 0) {
                readablePolicies.put(POLICY_REJECT_WIFI, context.getString(R.string.netpolicy_reject_wifi_data));
            }
            if ((policies & POLICY_REJECT_ALL) != 0) {
                readablePolicies.put(POLICY_REJECT_ALL, context.getString(R.string.netpolicy_disable_network_access));
            }
        } else {
            readablePolicies.put(NetworkPolicyManager.POLICY_NONE, context.getString(R.string.none));
        }
        return readablePolicies;
    }

    @NonNull
    public static ArrayMap<Integer, String> getAllReadablePolicies(@NonNull Context context) {
        ArrayMap<Integer, String> readablePolicies = new ArrayMap<>();
        List<Integer> visitedPolicies = new ArrayList<>();
        if (sNetworkPolicies.containsKey(NetworkPolicyManager.POLICY_NONE)) {
            readablePolicies.put(NetworkPolicyManager.POLICY_NONE, context.getString(R.string.none));
            visitedPolicies.add(NetworkPolicyManager.POLICY_NONE);
        }
        if (sNetworkPolicies.containsKey(NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND)) {
            readablePolicies.put(NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND,
                    context.getString(R.string.netpolicy_reject_background_data));
            visitedPolicies.add(NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND);
        }
        if (sNetworkPolicies.containsKey(NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND)) {
            readablePolicies.put(NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND,
                    context.getString(R.string.netpolicy_allow_background_data));
            visitedPolicies.add(NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND);
        }
        if (sNetworkPolicies.containsValue("POLICY_REJECT_ON_DATA")
                || sNetworkPolicies.containsValue("POLICY_REJECT_CELLULAR")) {
            readablePolicies.put(POLICY_REJECT_CELLULAR, context.getString(R.string.netpolicy_reject_cellular_data));
            visitedPolicies.add(POLICY_REJECT_CELLULAR);
        }
        if (sNetworkPolicies.containsValue("POLICY_REJECT_ON_VPN")
                || sNetworkPolicies.containsValue("POLICY_REJECT_VPN")) {
            readablePolicies.put(POLICY_REJECT_VPN, context.getString(R.string.netpolicy_reject_vpn_data));
            visitedPolicies.add(POLICY_REJECT_VPN);
        }
        if (sNetworkPolicies.containsValue("POLICY_REJECT_ON_WLAN")
                || sNetworkPolicies.containsValue("POLICY_REJECT_WIFI")) {
            readablePolicies.put(POLICY_REJECT_WIFI, context.getString(R.string.netpolicy_reject_wifi_data));
            visitedPolicies.add(POLICY_REJECT_WIFI);
        }
        if (sNetworkPolicies.containsValue("POLICY_NETWORK_ISOLATED") || sNetworkPolicies.containsValue("POLICY_REJECT_ALL")) {
            readablePolicies.put(POLICY_REJECT_ALL, context.getString(R.string.netpolicy_disable_network_access));
            visitedPolicies.add(POLICY_REJECT_ALL);
        }
        for (int i = 0; i < sNetworkPolicies.size(); ++i) {
            if (!visitedPolicies.contains(sNetworkPolicies.keyAt(i))) {
                readablePolicies.put(sNetworkPolicies.keyAt(i), context.getString(R.string.unknown_net_policy,
                        sNetworkPolicies.valueAt(i), sNetworkPolicies.keyAt(i)));
            }
        }
        return readablePolicies;
    }

    private static INetworkPolicyManager getNetPolicyManager() {
        return INetworkPolicyManager.Stub.asInterface(ProxyBinder.getService("netpolicy"));
    }
}
