// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.os.IBinder;
import android.os.Parcelable;

import misc.utils.HiddenUtil;

/**
 * Represents a {@code KeySet} that has been declared in the AndroidManifest.xml
 * file for the application.  A {@code KeySet} can be used explicitly to
 * represent a trust relationship with other applications on the device.
 */
public class KeySet implements Parcelable {
    public KeySet(IBinder token) {
        HiddenUtil.throwUOE(token);
    }

    public IBinder getToken() {
        return HiddenUtil.throwUOE();
    }
}
