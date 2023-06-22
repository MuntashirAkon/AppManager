// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.rules.RuleType;

public class NetPolicyRule extends RuleEntry {
    @NetworkPolicyManagerCompat.NetPolicy
    private int mNetPolicies;

    public NetPolicyRule(@NonNull String packageName, @NetworkPolicyManagerCompat.NetPolicy int netPolicies) {
        super(packageName, STUB, RuleType.NET_POLICY);
        mNetPolicies = netPolicies;
    }

    public NetPolicyRule(@NonNull String packageName, @NonNull StringTokenizer tokenizer) {
        super(packageName, STUB, RuleType.NET_POLICY);
        if (tokenizer.hasMoreElements()) {
            mNetPolicies = Integer.parseInt(tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: netPolicies not found");
    }

    @NetworkPolicyManagerCompat.NetPolicy
    public int getPolicies() {
        return mNetPolicies;
    }

    public void setPolicies(@NetworkPolicyManagerCompat.NetPolicy int netPolicies) {
        mNetPolicies = netPolicies;
    }

    @NonNull
    @Override
    public String toString() {
        return "NetPolicyRule{" +
                "packageName='" + packageName + '\'' +
                ", netPolicies=" + mNetPolicies +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + mNetPolicies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetPolicyRule)) return false;
        if (!super.equals(o)) return false;
        NetPolicyRule that = (NetPolicyRule) o;
        return mNetPolicies == that.mNetPolicies;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mNetPolicies);
    }
}
