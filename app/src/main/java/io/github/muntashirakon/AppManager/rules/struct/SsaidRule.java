// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RuleType;

public class SsaidRule extends RuleEntry {
    @NonNull
    private String mSsaid;

    public SsaidRule(@NonNull String packageName, @NonNull String ssaid) {
        super(packageName, STUB, RuleType.SSAID);
        mSsaid = ssaid;
    }

    public SsaidRule(@NonNull String packageName, @NonNull StringTokenizer tokenizer) {
        super(packageName, STUB, RuleType.SSAID);
        if (tokenizer.hasMoreElements()) {
            mSsaid = tokenizer.nextElement().toString();
        } else throw new IllegalArgumentException("Invalid format: ssaid not found");
    }

    @NonNull
    public String getSsaid() {
        return mSsaid;
    }

    public void setSsaid(@NonNull String ssaid) {
        mSsaid = ssaid;
    }

    @NonNull
    @Override
    public String toString() {
        return "SsaidRule{" +
                "packageName='" + packageName + '\'' +
                ", ssaid='" + mSsaid + '\'' +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + mSsaid;
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
