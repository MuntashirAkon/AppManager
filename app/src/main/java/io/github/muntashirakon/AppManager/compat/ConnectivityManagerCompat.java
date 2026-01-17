// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManagerHidden;
import android.net.IConnectivityManager;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.IntDef;
import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;

public class ConnectivityManagerCompat {
    @SuppressLint("UniqueConstants")
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            ConnectivityManagerHidden.FIREWALL_CHAIN_DOZABLE,
            ConnectivityManagerHidden.FIREWALL_CHAIN_STANDBY,
            ConnectivityManagerHidden.FIREWALL_CHAIN_POWERSAVE,
            ConnectivityManagerHidden.FIREWALL_CHAIN_RESTRICTED,
            ConnectivityManagerHidden.FIREWALL_CHAIN_LOW_POWER_STANDBY,
            ConnectivityManagerHidden.FIREWALL_CHAIN_LOCKDOWN_VPN,
            ConnectivityManagerHidden.FIREWALL_CHAIN_BACKGROUND,
            ConnectivityManagerHidden.FIREWALL_CHAIN_OEM_DENY_1,
            ConnectivityManagerHidden.FIREWALL_CHAIN_OEM_DENY_2,
            ConnectivityManagerHidden.FIREWALL_CHAIN_OEM_DENY_3,
    })
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public @interface FirewallChain {
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            ConnectivityManagerHidden.FIREWALL_RULE_DEFAULT,
            ConnectivityManagerHidden.FIREWALL_RULE_ALLOW,
            ConnectivityManagerHidden.FIREWALL_RULE_DENY
    })
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public @interface FirewallRule {
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static void setUidFirewallRule(@FirewallChain int chain, int uid,  @FirewallRule int rule) throws RemoteException {
        getConnectivityManager().setUidFirewallRule(chain, uid, rule);
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @FirewallRule
    public static int getUidFirewallRule(@FirewallChain int chain, int uid) throws RemoteException {
        return getConnectivityManager().getUidFirewallRule(chain, uid);
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static void setFirewallChainEnabled(@FirewallChain int chain, boolean enable) throws RemoteException {
        getConnectivityManager().setFirewallChainEnabled(chain, enable);
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static boolean getFirewallChainEnabled(@FirewallChain int chain) throws RemoteException {
        return getConnectivityManager().getFirewallChainEnabled(chain);
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static void replaceFirewallChain(@FirewallChain int chain, int[] uids) throws RemoteException {
        getConnectivityManager().replaceFirewallChain(chain, uids);
    }

    private static IConnectivityManager getConnectivityManager() {
        return IConnectivityManager.Stub.asInterface(ProxyBinder.getService(Context.CONNECTIVITY_SERVICE));
    }
}
