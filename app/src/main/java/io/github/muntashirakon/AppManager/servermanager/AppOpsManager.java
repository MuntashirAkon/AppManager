package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.SuppressLint;
import android.os.Bundle;

import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.ClassCaller;
import io.github.muntashirakon.AppManager.server.common.OpEntry;
import io.github.muntashirakon.AppManager.server.common.OpsCommands;
import io.github.muntashirakon.AppManager.server.common.OpsResult;
import io.github.muntashirakon.AppManager.server.common.PackageOps;
import io.github.muntashirakon.AppManager.servermanager.remote.AppOpsHandler;

public class AppOpsManager {
    private LocalServer localServer;
    private int userHandle;
    private String packageName;

    @SuppressLint("StaticFieldLeak")
    private static AppOpsManager INSTANCE;

    public static AppOpsManager getInstance(@NonNull LocalServer localServer) {
        if (INSTANCE == null) {
            INSTANCE = new AppOpsManager(localServer);
        }
        return INSTANCE;
    }

    public AppOpsManager(@NonNull LocalServer localServer) {
        this.localServer = localServer;
        userHandle = Users.getCurrentUser();
        packageName = BuildConfig.APPLICATION_ID;
    }

    public OpsResult getOpsForPackage(int uid, String packageName, int[] ops) throws Exception {
        OpsCommands.Builder builder = new OpsCommands.Builder();
        builder.setAction(OpsCommands.ACTION_GET);
        builder.setPackageName(packageName);
        builder.setOps(ops);
        builder.setUserHandleId(userHandle);
        return wrapOps(builder);
    }

    private OpsResult wrapOps(OpsCommands.Builder builder) throws Exception {
        Bundle args = new Bundle();
        args.putParcelable("args", builder);
        ClassCaller classCaller = new ClassCaller(packageName, AppOpsHandler.class.getName(), args);
        CallerResult result = localServer.exec(classCaller);
        Bundle replyBundle = result.getReplyBundle();
        return replyBundle.getParcelable("return");
    }

    public OpsResult getPackagesForOps(int[] ops, boolean reqNet) throws Exception {
        OpsCommands.Builder builder = new OpsCommands.Builder();
        builder.setAction(OpsCommands.ACTION_GET_FOR_OPS);
        builder.setOps(ops);
        builder.setReqNet(reqNet);
        builder.setUserHandleId(userHandle);
        return wrapOps(builder);
    }

    public OpsResult setOpsMode(String packageName, int op, int mode) throws Exception {
        OpsCommands.Builder builder = new OpsCommands.Builder();
        builder.setAction(OpsCommands.ACTION_SET);
        builder.setPackageName(packageName);
        builder.setOpInt(op);
        builder.setModeInt(mode);
        builder.setUserHandleId(userHandle);
        return wrapOps(builder);
    }

    public OpsResult resetAllModes(String packageName) throws Exception {
        OpsCommands.Builder builder = new OpsCommands.Builder();
        builder.setAction(OpsCommands.ACTION_RESET);
        builder.setPackageName(packageName);
        builder.setUserHandleId(userHandle);
        return wrapOps(builder);
    }

    public OpsResult checkOperation(int op, String packageName) throws Exception {
        OpsCommands.Builder builder = new OpsCommands.Builder();
        builder.setAction(OpsCommands.ACTION_CHECK);
        builder.setPackageName(packageName);
        builder.setOpInt(op);
        builder.setUserHandleId(userHandle);
        return wrapOps(builder);
    }

    public OpsResult disableAllPermission(final String packageName) throws Exception {
        OpsResult opsForPackage = getOpsForPackage(-1, packageName, null);
        if (opsForPackage != null) {
            if (opsForPackage.getException() == null) {
                List<PackageOps> list = opsForPackage.getList();
                if (list != null && !list.isEmpty()) {
                    for (PackageOps packageOps : list) {
                        List<OpEntry> ops = packageOps.getOps();
                        if (ops != null) {
                            for (OpEntry op : ops) {
                                if (op.getMode() != android.app.AppOpsManager.MODE_IGNORED) {
                                    setOpsMode(packageName, op.getOp(), android.app.AppOpsManager.MODE_IGNORED);
                                }
                            }
                        }
                    }
                }
            } else {
                throw new Exception(opsForPackage.getException());
            }
        }
        return opsForPackage;
    }

    public static boolean isEnableSELinux() {
        return AssetsUtils.isEnableSELinux();
    }
}
