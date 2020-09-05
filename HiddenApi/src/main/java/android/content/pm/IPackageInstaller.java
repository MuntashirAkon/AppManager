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
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import java.util.List;

public interface IPackageInstaller extends IInterface {

    int createSession(PackageInstaller.SessionParams params, String installerPackageName, int userId)
            throws RemoteException;

    void updateSessionAppIcon(int sessionId, Bitmap appIcon)
            throws RemoteException;

    void updateSessionAppLabel(int sessionId, String appLabel)
            throws RemoteException;

    void abandonSession(int sessionId)
            throws RemoteException;

    IPackageInstallerSession openSession(int sessionId)
            throws RemoteException;

    PackageInstaller.SessionInfo getSessionInfo(int sessionId)
            throws RemoteException;

    ParceledListSlice<PackageInstaller.SessionInfo> getAllSessions(int userId)
            throws RemoteException;

    ParceledListSlice<PackageInstaller.SessionInfo> getMySessions(String installerPackageName, int userId)
            throws RemoteException;

    @RequiresApi(29)
    ParceledListSlice<PackageInstaller.SessionInfo> getStagedSessions()
            throws RemoteException;

    void registerCallback(IPackageInstallerCallback callback, int userId)
            throws RemoteException;

    void unregisterCallback(IPackageInstallerCallback callback)
            throws RemoteException;

    // removed from 26
    void uninstall(String packageName, String callerPackageName, int flags,
                   IntentSender statusReceiver, int userId);

    @RequiresApi(26)
    void uninstall(VersionedPackage versionedPackage, String callerPackageName, int flags,
                   IntentSender statusReceiver, int userId)
            throws RemoteException;

    @RequiresApi(29)
    void installExistingPackage(String packageName, int installFlags, int installReason,
                                IntentSender statusReceiver, int userId, List<String> whiteListedPermissions)
            throws RemoteException;

    void setPermissionsResult(int sessionId, boolean accepted)
            throws RemoteException;

    abstract class Stub extends Binder implements IPackageInstaller {

        public static IPackageInstaller asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }
}