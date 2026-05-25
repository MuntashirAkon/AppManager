// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.content.IntentSender;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

public interface IPackageInstallerSession extends IInterface {
    void setClientProgress(float progress) throws RemoteException;

    void addClientProgress(float progress) throws RemoteException;

    String[] getNames() throws RemoteException;

    ParcelFileDescriptor openWrite(String name, long offsetBytes, long lengthBytes)
            throws RemoteException;

    ParcelFileDescriptor openRead(String name) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    void write(String name, long offsetBytes, long lengthBytes, ParcelFileDescriptor fd)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.N)
    void removeSplit(String splitName) throws RemoteException;

    void close() throws RemoteException;

    /**
     * @deprecated Replaced in API 28 (Android 9) with {@link #commit(IntentSender, boolean)}
     */
    @Deprecated
    void commit(IntentSender statusReceiver) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    void commit(IntentSender statusReceiver, boolean forTransferred) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    void transfer(String packageName) throws RemoteException;

    void abandon() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    boolean isMultiPackage() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    int[] getChildSessionIds() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    void addChildSessionId(int sessionId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    void removeChildSessionId(int sessionId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    int getParentSessionId() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    boolean isStaged() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    void requestUserPreapproval(PackageInstaller.PreapprovalDetails details, IntentSender statusReceiver) throws RemoteException;

    abstract class Stub extends Binder implements IPackageInstallerSession {

        public static IPackageInstallerSession asInterface(IBinder binder) {
            return HiddenUtil.throwUOE(binder);
        }
    }
}