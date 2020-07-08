package io.github.muntashirakon.AppManager.appops;

import java.util.List;

interface IAppOpsService {
    String checkOperation(int op, int uid, String packageName) throws Exception;
    List<AppOpsManager.PackageOps> getOpsForPackage(int uid, String packageName, int[] ops) throws Exception;
    void setMode(int op, int uid, String packageName, int mode) throws Exception;
    void resetAllModes(int reqUserId, String reqPackageName) throws Exception;
}
