// SPDX-License-Identifier: GPL-3.0-or-later

package android.telephony;

import android.annotation.Nullable;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(TelephonyManager.class)
public class TelephonyManagerHidden {
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
    @Nullable
    public String getSubscriberId(long subId) {
        throw new UnsupportedOperationException();
    }
}
