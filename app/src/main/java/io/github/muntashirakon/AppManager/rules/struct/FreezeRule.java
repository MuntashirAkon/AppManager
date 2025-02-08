// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

public class FreezeRule extends RuleEntry {
    @FreezeUtils.FreezeType
    private int mFreezeType;

    public FreezeRule(@NonNull String packageName, @FreezeUtils.FreezeType int freezeType) {
        super(packageName, STUB, RuleType.FREEZE);
        mFreezeType = freezeType;
    }

    public FreezeRule(@NonNull String packageName, @NonNull StringTokenizer tokenizer) {
        super(packageName, STUB, RuleType.FREEZE);
        if (tokenizer.hasMoreElements()) {
            mFreezeType = Integer.parseInt(tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: freeze_type not found");
    }

    public int getFreezeType() {
        return mFreezeType;
    }

    public void setFreezeType(@FreezeUtils.FreezeType int freezeType) {
        mFreezeType = freezeType;
    }

    @NonNull
    @Override
    public String toString() {
        return "FreezeRule{" +
                "mFreezeType=" + mFreezeType +
                ", packageName='" + packageName + '\'' +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + mFreezeType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FreezeRule)) return false;
        if (!super.equals(o)) return false;
        FreezeRule freezeRule = (FreezeRule) o;
        return getFreezeType() == freezeRule.getFreezeType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getFreezeType());
    }
}
