package io.github.muntashirakon.AppManager.servermanager;

import android.content.Context;
import android.os.Bundle;
import android.os.Process;

import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.servermanager.remote.AppOpsHandler;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.server.common.ClassCaller;
import io.github.muntashirakon.AppManager.server.common.OpEntry;
import io.github.muntashirakon.AppManager.server.common.OpsCommands;
import io.github.muntashirakon.AppManager.server.common.OpsResult;
import io.github.muntashirakon.AppManager.server.common.PackageOps;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class AppOpsManager {
    private static final String TAG = "AppOpsManager";

    private Context mContext;
    private LocalServerManager mLocalServerManager;
    private int mUserHandleId;
    private int userId;
    private static String pkgName;
    private ApiSupporter apiSupporter;

    public AppOpsManager(Context context) {
        this(context, new Config());
    }

    public AppOpsManager(Context context, @NonNull Config config) {
        mContext = context;
        config.context = mContext;
        mUserHandleId = Process.myUid() / 100000; // android.os.UserHandle.myUserId()
        ServerConfig.init(context, mUserHandleId);
        userId = mUserHandleId;
        mLocalServerManager = LocalServerManager.getInstance(config);
        apiSupporter = new ApiSupporter(mLocalServerManager);
        pkgName = context.getPackageName();
        checkFile();
    }

    public void setUserHandleId(int uid) {
        this.userId = uid;
    }

    public void updateConfig(Config config) {
        mLocalServerManager.updateConfig(config);
    }

    public Config getConfig() {
        return mLocalServerManager.getConfig();
    }

    private void checkFile() {
        AssetsUtils.copyFile(mContext, ServerConfig.JAR_NAME, ServerConfig.getDestJarFile(), BuildConfig.DEBUG);
        AssetsUtils.writeScript(getConfig());
    }

    private synchronized void checkConnect() throws Exception {
        mLocalServerManager.start();
    }

    public OpsResult getOpsForPackage(int uid, String packageName, int[] ops) throws Exception {
        checkConnect();
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
        ClassCaller classCaller = new ClassCaller(pkgName, AppOpsHandler.class.getName(), args);
        CallerResult result = mLocalServerManager.execNew(classCaller);
        Bundle replyBundle = result.getReplyBundle();
        return replyBundle.getParcelable("return");
    }

    public OpsResult getPackagesForOps(int[] ops, boolean reqNet) throws Exception {
        checkConnect();
        OpsCommands.Builder builder = new OpsCommands.Builder();
        builder.setAction(OpsCommands.ACTION_GET_FOR_OPS);
        builder.setOps(ops);
        builder.setReqNet(reqNet);
        builder.setUserHandleId(userId);
        return wrapOps(builder);
    }

    public OpsResult setOpsMode(String packageName, int op, int mode) throws Exception {
        checkConnect();
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

    public ApiSupporter getApiSupporter() {
        return apiSupporter;
    }

    public void destory() {
        if (mLocalServerManager != null) {
            mLocalServerManager.stop();
        }
    }

    public boolean isRunning() {
        return mLocalServerManager != null && mLocalServerManager.isRunning();
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


    public void closeBgServer() {
        if (mLocalServerManager != null) {
            mLocalServerManager.closeBgServer();
            mLocalServerManager.stop();
        }

    }

    public static boolean isEnableSELinux() {
        return AssetsUtils.isEnableSELinux();
    }

    public static class Config {
        public boolean allowBgRunning = false;
        public String logFile;
        public boolean printLog = false;
        public boolean useAdb = AppPref.isAdbEnabled() && !AppPref.isRootEnabled();;
        public boolean rootOverAdb = false;
        public String adbHost = "127.0.0.1";
        public int adbPort = 5555;
        Context context;
    }
}
