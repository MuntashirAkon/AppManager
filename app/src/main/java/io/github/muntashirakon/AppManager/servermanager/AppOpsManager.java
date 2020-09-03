package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.SuppressLint;
import android.os.Bundle;

import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.ClassCaller;
import io.github.muntashirakon.AppManager.server.common.OpEntry;
import io.github.muntashirakon.AppManager.server.common.OpsCommands;
import io.github.muntashirakon.AppManager.server.common.OpsResult;
import io.github.muntashirakon.AppManager.server.common.PackageOps;
import io.github.muntashirakon.AppManager.server.common.Shell;
import io.github.muntashirakon.AppManager.servermanager.remote.AppOpsHandler;
import io.github.muntashirakon.AppManager.servermanager.remote.ShellCommandHandler;

public class AppOpsManager {
    private LocalServer mLocalServer;
    private int userId;
    private static String packageName;

    @SuppressLint("StaticFieldLeak")
    private static AppOpsManager INSTANCE;

    public static AppOpsManager getInstance(@NonNull LocalServer localServer) {
        if (INSTANCE == null) {
            INSTANCE = new AppOpsManager(localServer);
        }
        return INSTANCE;
    }

    public AppOpsManager(@NonNull LocalServer localServer) {
        mLocalServer = localServer;
        userId = Users.getCurrentUser();
        packageName = localServer.getContext().getPackageName();
    }

    public Shell.Result runCommand(String command) throws Exception {
        Bundle args = new Bundle();
        args.putString("command", command);
        ClassCaller classCaller = new ClassCaller(packageName, ShellCommandHandler.class.getName(), args);
        CallerResult result = mLocalServer.exec(classCaller);
        Bundle replyBundle = result.getReplyBundle();
        return replyBundle.getParcelable("return");
    }

    public OpsResult getOpsForPackage(int uid, String packageName, int[] ops) throws Exception {
        OpsCommands.Builder builder = new OpsCommands.Builder();
        builder.setAction(OpsCommands.ACTION_GET);
        builder.setPackageName(packageName);
        builder.setOps(ops);
        builder.setUserHandleId(userId);
        return wrapOps(builder);
    }

    private OpsResult wrapOps(OpsCommands.Builder builder) throws Exception {
        Bundle args = new Bundle();
        args.putParcelable("args", builder);
        ClassCaller classCaller = new ClassCaller(packageName, AppOpsHandler.class.getName(), args);
        CallerResult result = mLocalServer.exec(classCaller);
        Bundle replyBundle = result.getReplyBundle();
        return replyBundle.getParcelable("return");
    }

    public OpsResult getPackagesForOps(int[] ops, boolean reqNet) throws Exception {
        OpsCommands.Builder builder = new OpsCommands.Builder();
        builder.setAction(OpsCommands.ACTION_GET_FOR_OPS);
        builder.setOps(ops);
        builder.setReqNet(reqNet);
        builder.setUserHandleId(userId);
        return wrapOps(builder);
    }

    public OpsResult setOpsMode(String packageName, int op, int mode) throws Exception {
        OpsCommands.Builder builder = new OpsCommands.Builder();
        builder.setAction(OpsCommands.ACTION_SET);
        builder.setPackageName(packageName);
        builder.setOpInt(op);
        builder.setModeInt(mode);
        builder.setUserHandleId(userId);
        return wrapOps(builder);
    }

    public OpsResult resetAllModes(String packageName) throws Exception {
        OpsCommands.Builder builder = new OpsCommands.Builder();
        builder.setAction(OpsCommands.ACTION_RESET);
        builder.setPackageName(packageName);
        builder.setUserHandleId(userId);
        return wrapOps(builder);
    }

    public OpsResult checkOperation(int op, String packageName) throws Exception {
        OpsCommands.Builder builder = new OpsCommands.Builder();
        builder.setAction(OpsCommands.ACTION_CHECK);
        builder.setPackageName(packageName);
        builder.setOpInt(op);
        builder.setUserHandleId(userId);
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
