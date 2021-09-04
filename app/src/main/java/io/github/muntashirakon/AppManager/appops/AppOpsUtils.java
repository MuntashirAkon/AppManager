// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.appops;

import android.app.AppOpsManagerHidden;
import android.os.Build;
import android.os.Parcelable;
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

    @NonNull
    public static PackageOps opsConvert(@NonNull AppOpsManagerHidden.PackageOps packageOps) {
        String packageName = packageOps.getPackageName();
        int uid = packageOps.getUid();
        List<OpEntry> entries = getOpEntries(packageOps.getOps());
        return new PackageOps(packageName, uid, entries);
    }

    @NonNull
    private static List<OpEntry> getOpEntries(@NonNull List<? extends Parcelable> entries) {
        List<OpEntry> opEntries = new ArrayList<>();
        for (Parcelable opEntry : entries) {
            OpEntryCompat opEntryCompat = new OpEntryCompat(opEntry);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                opEntries.add(new OpEntry(opEntryCompat.getOp(), opEntryCompat.getMode(),
                        opEntryCompat.getTime(), opEntryCompat.getRejectTime(), opEntryCompat.getDuration(),
                        opEntryCompat.getProxyUid(), opEntryCompat.getProxyPackageName()));
            } else {
                opEntries.add(new OpEntry(opEntryCompat.getOp(), opEntryCompat.getMode(),
                        opEntryCompat.getTime(), opEntryCompat.getRejectTime(), opEntryCompat.getDuration(),
                        0, null));
            }
        }
        return opEntries;
    }
}
