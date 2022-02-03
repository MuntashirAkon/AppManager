// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RuleType;

public class MagiskDenyListRule extends RuleEntry {
    private boolean isDenied;

    public MagiskDenyListRule(@NonNull String packageName, @NonNull String processName, boolean isDenied) {
        super(packageName, processName, RuleType.MAGISK_DENY_LIST);
        this.isDenied = isDenied;
    }

    public MagiskDenyListRule(@NonNull String packageName, @NonNull String processName, @NonNull StringTokenizer tokenizer)
            throws IllegalArgumentException {
        super(packageName, processName, RuleType.MAGISK_DENY_LIST);
        if (tokenizer.hasMoreElements()) {
            isDenied = Boolean.parseBoolean(tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: isDenied not found");
    }

    public boolean isDenied() {
        return isDenied;
    }

    public void setDenied(boolean denied) {
        isDenied = denied;
    }

    public String getProcessName() {
        return name;
    }

    @NonNull
    @Override
    public String toString() {
        return "MagiskDenyListRule{" +
                "packageName='" + packageName + '\'' +
                "processName='" + name + '\'' +
                ", isDenied=" + isDenied +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + isDenied;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MagiskDenyListRule)) return false;
        if (!super.equals(o)) return false;
        MagiskDenyListRule that = (MagiskDenyListRule) o;
        return isDenied() == that.isDenied();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), isDenied());
    }
}
