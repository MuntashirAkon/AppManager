// SPDX-License-Identifier: Apache-2.0

package android.content;

import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

public interface IIntentSender extends IInterface {

    int send(int code, Intent intent, String resolvedType,
             IIntentReceiver finishedReceiver, String requiredPermission) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.M)
    int send(int code, Intent intent, String resolvedType,
             IIntentReceiver finishedReceiver, String requiredPermission, Bundle options) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    void send(int code, Intent intent, String resolvedType, IBinder whitelistToken,
              IIntentReceiver finishedReceiver, String requiredPermission, Bundle options) throws RemoteException;

    abstract class Stub extends Binder implements IIntentSender {
        public static IIntentSender asInterface(android.os.IBinder obj) {
            throw new UnsupportedOperationException();
        }

        @Override
        public android.os.IBinder asBinder() {
            throw new UnsupportedOperationException();
        }
    }
}