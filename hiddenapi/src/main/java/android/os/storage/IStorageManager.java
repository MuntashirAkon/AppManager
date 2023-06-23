// SPDX-License-Identifier: GPL-3.0-or-later

package android.os.storage;

import android.os.Build;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

@RequiresApi(Build.VERSION_CODES.O)
public interface IStorageManager extends IInterface {
    /**
     * Returns list of all mountable volumes for the specified userId
     */
    // userId was uid and callingPackage was packageName until Android 13
    StorageVolume[] getVolumeList(int uidOrA13UserId, String callingPackage, int flags) throws RemoteException;

    abstract class Stub {
        public static IStorageManager asInterface(android.os.IBinder obj) {
            return HiddenUtil.throwUOE(obj);
        }
    }
}
