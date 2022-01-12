// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import android.content.pm.ComponentInfo;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;

/**
 * Stores individual app details component item
 */
public class AppDetailsComponentItem extends AppDetailsItem<ComponentInfo> {
    private boolean isTracker = false;
    private ComponentRule rule;

    public AppDetailsComponentItem(@NonNull ComponentInfo componentInfo) {
        super(componentInfo);
    }

    public boolean isTracker() {
        return isTracker;
    }

    public void setTracker(boolean tracker) {
        isTracker = tracker;
    }

    public boolean isBlocked() {
        return rule != null && rule.isBlocked();
    }

    public ComponentRule getRule() {
        return rule;
    }

    public void setRule(ComponentRule rule) {
        this.rule = rule;
    }
}
