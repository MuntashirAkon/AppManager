// SPDX-License-Identifier: GPL-3.0-or-later

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
