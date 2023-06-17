// SPDX-License-Identifier: Apache-2.0

package android.os;

import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

@RequiresApi(Build.VERSION_CODES.M)
public interface IDeviceIdleController extends IInterface {
    void addPowerSaveWhitelistApp(String name) throws RemoteException;
    void removePowerSaveWhitelistApp(String name) throws RemoteException;

    boolean isPowerSaveWhitelistExceptIdleApp(String name) throws RemoteException;
    boolean isPowerSaveWhitelistApp(String name) throws RemoteException;

    abstract class Stub {
        public static IDeviceIdleController asInterface(android.os.IBinder obj) {
            return HiddenUtil.throwUOE(obj);
        }
    }
}
