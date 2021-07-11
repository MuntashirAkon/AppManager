// SPDX-License-Identifier: Apache-2.0

package android.content;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;

public interface IIntentReceiver extends IInterface {

    void performReceive(Intent intent, int resultCode, String data, Bundle extras, boolean ordered,
                        boolean sticky, int sendingUser);

    abstract class Stub extends Binder implements IIntentReceiver {
        public static IIntentSender asInterface(android.os.IBinder obj) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}