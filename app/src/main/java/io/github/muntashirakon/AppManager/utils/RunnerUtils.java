package io.github.muntashirakon.AppManager.utils;

import io.github.muntashirakon.AppManager.runner.Runner;

public final class RunnerUtils {
    public static final String CMD_CLEAR_PACKAGE_DATA = "pm clear %s";
    public static final String CMD_ENABLE_PACKAGE  = "pm enable %s";
    public static final String CMD_DISABLE_PACKAGE = "pm disable-user %s";
    public static final String CMD_FORCE_STOP_PACKAGE  = "am force-stop %s";
    public static final String CMD_UNINSTALL_PACKAGE = "pm uninstall --user 0 %s";
    public static final String CMD_REINSTALL_PACKAGE = "pm install-existing %s";

    public static final String CMD_COMPONENT_ENABLE = "pm default-state %s/%s";  // default-state is more safe than enable
    public static final String CMD_COMPONENT_DISABLE = "pm disable %s/%s";

    public static final String CMD_PERMISSION_GRANT = "pm grant %s %s";
    public static final String CMD_PERMISSION_REVOKE = "pm revoke %s %s";

    public static final String CMD_APP_OPS_GET = "appops get %s %d";
    public static final String CMD_APP_OPS_GET_ALL = "appops get %s";
    public static final String CMD_APP_OPS_RESET = "appops reset %s";
    public static final String CMD_APP_OPS_RESET_USER = "appops reset --user %d %s";
    public static final String CMD_APP_OPS_SET = "appops set %s %d %s";
    public static final String CMD_APP_OPS_SET_MODE_INT = "appops set %s %d %d";
    public static final String CMD_APP_OPS_SET_UID = "appops set --uid %d %d %s";

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

    public static Runner.Result uninstallPackage(String packageName) {
        return Runner.runCommand(String.format(CMD_UNINSTALL_PACKAGE, packageName));
    }

    public static Runner.Result reinstallPackage(String packageName) {
        return Runner.runCommand(String.format(CMD_REINSTALL_PACKAGE, packageName));
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
