// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.test.shadows;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

@Implements(PackageManagerCompat.class)
public class ShadowPackageManagerCompat {
    @Implementation
    @NonNull
    public static PackageInfo getPackageInfo(@NonNull String packageName, int flags, int userId)
            throws RemoteException, PackageManager.NameNotFoundException {
        return ContextUtils.getContext().getPackageManager().getPackageInfo(packageName, flags);
    }

    public static boolean clearApplicationUserData(@NonNull String packageName, int userId) {
        // App data may have been cleaned already depending on how it was handled in unit tests
        return true;
    }

    public static String getInstallerPackageName(@NonNull String packageName, int userId) {
        return BuildConfig.APPLICATION_ID;
    }
}
