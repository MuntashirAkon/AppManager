// SPDX-License-Identifier: Apache-2.0

package android.content.pm.verify.domain;

import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

@RequiresApi(Build.VERSION_CODES.S)
public interface IDomainVerificationManager extends IInterface {
    /**
     * Retrieve the user state for the given package and the user.
     *
     * @param packageName The app to query state for.
     * @return The user selection verification data for the given package for the user, or null if
     * the package does not declare any HTTP/HTTPS domains.
     */
    @Nullable
    DomainVerificationUserState getDomainVerificationUserState(String packageName,
            int userId) throws RemoteException;

    /**
     * Change whether the given packageName is allowed to handle BROWSABLE and DEFAULT category web
     * (HTTP/HTTPS) {@link Intent} Activity open requests. The final state is determined along with
     * the verification status for the specific domain being opened and other system state. An app
     * with this enabled is not guaranteed to be the sole link handler for its domains.
     * <p>
     * By default, all apps are allowed to open links. Users must disable them explicitly.
     */
    void setDomainVerificationLinkHandlingAllowed(String packageName, boolean allowed, int userId) throws RemoteException;

    abstract class Stub extends Binder implements IDomainVerificationManager {
        public static IDomainVerificationManager asInterface(IBinder obj) {
            return HiddenUtil.throwUOE(obj);
        }

        @Override
        public android.os.IBinder asBinder() {
            return this;
        }
    }
}
