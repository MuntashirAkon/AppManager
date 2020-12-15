/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package android.content.pm;

import android.content.IntentSender;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

public interface IPackageInstallerSession extends IInterface {
    void setClientProgress(float progress)
            throws RemoteException;

    void addClientProgress(float progress)
            throws RemoteException;

    String[] getNames()
            throws RemoteException;

    ParcelFileDescriptor openWrite(String name, long offsetBytes, long lengthBytes)
            throws RemoteException;

    ParcelFileDescriptor openRead(String name)
            throws RemoteException;

    @RequiresApi(27)
    void write(String name, long offsetBytes, long lengthBytes, ParcelFileDescriptor fd)
            throws RemoteException;

    @RequiresApi(24)
    void removeSplit(String splitName)
            throws RemoteException;

    void close()
            throws RemoteException;

    // removed from 28
    void commit(IntentSender statusReceiver)
            throws RemoteException;

    @RequiresApi(28)
    void commit(IntentSender statusReceiver, boolean forTransferred)
            throws RemoteException;

    @RequiresApi(28)
    void transfer(String packageName)
            throws RemoteException;

    void abandon()
            throws RemoteException;

    @RequiresApi(29)
    boolean isMultiPackage()
            throws RemoteException;

    @RequiresApi(29)
    int[] getChildSessionIds()
            throws RemoteException;

    @RequiresApi(29)
    void addChildSessionId(int sessionId)
            throws RemoteException;

    @RequiresApi(29)
    void removeChildSessionId(int sessionId)
            throws RemoteException;

    @RequiresApi(29)
    int getParentSessionId()
            throws RemoteException;

    @RequiresApi(29)
    boolean isStaged()
            throws RemoteException;

    abstract class Stub extends Binder implements IPackageInstallerSession {

        public static IPackageInstallerSession asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }
}