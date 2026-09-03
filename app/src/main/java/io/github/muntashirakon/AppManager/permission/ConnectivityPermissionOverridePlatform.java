// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.compat.ConnectivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.PermissionOverride;

/**
 * ConnectivityManager's firewall-chain on Android 13+.
 */
public final class ConnectivityPermissionOverridePlatform implements PermissionOverrideReconciler.Platform {
    @Override
    public int resolveUid(@NonNull String packageName, int userId) throws Exception {
        ApplicationInfo info = PackageManagerCompat.getApplicationInfo(packageName, 0, userId);
        return info.uid;
    }

    @Override
    public boolean isEnforced(int uid, @NonNull PermissionOverride override) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false;
        }
        int firewallChain = chain(override);
        if (!ConnectivityManagerCompat.getFirewallChainEnabled(firewallChain)) {
            return false;
        }
        int rule = ConnectivityManagerCompat.getUidFirewallRule(firewallChain, uid);
        int expected = override.desiredGranted
                ? android.net.ConnectivityManagerHidden.FIREWALL_RULE_DEFAULT
                : android.net.ConnectivityManagerHidden.FIREWALL_RULE_DENY;
        return rule == expected;
    }

    @Override
    public void apply(int uid, @NonNull PermissionOverride override) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            throw new RemoteException("Firewall overlays require Android 13+");
        }
        int firewallChain = chain(override);
        // Per-UID rules only take effect while their chain is enabled.
        ConnectivityManagerCompat.setFirewallChainEnabled(firewallChain, true);
        int rule = override.desiredGranted
                ? android.net.ConnectivityManagerHidden.FIREWALL_RULE_DEFAULT
                : android.net.ConnectivityManagerHidden.FIREWALL_RULE_DENY;
        ConnectivityManagerCompat.setUidFirewallRule(firewallChain, uid, rule);
    }

    private static int chain(@NonNull PermissionOverride override) {
        return Integer.parseInt(override.controller);
    }
}
