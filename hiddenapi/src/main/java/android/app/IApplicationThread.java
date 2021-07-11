// SPDX-License-Identifier: Apache-2.0

package android.app;

import android.os.IBinder;

/**
 * System private API for communicating with the application.  This is given to
 * the activity manager by an application  when it starts up, for the activity
 * manager to tell the application about things it needs to do.
 */
public interface IApplicationThread extends android.os.IInterface {
    abstract class Stub extends android.os.Binder implements android.app.IApplicationThread {
        public static android.app.IApplicationThread asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}