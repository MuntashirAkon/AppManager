/*
 * Copyright (C) 2020 Muntashir Al-Islam
 * Copyright (C) 2020 xz-dev
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

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.Context;
import android.content.pm.IPackageInstaller;
import android.content.pm.IPackageInstallerSession;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

import java.lang.reflect.InvocationTargetException;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;

@SuppressWarnings({"JavaReflectionMemberAccess", "ConstantConditions"})
public class PackageInstallerUtils {

    @NonNull
    public static PackageInstaller createPackageInstaller(@NonNull IPackageInstaller installer,
                                                          @NonNull String installerPackageName,
                                                          int userId)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        if (Build.VERSION.SDK_INT >= 26) {
            return PackageInstaller.class.getConstructor(IPackageInstaller.class, String.class, int.class)
                    .newInstance(installer, installerPackageName, userId);
        } else {
            return PackageInstaller.class.getConstructor(Context.class, PackageManager.class, IPackageInstaller.class, String.class, int.class)
                    .newInstance(AppManager.getContext(), AppManager.getContext().getPackageManager(), installer, installerPackageName, userId);
        }
    }

    @NonNull
    public static PackageInstaller.Session createSession(@NonNull IPackageInstallerSession session)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        return PackageInstaller.Session.class.getConstructor(IPackageInstallerSession.class)
                .newInstance(session);
    }

    public static int getInstallFlags(@NonNull PackageInstaller.SessionParams sessionParams)
            throws NoSuchFieldException, IllegalAccessException {
        return (int) PackageInstaller.SessionParams.class.getDeclaredField("installFlags").get(sessionParams);
    }

    public static void setInstallFlags(@NonNull PackageInstaller.SessionParams params,
                                       @PackageInstallerCompat.InstallFlags int newValue)
            throws NoSuchFieldException, IllegalAccessException {
        PackageInstaller.SessionParams.class.getDeclaredField("installFlags").set(params, newValue);
    }
}
