// SPDX-License-Identifier: Apache-2.0

package android.app;

import android.content.ComponentName;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface IServiceConnection extends IInterface {
    abstract class Stub extends Binder implements IServiceConnection {
        public static IServiceConnection asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

    }

    void connected(ComponentName name, IBinder service, boolean dead) throws RemoteException;
}