// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.servermanager.NetworkPolicyManagerCompat;

public class NetPolicyRule extends RuleEntry {
    @NetworkPolicyManagerCompat.NetPolicy
    private int netPolicies;

    public NetPolicyRule(@NonNull String packageName, @NetworkPolicyManagerCompat.NetPolicy int netPolicies) {
        super(packageName, STUB, RulesStorageManager.Type.NET_POLICY);
        this.netPolicies = netPolicies;
    }

    public NetPolicyRule(@NonNull String packageName, @NonNull StringTokenizer tokenizer) {
        super(packageName, STUB, RulesStorageManager.Type.NET_POLICY);
        if (tokenizer.hasMoreElements()) {
            netPolicies = Integer.parseInt(tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: netPolicies not found");
    }

    @NetworkPolicyManagerCompat.NetPolicy
    public int getPolicies() {
        return netPolicies;
    }

    public void setPolicies(@NetworkPolicyManagerCompat.NetPolicy int netPolicies) {
        this.netPolicies = netPolicies;
    }

    @NonNull
    @Override
    public String toString() {
        return "NetPolicyRule{" +
                "packageName='" + packageName + '\'' +
                ", netPolicies=" + netPolicies +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + netPolicies;
    }
}
