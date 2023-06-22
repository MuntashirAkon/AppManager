// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.rules.RuleType;

public class AppOpRule extends RuleEntry {
    private final int mOp;
    @AppOpsManagerCompat.Mode
    private int mMode;

    public AppOpRule(@NonNull String packageName, int op, @AppOpsManagerCompat.Mode int mode) {
        super(packageName, String.valueOf(op), RuleType.APP_OP);
        mOp = op;
        mMode = mode;
    }

    public AppOpRule(@NonNull String packageName, String opInt, @NonNull StringTokenizer tokenizer)
            throws RuntimeException {
        super(packageName, opInt, RuleType.APP_OP);
        mOp = Integer.parseInt(opInt);
        if (tokenizer.hasMoreElements()) {
            mMode = Integer.parseInt(tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: mode not found");
    }

    public int getOp() {
        return mOp;
    }

    @AppOpsManagerCompat.Mode
    public int getMode() {
        return mMode;
    }

    public void setMode(@AppOpsManagerCompat.Mode int mode) {
        mMode = mode;
    }

    @NonNull
    @Override
    public String toString() {
        return "AppOpRule{" +
                "packageName='" + packageName + '\'' +
                ", op=" + mOp +
                ", mode=" + mMode +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + mOp + "\t" + type.name() + "\t" + mMode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppOpRule)) return false;
        if (!super.equals(o)) return false;
        AppOpRule appOpRule = (AppOpRule) o;
        return getOp() == appOpRule.getOp() && getMode() == appOpRule.getMode();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getOp(), getMode());
    }
}
