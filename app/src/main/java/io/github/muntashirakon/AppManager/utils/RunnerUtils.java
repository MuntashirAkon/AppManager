package io.github.muntashirakon.AppManager.utils;

import android.os.Build;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.runner.Runner;

public final class RunnerUtils {
    public static final String CMD_PM = Build.VERSION.SDK_INT >= 28 ? "cmd package" : "pm";
    public static final String CMD_AM = Build.VERSION.SDK_INT >= 28 ? "cmd activity" : "am";
    public static final String CMD_APP_OPS = Build.VERSION.SDK_INT >= 28 ? "cmd appops" : "appops";

    public static final String CMD_CLEAR_PACKAGE_DATA = CMD_PM + " clear %s";
    public static final String CMD_ENABLE_PACKAGE  = CMD_PM + " enable %s";
    public static final String CMD_DISABLE_PACKAGE = CMD_PM + " disable-user %s";
    public static final String CMD_FORCE_STOP_PACKAGE  = CMD_AM + " force-stop %s";
    public static final String CMD_UNINSTALL_PACKAGE = CMD_PM + " uninstall -k --user 0 %s";
    public static final String CMD_UNINSTALL_PACKAGE_WITH_DATA = CMD_PM + " uninstall --user 0 %s";
    public static final String CMD_INSTALL_PACKAGE = CMD_PM + " install -r -i " + BuildConfig.APPLICATION_ID + " %s";

    public static final String CMD_COMPONENT_ENABLE = CMD_PM + " default-state %s/%s";  // default-state is more safe than enable
    public static final String CMD_COMPONENT_DISABLE = CMD_PM + " disable %s/%s";

    public static final String CMD_PERMISSION_GRANT = CMD_PM + " grant %s %s";
    public static final String CMD_PERMISSION_REVOKE = CMD_PM + " revoke %s %s";

    public static final String CMD_APP_OPS_GET = CMD_APP_OPS + " get %s %d";
    public static final String CMD_APP_OPS_GET_ALL = CMD_APP_OPS + " get %s";
    public static final String CMD_APP_OPS_RESET = CMD_APP_OPS + " reset %s";
    public static final String CMD_APP_OPS_RESET_USER = CMD_APP_OPS + " reset --user %d %s";
    public static final String CMD_APP_OPS_SET = CMD_APP_OPS + " set %s %d %s";
    public static final String CMD_APP_OPS_SET_MODE_INT = CMD_APP_OPS + " set %s %d %d";
    public static final String CMD_APP_OPS_SET_UID = CMD_APP_OPS + " set --uid %d %d %s";

    public static final String CMD_PID_PACKAGE = "pidof %s";
    public static final String CMD_KILL_SIG9 = "kill -9 %s";

    public static Runner.Result clearPackageData(String packageName) {
        return Runner.runCommand(String.format(CMD_CLEAR_PACKAGE_DATA, packageName));
    }

    public static Runner.Result enablePackage(String packageName) {
        return Runner.runCommand(String.format(CMD_ENABLE_PACKAGE, packageName));
    }

    public static Runner.Result disablePackage(String packageName) {
        return Runner.runCommand(String.format(CMD_DISABLE_PACKAGE, packageName));
    }

    public static Runner.Result forceStopPackage(String packageName) {
        return Runner.runCommand(String.format(CMD_FORCE_STOP_PACKAGE, packageName));
    }

    public static Runner.Result uninstallPackageWithoutData(String packageName) {
        return Runner.runCommand(String.format(CMD_UNINSTALL_PACKAGE, packageName));
    }

    public static Runner.Result uninstallPackageWithData(String packageName) {
        return Runner.runCommand(String.format(CMD_UNINSTALL_PACKAGE_WITH_DATA, packageName));
    }

    public static Runner.Result installPackage(String packageLocation) {
        return Runner.runCommand(String.format(CMD_INSTALL_PACKAGE, packageLocation));
    }

    public static Runner.Result disableComponent(String packageName, String componentName) {
        return Runner.runCommand(String.format(CMD_COMPONENT_DISABLE, packageName, componentName));
    }

    public static Runner.Result enableComponent(String packageName, String componentName) {
        return Runner.runCommand(String.format(CMD_COMPONENT_ENABLE, packageName, componentName));
    }

    public static Runner.Result grantPermission(String packageName, String permissionName) {
        return Runner.runCommand(String.format(CMD_PERMISSION_GRANT, packageName, permissionName));
    }

    public static Runner.Result revokePermission(String packageName, String permissionName) {
        return Runner.runCommand(String.format(CMD_PERMISSION_REVOKE, packageName, permissionName));
    }
}
