// SPDX-License-Identifier: Apache-2.0

package android.app;

import android.content.UriPermission;
import android.content.pm.ParceledListSlice;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

/**
 * Interface for managing an app's permission to access a particular URI.
 */
@RequiresApi(Build.VERSION_CODES.Q)
public interface IUriGrantsManager extends android.os.IInterface {
    abstract class Stub extends Binder implements IUriGrantsManager {
        public static IUriGrantsManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }

    void grantUriPermissionFromOwner(IBinder owner, int fromUid, String targetPkg, Uri uri, int mode, int sourceUserId, int targetUserId) throws RemoteException;

    /**
     * Gets the URI permissions granted to an arbitrary package (or all packages if null)
     * NOTE: this is different from getUriPermissions(), which returns the URIs the package
     * granted to another packages (instead of those granted to it).
     */
    ParceledListSlice<GrantedUriPermission> getGrantedUriPermissions(String packageName, int userId) throws RemoteException;

    /**
     * Clears the URI permissions granted to an arbitrary package.
     */
    void clearGrantedUriPermissions(String packageName, int userId) throws RemoteException;

    ParceledListSlice<UriPermission> getUriPermissions(String packageName, boolean incoming, boolean persistedOnly) throws RemoteException;
}