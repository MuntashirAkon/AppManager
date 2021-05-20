// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.os.IBinder;
import android.os.Parcelable;

/**
 * Represents a {@code KeySet} that has been declared in the AndroidManifest.xml
 * file for the application.  A {@code KeySet} can be used explicitly to
 * represent a trust relationship with other applications on the device.
 */
public class KeySet implements Parcelable {
    public KeySet(IBinder token) {
        throw new UnsupportedOperationException();
    }

    public IBinder getToken() {
        throw new UnsupportedOperationException();
    }
}