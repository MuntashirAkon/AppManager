// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import androidx.annotation.IntDef;

@IntDef({IntervalType.INTERVAL_DAILY, IntervalType.INTERVAL_WEEKLY})
public @interface IntervalType {
    // These numbers are tied to "usage_interval_dropdown_list" array.
    // DO NOT MODIFY!
    int INTERVAL_DAILY = 0;
    int INTERVAL_WEEKLY = 1;
}