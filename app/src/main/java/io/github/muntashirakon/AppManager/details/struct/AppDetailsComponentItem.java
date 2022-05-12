// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import android.content.pm.ComponentInfo;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;

/**
 * Stores individual app details component item
 */
public class AppDetailsComponentItem extends AppDetailsItem<ComponentInfo> {
    private boolean mIsTracker;
    private ComponentRule mRule;
    private boolean mIsDisabled;

    public AppDetailsComponentItem(@NonNull ComponentInfo componentInfo) {
        super(componentInfo);
        mIsDisabled = !componentInfo.isEnabled();
    }

    public boolean isTracker() {
        return mIsTracker;
    }

    public void setTracker(boolean tracker) {
        mIsTracker = tracker;
    }

    public boolean isBlocked() {
        if (mRule == null) {
            return false;
        }
        return mRule.isBlocked() && (mRule.isIfw() || isDisabled());
    }

    public ComponentRule getRule() {
        return mRule;
    }

    public void setRule(ComponentRule rule) {
        mRule = rule;
    }

    public boolean isDisabled() {
        return mIsDisabled;
    }

    public void setDisabled(boolean disabled) {
        mIsDisabled = disabled;
    }
}
