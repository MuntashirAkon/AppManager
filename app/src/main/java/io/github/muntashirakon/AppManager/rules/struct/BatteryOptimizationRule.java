// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RuleType;

public class BatteryOptimizationRule extends RuleEntry {
    private boolean enabled;

    public BatteryOptimizationRule(@NonNull String packageName, boolean enabled) {
        super(packageName, STUB, RuleType.BATTERY_OPT);
        this.enabled = enabled;
    }

    public BatteryOptimizationRule(@NonNull String packageName, @NonNull StringTokenizer tokenizer) {
        super(packageName, STUB, RuleType.BATTERY_OPT);
        if (tokenizer.hasMoreElements()) {
            enabled = Boolean.parseBoolean(tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: enabled not found");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @NonNull
    @Override
    public String toString() {
        return "BatteryOptimizationRule{" +
                "packageName='" + packageName + '\'' +
                ", enabled=" + enabled +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BatteryOptimizationRule)) return false;
        if (!super.equals(o)) return false;
        BatteryOptimizationRule that = (BatteryOptimizationRule) o;
        return isEnabled() == that.isEnabled();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), isEnabled());
    }
}
