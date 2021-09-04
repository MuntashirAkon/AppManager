// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.os.Parcelable;

import misc.utils.HiddenUtil;

public class VerifierDeviceIdentity implements Parcelable {
    /**
     * Create a verifier device identity from a long.
     *
     * @param identity device identity in a 64-bit integer.
     */
    public VerifierDeviceIdentity(long identity) {
        HiddenUtil.throwUOE(identity);
    }

    /**
     * Generate a new device identity.
     *
     * @return random uniformly-distributed device identity
     */
    public static VerifierDeviceIdentity generate() {
        return HiddenUtil.throwUOE();
    }

    public static VerifierDeviceIdentity parse(String deviceIdentity)
            throws IllegalArgumentException {
        return HiddenUtil.throwUOE(deviceIdentity);
    }
}
