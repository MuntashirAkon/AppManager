// SPDX-License-Identifier: GPL-3.0-or-later

package android.os.storage;

import android.os.Build;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

/**
 * @deprecated Replaced with {@link IStorageManager} in SDK 26 (Android O)
 */
@Deprecated
public interface IMountService extends IInterface {
    /**
     * Returns list of all mountable volumes.
     *
     * @deprecated Replaced by {@link #getVolumeList(int, String, int)} in SDK 23 (Android M)
     */
    @Deprecated
    StorageVolume[] getVolumeList() throws RemoteException;

    /**
     * Returns list of all mountable volumes.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    StorageVolume[] getVolumeList(int uid, String packageName, int flags) throws RemoteException;

    abstract class Stub {
        public static IMountService asInterface(android.os.IBinder obj) {
            return HiddenUtil.throwUOE(obj);
        }
    }
}