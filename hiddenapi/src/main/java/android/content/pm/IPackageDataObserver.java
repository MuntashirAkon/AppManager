// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface IPackageDataObserver extends IInterface {
    void onRemoveCompleted(String packageName, boolean succeeded) throws RemoteException;

    abstract class Stub extends Binder implements IPackageDataObserver {
        public static IPackageDataObserver asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}