// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.appops;

import android.os.RemoteException;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class AppOpsUtils {
    @NonNull
    public static List<OpEntry> getChangedAppOps(@NonNull AppOpsService service, @NonNull String packageName, int uid)
            throws RemoteException {
        List<PackageOps> packageOpsList = service.getOpsForPackage(uid, packageName, null);
        List<OpEntry> opEntries = new ArrayList<>();
        if (packageOpsList.size() == 1) {
            opEntries.addAll(packageOpsList.get(0).getOps());
        }
        return opEntries;
    }
}
