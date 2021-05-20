// SPDX-License-Identifier: Apache-2.0

package android.net;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

public interface INetworkPolicyManager extends IInterface {
    /**
     * Control UID policies.
     */
    void setUidPolicy(int uid, int policy) throws RemoteException;

    void addUidPolicy(int uid, int policy) throws RemoteException;

    void removeUidPolicy(int uid, int policy) throws RemoteException;

    int getUidPolicy(int uid) throws RemoteException;

    int[] getUidsWithPolicy(int policy) throws RemoteException;

    void registerListener(INetworkPolicyListener listener) throws RemoteException;

    void unregisterListener(INetworkPolicyListener listener) throws RemoteException;

    /**
     * Control if background data is restricted system-wide.
     */
    void setRestrictBackground(boolean restrictBackground) throws RemoteException;

    boolean getRestrictBackground() throws RemoteException;

    /**
     * Gets the restrict background status based on the caller's UID:
     * 1 - disabled
     * 2 - whitelisted
     * 3 - enabled
     */
    @RequiresApi(Build.VERSION_CODES.N)
    int getRestrictBackgroundByCaller() throws RemoteException;

    abstract class Stub extends Binder implements INetworkPolicyManager {
        public static INetworkPolicyManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}