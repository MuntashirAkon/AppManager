package android.app;

import android.os.Parcelable;

public class AppOpsManager {
    public static int strOpToOp(String op) {
        return 0;
    }

    public static String permissionToOp(String s) {
        return null;
    }

    public static int permissionToOpCode(String s) {
        return 0;
    }

    public static int strDebugOpToOp(String op) {
        throw new IllegalArgumentException("Unknown operation string: " + op);
    }

    public static class PackageOps implements Parcelable {
    }

    public static class OpEntry implements Parcelable {
    }
}
