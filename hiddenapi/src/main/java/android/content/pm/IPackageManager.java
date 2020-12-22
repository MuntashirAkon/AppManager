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

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface IPackageManager extends IInterface {
    PackageInfo getPackageInfo(String packageName, int flags, int userId) throws RemoteException;

    PermissionInfo getPermissionInfo(String name, int flags) throws RemoteException;

    PermissionGroupInfo getPermissionGroupInfo(String name, int flags) throws RemoteException;

    void grantRuntimePermission(String packageName, String permissionName, int userId) throws RemoteException;

    void revokeRuntimePermission(String packageName, String permissionName, int userId) throws RemoteException;

    void resetRuntimePermissions() throws RemoteException;

    int getPermissionFlags(String permissionName, String packageName, int userId) throws RemoteException;

    String[] getAppOpPermissionPackages(String permissionName) throws RemoteException;

    ResolveInfo resolveIntent(Intent intent, String resolvedType, int flags, int userId) throws RemoteException;

    ApplicationInfo getApplicationInfo(String packageName, int flags, int userId) throws RemoteException;

    int getPackageUid(String packageName, int flags, int userId) throws RemoteException;

    @TargetApi(23)
    int getPackageUid(String packageName, int userId) throws RemoteException;

    IPackageInstaller getPackageInstaller() throws RemoteException;

    ParceledListSlice<PackageInfo> getInstalledPackages(int flags, int userId) throws RemoteException;

    void setComponentEnabledSetting(ComponentName componentName, int newState, int flags, int userId) throws RemoteException;

    int getComponentEnabledSetting(ComponentName componentName, int userId) throws RemoteException;

    void setApplicationEnabledSetting(String packageName, int newState, int flags, int userId) throws RemoteException;

    int getApplicationEnabledSetting(String packageName, int userId) throws RemoteException;

    abstract class Stub extends Binder implements IPackageManager {
        public static IPackageManager asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }
}
