// SPDX-License-Identifier: Apache-2.0

package com.android.internal.os;

import android.content.Context;

import misc.utils.HiddenUtil;

public class PowerProfile {
    /**
     * Battery capacity in milliAmpHour (mAh).
     */
    public static final String POWER_BATTERY_CAPACITY = "battery.capacity";

    public PowerProfile(Context context) {
        HiddenUtil.throwUOE(context);
    }

    /**
     * Returns the average current in mA consumed by the subsystem
     *
     * @param type the subsystem type
     * @return the average current in milliAmps.
     */
    public double getAveragePower(String type) {
        return HiddenUtil.throwUOE(type);
    }

    /**
     * Returns the battery capacity, if available, in milli Amp Hours. If not available,
     * it returns zero.
     *
     * @return the battery capacity in mAh
     */
    public double getBatteryCapacity() {
        return HiddenUtil.throwUOE();
    }
}
