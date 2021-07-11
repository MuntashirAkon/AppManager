// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.appops;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Parcelable;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.app.IAppOpsService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.appops.reflector.ReflectUtils;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.servermanager.PermissionCompat;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;

@SuppressLint("DefaultLocale")
public class AppOpsService {
    private final IAppOpsService appOpsService;

    public AppOpsService() {
        Context context = AppManager.getContext();
        if (!PermissionUtils.hasAppOpsPermission(context) && AppPref.isRootOrAdbEnabled()) {
            try {
                PermissionCompat.grantPermission(
                        context.getPackageName(),
                        PermissionUtils.PERMISSION_GET_APP_OPS_STATS,
                        Users.myUserId());
            } catch (RemoteException e) {
                throw new RuntimeException("Couldn't connect to appOpsService locally", e);
            }
        }
        // Local/remote services are handled automatically
        this.appOpsService = IAppOpsService.Stub.asInterface(ProxyBinder.getService(Context.APP_OPS_SERVICE));
    }

    /**
     * Get the mode of operation of the given package or uid.
     *
     * @param op          One of the OP_*
     * @param uid         User ID for the package(s)
     * @param packageName Name of the package
     * @return One of the MODE_*
     */
    public int checkOperation(int op, int uid, String packageName) throws RemoteException {
        return appOpsService.checkOperation(op, uid, packageName);
    }

    public List<PackageOps> getOpsForPackage(int uid, String packageName, @Nullable int[] ops)
            throws RemoteException {
        // Check using uid mode and package mode, override ops in package mode from uid mode
        List<OpEntry> opEntries = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                addAllRelevantOpEntriesWithNoOverride(opEntries, appOpsService.getUidOps(uid, ops));
            } catch (NullPointerException e) {
                Log.e("AppOpsService", "Could not get app ops for UID " + uid, e);
            }
        }
        addAllRelevantOpEntriesWithNoOverride(opEntries, appOpsService.getOpsForPackage(uid, packageName, ops));
        return Collections.singletonList(new PackageOps(packageName, uid, opEntries));
    }

    @NonNull
    public List<PackageOps> getPackagesForOps(int[] ops) throws RemoteException {
        List<Parcelable> opsForPackage = appOpsService.getPackagesForOps(ops);
        List<PackageOps> packageOpsList = new ArrayList<>();
        if (opsForPackage != null) {
            for (Object o : opsForPackage) {
                PackageOps packageOps = ReflectUtils.opsConvert(o);
                packageOpsList.add(packageOps);
            }
        }
        return packageOpsList;
    }

    public void setMode(int op, int uid, String packageName, int mode) throws RemoteException {
        if (AppOpsManager.isMiuiOp(op) || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Only package mode works in MIUI-only app ops and before Android M
            appOpsService.setMode(op, uid, packageName, mode);
        } else {
            // Set UID mode
            appOpsService.setUidMode(op, uid, mode);
        }
    }

    public void resetAllModes(int reqUserId, @NonNull String reqPackageName) throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            appOpsService.resetAllModes(reqUserId, reqPackageName);
        }
    }

    private void addAllRelevantOpEntriesWithNoOverride(final List<OpEntry> opEntries,
                                                       @Nullable final List<Parcelable> opsForPackage) {
        if (opsForPackage != null) {
            for (Parcelable o : opsForPackage) {
                PackageOps packageOps = ReflectUtils.opsConvert(o);
                for (OpEntry opEntry : packageOps.getOps()) {
                    if (!opEntries.contains(opEntry)) {
                        opEntries.add(opEntry);
                    }
                }
            }
        }
    }
}
