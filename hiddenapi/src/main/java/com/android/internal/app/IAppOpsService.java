// SPDX-License-Identifier: Apache-2.0

package com.android.internal.app;

import android.os.Build;
import android.os.IInterface;
import android.os.Parcelable;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import java.util.List;

public interface IAppOpsService extends IInterface {
    int checkOperation(int code, int uid, String packageName) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    int permissionToOpCode(String permission) throws RemoteException;

    int checkPackage(int uid, String packageName) throws RemoteException;

    List<Parcelable> getPackagesForOps(int[] ops) throws RemoteException;

    List<Parcelable> getOpsForPackage(int uid, String packageName, int[] ops)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    List<Parcelable> getUidOps(int uid, int[] ops) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    void setUidMode(int code, int uid, int mode) throws RemoteException;

    void setMode(int code, int uid, String packageName, int mode) throws RemoteException;

    // Removed in 22
    void resetAllModes() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    void resetAllModes(int reqUserId, String reqPackageName) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    boolean isOperationActive(int code, int uid, String packageName) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    int checkOperationRaw(int code, int uid, String packageName) throws RemoteException;

    abstract class Stub {
        public static IAppOpsService asInterface(android.os.IBinder obj) {
            return null;
        }
    }
}
