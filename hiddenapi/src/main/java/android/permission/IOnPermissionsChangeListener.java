// SPDX-License-Identifier: Apache-2.0

package android.permission;

import android.os.Build;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.R)
public interface IOnPermissionsChangeListener extends IInterface {
    void onPermissionsChanged(int uid) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // 14 r50
    void onPermissionsChanged(int uid, String persistentDeviceId) throws RemoteException;
}