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

package io.github.muntashirakon.AppManager.appops;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;

@SuppressLint("DefaultLocale")
public class AppOpsService {
    private final com.android.internal.app.IAppOpsService appOpsService;
    public AppOpsService() {
        Context context = AppManager.getContext();
        if (!PermissionUtils.hasAppOpsPermission(context)) {
            Runner.Result result = RunnerUtils.grantPermission(context.getPackageName(),
                    PermissionUtils.PERMISSION_GET_APP_OPS_STATS, Users.getCurrentUserHandle());
            if (!result.isSuccessful()) {
                throw new RuntimeException("Couldn't connect to appOpsService locally");
            }
        }
        // Local/remote services are handled automatically
        this.appOpsService = com.android.internal.app.IAppOpsService.Stub.asInterface(ProxyBinder.getService(Context.APP_OPS_SERVICE));
    }

    /**
     * Get the mode of operation of the given package or uid.
     *
     * @param op          One of the OP_*
     * @param uid         User ID for the package(s)
     * @param packageName Name of the package
     * @return One of the MODE_*
     */
    public int checkOperation(int op, int uid, String packageName) throws Exception {
        return appOpsService.checkOperation(op, uid, packageName);
    }

    public List<PackageOps> getOpsForPackage(int uid, String packageName, int[] ops)
            throws Exception {
        List<Parcelable> opsForPackage = appOpsService.getOpsForPackage(uid, packageName, ops);
        ArrayList<PackageOps> packageOpsList = new ArrayList<>();
        if (opsForPackage != null) {
            for (Parcelable o : opsForPackage) {
                PackageOps packageOps = ReflectUtils.opsConvert(o);
                packageOpsList.add(packageOps);
            }
        } else {
            packageOpsList.add(new PackageOps(packageName, uid, Collections.emptyList()));
        }
        return packageOpsList;
    }

    @NonNull
    public List<PackageOps> getPackagesForOps(int[] ops) {
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

    public void setMode(int op, int uid, String packageName, int mode) throws Exception {
        appOpsService.setMode(op, uid, packageName, mode);
    }

    public void resetAllModes(int reqUserId, @NonNull String reqPackageName) throws Exception {
        appOpsService.resetAllModes(reqUserId, reqPackageName);
    }
}
