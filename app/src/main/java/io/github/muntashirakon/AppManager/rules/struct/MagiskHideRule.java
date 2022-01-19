// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RuleType;

public class MagiskHideRule extends RuleEntry {
    private boolean isHidden;

    public MagiskHideRule(@NonNull String packageName, @NonNull String processName, boolean isHidden) {
        super(packageName, processName, RuleType.MAGISK_HIDE);
        this.isHidden = isHidden;
    }

    public MagiskHideRule(@NonNull String packageName, @NonNull String processName, @NonNull StringTokenizer tokenizer)
            throws IllegalArgumentException {
        super(packageName, processName.equals(STUB) ? packageName : processName, RuleType.MAGISK_HIDE);
        if (tokenizer.hasMoreElements()) {
            isHidden = Boolean.parseBoolean(tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: isHidden not found");
    }

    public boolean isHidden() {
        return isHidden;
    }

    public void setHidden(boolean hidden) {
        isHidden = hidden;
    }

    public String getProcessName() {
        return name.equals(STUB) ? packageName : name;
    }

    @NonNull
    @Override
    public String toString() {
        return "MagiskHideRule{" +
                "packageName='" + packageName + '\'' +
                "processName='" + getProcessName() + '\'' +
                ", isHidden=" + isHidden +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + isHidden;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MagiskHideRule)) return false;
        if (!super.equals(o)) return false;
        MagiskHideRule that = (MagiskHideRule) o;
        return isHidden() == that.isHidden();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), isHidden());
    }
}
