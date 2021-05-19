// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RulesStorageManager;

public class BatteryOptimizationRule extends RuleEntry {
    private boolean enabled;

    public BatteryOptimizationRule(@NonNull String packageName, boolean enabled) {
        super(packageName, STUB, RulesStorageManager.Type.BATTERY_OPT);
        this.enabled = enabled;
    }

    public BatteryOptimizationRule(@NonNull String packageName, @NonNull StringTokenizer tokenizer) {
        super(packageName, STUB, RulesStorageManager.Type.BATTERY_OPT);
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
}
