// SPDX-License-Identifier: GPL-3.0-or-later

package android.content.pm;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

@RefineAs(PackageInstaller.class)
public class PackageInstallerHidden {
    @RequiresApi(Build.VERSION_CODES.S)
    public PackageInstallerHidden(IPackageInstaller installer,
                                  String installerPackageName,
                                  String installerAttributionTag,
                                  int userId) {
        HiddenUtil.throwUOE(installer, installerPackageName, installerAttributionTag, userId);
    }

    /**
     * @deprecated Removed in Android 12 (API 31)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.O)
    public PackageInstallerHidden(IPackageInstaller installer,
                                  String installerPackageName,
                                  int userId) {
        HiddenUtil.throwUOE(installer, installerPackageName, userId);
    }

    /**
     * @deprecated Removed in Android 8 (API 26)
     */
    @Deprecated
    public PackageInstallerHidden(Context context,
                                  PackageManager packageManager,
                                  IPackageInstaller installer,
                                  String installerPackageName,
                                  int userId) {
        HiddenUtil.throwUOE(context, packageManager, installer, installerPackageName, userId);
    }

    public static class Session {
        public Session(IPackageInstallerSession session) {
            HiddenUtil.throwUOE(session);
        }
    }

    public static class SessionParams {
        public int installFlags;
        @RequiresApi(Build.VERSION_CODES.P)
        public String installerPackageName;
    }
}
