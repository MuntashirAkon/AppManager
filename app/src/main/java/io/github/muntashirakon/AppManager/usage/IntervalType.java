// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import androidx.annotation.IntDef;

@IntDef({IntervalType.INTERVAL_DAILY, IntervalType.INTERVAL_WEEKLY})
public @interface IntervalType {
    int INTERVAL_DAILY = 0;
    int INTERVAL_WEEKLY = 1;
}