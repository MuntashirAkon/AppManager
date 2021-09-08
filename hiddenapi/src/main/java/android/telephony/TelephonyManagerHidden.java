// SPDX-License-Identifier: GPL-3.0-or-later

package android.telephony;

import android.annotation.Nullable;
import android.os.Build;

import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

@RefineAs(TelephonyManager.class)
public class TelephonyManagerHidden {
    /**
     * Returns the unique subscriber ID, for example, the IMSI for a GSM phone
     * for a subscription.
     * Return null if it is unavailable.
     *
     * @param subId whose subscriber id is returned
     * @deprecated Removed in Android 5.1 (Lollipop MR1)
     */
    @Deprecated
    @Nullable
    public String getSubscriberId(long subId) {
        return HiddenUtil.throwUOE(subId);
    }

    /**
     * Returns the unique subscriber ID, for example, the IMSI for a GSM phone
     * for a subscription.
     * Return null if it is unavailable.
     * <p>
     * Requires Permission:
     * {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     *
     * @param subId whose subscriber id is returned
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    @Nullable
    public String getSubscriberId(int subId) {
        return HiddenUtil.throwUOE(subId);
    }
}
