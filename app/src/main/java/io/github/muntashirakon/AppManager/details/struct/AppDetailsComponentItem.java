// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import android.content.pm.ComponentInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;

/**
 * Stores individual app details component item
 */
public class AppDetailsComponentItem extends AppDetailsItem<ComponentInfo> {
    public CharSequence label;
    public boolean canLaunch;

    private boolean mIsTracker;
    @Nullable
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

    @Nullable
    public ComponentRule getRule() {
        return mRule;
    }

    public void setRule(@Nullable ComponentRule rule) {
        mRule = rule;
    }

    public boolean isDisabled() {
        return mIsDisabled;
    }

    public void setDisabled(boolean disabled) {
        mIsDisabled = disabled;
    }
}
