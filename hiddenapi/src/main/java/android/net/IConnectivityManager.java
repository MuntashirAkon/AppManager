// SPDX-License-Identifier: Apache-2.0

package android.net;

import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

public interface IConnectivityManager extends IInterface {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    void setUidFirewallRule(int chain, int uid, int rule) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    int getUidFirewallRule(int chain, int uid) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    void setFirewallChainEnabled(int chain, boolean enable) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    boolean getFirewallChainEnabled(int chain) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    void replaceFirewallChain(int chain, int[] uids) throws RemoteException;

    abstract class Stub extends android.os.Binder implements IConnectivityManager {

        public static IConnectivityManager asInterface(IBinder obj) {
            return HiddenUtil.throwUOE(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}
