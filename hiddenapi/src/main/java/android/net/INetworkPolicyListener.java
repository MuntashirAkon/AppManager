// SPDX-License-Identifier: Apache-2.0

package android.net;

import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface INetworkPolicyListener extends IInterface {

    void onUidRulesChanged(int uid, int uidRules) throws RemoteException;

    void onMeteredIfacesChanged(java.lang.String[] meteredIfaces) throws RemoteException;

    void onRestrictBackgroundChanged(boolean restrictBackground) throws RemoteException;

    void onUidPoliciesChanged(int uid, int uidPolicies) throws RemoteException;

    void onSubscriptionOverride(int subId, int overrideMask, int overrideValue) throws RemoteException;

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
