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

package io.github.muntashirakon.AppManager.servermanager.remote;

import android.Manifest;
import android.app.ActivityThread;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Process;
import android.os.ServiceManager;
import android.util.Log;

import com.android.internal.app.IAppOpsService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.server.common.ClassCallerProcessor;
import io.github.muntashirakon.AppManager.server.common.FLog;
import io.github.muntashirakon.AppManager.server.common.OpEntry;
import io.github.muntashirakon.AppManager.server.common.OpsCommands;
import io.github.muntashirakon.AppManager.server.common.OpsResult;
import io.github.muntashirakon.AppManager.server.common.OtherOp;
import io.github.muntashirakon.AppManager.server.common.PackageOps;
import io.github.muntashirakon.AppManager.server.common.ReflectUtils;
import io.github.muntashirakon.AppManager.server.common.Shell;

public class AppOpsHandler extends ClassCallerProcessor {

    private static IpTablesController mIpTablesController = null;

    static {
        try {
            if (Process.myUid() == 0) {
                mIpTablesController = new IpTablesController(Shell.getRootShell());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public AppOpsHandler(Context mPackageContext, Context mSystemContext, byte[] bytes) {
        super(mPackageContext, mSystemContext, bytes);
    }

    @Override
    public Bundle proxyInvoke(Bundle args) throws Throwable {
        OpsResult result;
        try {
            OpsCommands.Builder builder = args.getParcelable("args");
            args.clear();
            FLog.log(" appops " + builder);
            result = handleCommand(builder);
            if (result == null) {
                result = new OpsResult(null, null);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            result = new OpsResult(null, throwable);
        }
        args.putParcelable("return", result);
        return args;
    }

    private OpsResult handleCommand(@NonNull OpsCommands.Builder builder) throws Throwable {
        String s = builder.getAction();
        OpsResult result = null;
        if (OpsCommands.ACTION_GET.equals(s)) {
            result = runGet(builder);
        } else if (OpsCommands.ACTION_SET.equals(s)) {
            runSet(builder);
        } else if (OpsCommands.ACTION_RESET.equals(s)) {
            runReset(builder);
        } else if (OpsCommands.ACTION_GET_FOR_OPS.equals(s)) {
            result = runGetForOps(builder);
        }
        return result;
    }

    @NonNull
    private OpsResult runGet(@NonNull OpsCommands.Builder getBuilder) throws Throwable {
        final IAppOpsService appOpsService = IAppOpsService.Stub.asInterface(
                ServiceManager.getService(Context.APP_OPS_SERVICE));
        String packageName = getBuilder.getPackageName();
        int uid = Helper.getPackageUid(packageName, getBuilder.getUserHandleId());
        List<Parcelable> opsForPackage = appOpsService.getOpsForPackage(uid, packageName, null);
        ArrayList<PackageOps> packageOpses = new ArrayList<>();
        if (opsForPackage != null) {
            for (Object o : opsForPackage) {
                PackageOps packageOps = ReflectUtils.opsConvert(o);
                addSupport(appOpsService, packageOps, getBuilder.getUserHandleId());
                packageOpses.add(packageOps);
            }
        } else {
            PackageOps packageOps = new PackageOps(packageName, uid, new ArrayList<>());
            addSupport(appOpsService, packageOps, getBuilder.getUserHandleId());
            packageOpses.add(packageOps);
        }

        return new OpsResult(packageOpses, null);

    }


    private void addSupport(IAppOpsService appOpsService, PackageOps ops, int userHandleId) {
        addSupport(appOpsService, ops, userHandleId, true);
    }

    private void addSupport(IAppOpsService appOpsService, PackageOps ops, int userHandleId, boolean checkNet) {
        try {
            if (checkNet && mIpTablesController != null) {
                int mode = mIpTablesController.isMobileDataEnable(ops.getUid()) ? AppOpsManager.MODE_ALLOWED
                        : AppOpsManager.MODE_IGNORED;
                OpEntry opEntry = new OpEntry(OtherOp.OP_ACCESS_PHONE_DATA, mode, 0, 0, 0, 0, null);
                ops.getOps().add(opEntry);

                mode = mIpTablesController.isWifiDataEnable(ops.getUid()) ? AppOpsManager.MODE_ALLOWED
                        : AppOpsManager.MODE_IGNORED;
                opEntry = new OpEntry(OtherOp.OP_ACCESS_WIFI_NETWORK, mode, 0, 0, 0, 0, null);
                ops.getOps().add(opEntry);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(Log.getStackTraceString(e));
        }
        try {
            PackageInfo packageInfo = ActivityThread.getPackageManager()
                    .getPackageInfo(ops.getPackageName(), PackageManager.GET_PERMISSIONS, userHandleId);
            if (packageInfo != null && packageInfo.requestedPermissions != null) {
                for (String permission : packageInfo.requestedPermissions) {
                    int code = Helper.permissionToCode(permission);

                    if (code <= 0) {
                        //correct OP_WIFI_SCAN code.
                        if (Manifest.permission.ACCESS_WIFI_STATE.equals(permission)) {
                            code = OtherOp.getWifiScanOp();
                        }
                    }

                    if (code > 0 && !ops.hasOp(code)) {
                        int mode = appOpsService.checkOperation(code, ops.getUid(), ops.getPackageName());
                        if (mode != AppOpsManager.MODE_ERRORED) {
                            //
                            ops.getOps().add(new OpEntry(code, mode, 0, 0, 0, 0, null));
                        }
                    }
                }
            }

        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void runSet(@NonNull OpsCommands.Builder builder) throws Throwable {

        final int uid = Helper.getPackageUid(builder.getPackageName(), builder.getUserHandleId());
        if (OtherOp.isOtherOp(builder.getOpInt())) {
            setOther(builder, uid);
        } else {
            final IAppOpsService appOpsService = IAppOpsService.Stub.asInterface(
                    ServiceManager.getService(Context.APP_OPS_SERVICE));
            appOpsService.setMode(builder.getOpInt(), uid, builder.getPackageName(), builder.getModeInt());
        }


    }

    private void setOther(OpsCommands.Builder builder, int uid) {
        if (mIpTablesController != null) {
            boolean enable = builder.getModeInt() == AppOpsManager.MODE_ALLOWED;
            switch (builder.getOpInt()) {
                case OtherOp.OP_ACCESS_PHONE_DATA:
                    mIpTablesController.setMobileData(uid, enable);
                    break;
                case OtherOp.OP_ACCESS_WIFI_NETWORK:
                    mIpTablesController.setWifiData(uid, enable);
                    break;
            }
        }
    }

    private void runReset(@NonNull OpsCommands.Builder builder) throws Throwable {
        final IAppOpsService appOpsService = IAppOpsService.Stub.asInterface(
                ServiceManager.getService(Context.APP_OPS_SERVICE));

        appOpsService.resetAllModes(builder.getUserHandleId(), builder.getPackageName());

    }

    @NonNull
    private OpsResult runGetForOps(@NonNull OpsCommands.Builder builder) throws Throwable {

        final IAppOpsService appOpsService = IAppOpsService.Stub.asInterface(
                ServiceManager.getService(Context.APP_OPS_SERVICE));

        List opsForPackage = appOpsService.getPackagesForOps(builder.getOps());
        ArrayList<PackageOps> packageOpses = new ArrayList<>();

        if (opsForPackage != null) {
            for (Object o : opsForPackage) {
                PackageOps packageOps = ReflectUtils.opsConvert(o);
                addSupport(appOpsService, packageOps, builder.getUserHandleId(), builder.isReqNet());
                packageOpses.add(packageOps);
            }

        }

        return new OpsResult(packageOpses, null);
    }


}
