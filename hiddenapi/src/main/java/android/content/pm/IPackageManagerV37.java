// SPDX-License-Identifier: GPL-3.0-or-later

package android.content.pm;

import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.VersionCodes;

@RequiresApi(VersionCodes.CINNAMON_BUN)
@RefineAs(IPackageManager.class)
public interface IPackageManagerV37 {
    PackageInfoList getInstalledPackages(long flags, int userId) throws RemoteException;
}
