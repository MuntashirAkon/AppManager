// SPDX-License-Identifier: Apache-2.0

package android.net;

import android.os.IBinder;
import android.os.IInterface;

public interface INetworkPolicyListener extends IInterface {
    abstract class Stub extends android.os.Binder implements INetworkPolicyListener {

        public static INetworkPolicyListener asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}
