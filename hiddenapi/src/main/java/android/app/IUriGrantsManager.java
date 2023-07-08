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

import misc.utils.HiddenUtil;

/**
 * Interface for managing an app's permission to access a particular URI.
 */
@RequiresApi(Build.VERSION_CODES.Q)
public interface IUriGrantsManager extends android.os.IInterface {
    void takePersistableUriPermission(Uri uri, int modeFlags, String toPackage, int userId) throws RemoteException;

    void releasePersistableUriPermission(Uri uri, int modeFlags, String toPackage, int userId) throws RemoteException;

    void grantUriPermissionFromOwner(IBinder owner, int fromUid, String targetPkg, Uri uri, int mode, int sourceUserId, int targetUserId) throws RemoteException;

    /**
     * Gets the URI permissions granted to an arbitrary package (or all packages if null)
     * <p>
     * NOTE: this is different from {@link #getUriPermissions(String, boolean, boolean)}, which returns the URIs the
     * package granted to another packages (instead of those granted to it).
     */
    ParceledListSlice<GrantedUriPermission> getGrantedUriPermissions(String packageName, int userId) throws RemoteException;

    /**
     * Clears the URI permissions granted to an arbitrary package.
     */
    void clearGrantedUriPermissions(String packageName, int userId) throws RemoteException;

    ParceledListSlice<UriPermission> getUriPermissions(String packageName, boolean incoming, boolean persistedOnly) throws RemoteException;

    abstract class Stub extends Binder implements IUriGrantsManager {
        public static IUriGrantsManager asInterface(IBinder obj) {
            return HiddenUtil.throwUOE(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}