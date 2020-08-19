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

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/**
 * Created by zl on 2017/7/31.
 */

public interface IPackageManager {
    PackageInfo getPackageInfo(String packageName, int flags, int userId);

    PermissionInfo getPermissionInfo(String name, int flags);

    PermissionGroupInfo getPermissionGroupInfo(String name, int flags);

    void grantRuntimePermission(String packageName, String permissionName, int userId);

    void revokeRuntimePermission(String packageName, String permissionName, int userId);

    void resetRuntimePermissions();

    int getPermissionFlags(String permissionName, String packageName, int userId);

    String[] getAppOpPermissionPackages(String permissionName);

    ResolveInfo resolveIntent(Intent intent, String resolvedType, int flags, int userId);

    ApplicationInfo getApplicationInfo(String packageName, int flags, int userId);

    ParceledListSlice getInstalledPackages(int flags, int userId);

    int getPackageUid(String packageName, int flags, int userId);

    // for API 23 or lower
    int getPackageUid(String packageName, int userId);
}
