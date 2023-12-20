// SPDX-License-Identifier: Apache-2.0

package android.hardware.input;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.view.InputEvent;

import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

public interface IInputManager extends IInterface {
    /**
     * Injects an input event into the system. The caller must have the INJECT_EVENTS permssion.
     * This method exists only for compatibility purposes and may be removed in a future release.
     */
    boolean injectInputEvent(InputEvent ev, int mode) throws RemoteException;

    /**
     * Injects an input event into the system. The caller must have the INJECT_EVENTS permission.
     * The caller can target windows owned by a certain UID by providing a valid UID, or by
     * providing {@link android.os.Process#INVALID_UID} to target all windows.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    boolean injectInputEventToTarget(InputEvent ev, int mode, int targetUid) throws RemoteException;

    abstract class Stub extends Binder implements IInputManager {
        public static IInputManager asInterface(IBinder obj) {
            return HiddenUtil.throwUOE(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}
