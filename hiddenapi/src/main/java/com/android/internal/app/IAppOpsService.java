package com.android.internal.app;

import android.os.Parcelable;

import java.util.List;

public interface IAppOpsService {
    int checkOperation(int code, int uid, String packageName);

    int noteOperation(int code, int uid, String packageName);

    int permissionToOpCode(String permission);

    // Remaining methods are only used in Java.
    int checkPackage(int uid, String packageName);

    List<Parcelable> getPackagesForOps(int[] ops);

    List<Parcelable> getOpsForPackage(int uid, String packageName, int[] ops);

    void setUidMode(int code, int uid, int mode);

    void setMode(int code, int uid, String packageName, int mode);

    void resetAllModes(int reqUserId, String reqPackageName);

    abstract class Stub {
        public static IAppOpsService asInterface(android.os.IBinder obj) {
            return null;
        }
    }
}
