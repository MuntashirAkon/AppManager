// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RuleType;

public class SsaidRule extends RuleEntry {
    @NonNull
    private String ssaid;

    public SsaidRule(@NonNull String packageName, @NonNull String ssaid) {
        super(packageName, STUB, RuleType.SSAID);
        this.ssaid = ssaid;
    }

    public SsaidRule(@NonNull String packageName, @NonNull StringTokenizer tokenizer) {
        super(packageName, STUB, RuleType.SSAID);
        if (tokenizer.hasMoreElements()) {
            ssaid = tokenizer.nextElement().toString();
        } else throw new IllegalArgumentException("Invalid format: ssaid not found");
    }

    @NonNull
    public String getSsaid() {
        return ssaid;
    }

    public void setSsaid(@NonNull String ssaid) {
        this.ssaid = ssaid;
    }

    @NonNull
    @Override
    public String toString() {
        return "SsaidRule{" +
                "packageName='" + packageName + '\'' +
                ", ssaid='" + ssaid + '\'' +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + ssaid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SsaidRule)) return false;
        if (!super.equals(o)) return false;
        SsaidRule ssaidRule = (SsaidRule) o;
        return getSsaid().equals(ssaidRule.getSsaid());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getSsaid());
    }
}
