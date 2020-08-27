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

package io.github.muntashirakon.AppManager.runner;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;

@SuppressWarnings("unused,UnusedReturnValue")
public final class RunnerUtils {
    public static final String CMD_PM = Build.VERSION.SDK_INT >= 28 ? "cmd package" : "pm";
    public static final String CMD_AM = Build.VERSION.SDK_INT >= 28 ? "cmd activity" : "am";
    public static final String CMD_APP_OPS = Build.VERSION.SDK_INT >= 28 ? "cmd appops" : "appops";

    public static final String CMD_CLEAR_CACHE_PREFIX = "rm -rf";
    public static final String CMD_CLEAR_CACHE_DIR_SUFFIX = " %s/cache %s/code_cache";
    public static final String CMD_CLEAR_PACKAGE_DATA = CMD_PM + " clear %s";
    public static final String CMD_ENABLE_PACKAGE = CMD_PM + " enable %s";
    public static final String CMD_DISABLE_PACKAGE = CMD_PM + " disable-user %s";
    public static final String CMD_FORCE_STOP_PACKAGE = CMD_AM + " force-stop %s";
    public static final String CMD_UNINSTALL_PACKAGE = CMD_PM + " uninstall -k --user 0 %s";
    public static final String CMD_UNINSTALL_PACKAGE_WITH_DATA = CMD_PM + " uninstall --user 0 %s";
    public static final String CMD_INSTALL_PACKAGE = CMD_PM + " install --user 0 -r -i " + BuildConfig.APPLICATION_ID + " %s";

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

    @NonNull
    public static Runner.Result clearPackageCache(String packageName) {
        try {
            ApplicationInfo applicationInfo = AppManager.getContext().getPackageManager().getApplicationInfo(packageName, 0);
            StringBuilder command = new StringBuilder(CMD_CLEAR_CACHE_PREFIX);
            command.append(String.format(CMD_CLEAR_CACHE_DIR_SUFFIX, applicationInfo.dataDir, applicationInfo.dataDir));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !applicationInfo.dataDir.equals(applicationInfo.deviceProtectedDataDir)) {
                command.append(String.format(CMD_CLEAR_CACHE_DIR_SUFFIX, applicationInfo.deviceProtectedDataDir, applicationInfo.deviceProtectedDataDir));
            }
            File[] cacheDirs = AppManager.getInstance().getExternalCacheDirs();
            for (File cacheDir : cacheDirs) {
                if (cacheDir != null) {
                    String extCache = cacheDir.getAbsolutePath().replace(BuildConfig.APPLICATION_ID, packageName);
                    command.append(" ").append(extCache);
                }
            }
            return Runner.runCommand(command.toString());
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return new Runner.Result() {
                @Override
                public boolean isSuccessful() {
                    return false;
                }

                @Override
                public List<String> getOutputAsList() {
                    return null;
                }

                @Override
                public List<String> getOutputAsList(int first_index) {
                    return null;
                }

                @Override
                public List<String> getOutputAsList(int first_index, int length) {
                    return null;
                }

                @Override
                public String getOutput() {
                    return null;
                }
            };
        }
    }

    @NonNull
    public static Runner.Result clearPackageData(String packageName) {
        return Runner.runCommand(String.format(CMD_CLEAR_PACKAGE_DATA, packageName));
    }

    @NonNull
    public static Runner.Result enablePackage(String packageName) {
        return Runner.runCommand(String.format(CMD_ENABLE_PACKAGE, packageName));
    }

    @NonNull
    public static Runner.Result disablePackage(String packageName) {
        return Runner.runCommand(String.format(CMD_DISABLE_PACKAGE, packageName));
    }

    @NonNull
    public static Runner.Result forceStopPackage(String packageName) {
        return Runner.runCommand(String.format(CMD_FORCE_STOP_PACKAGE, packageName));
    }

    @NonNull
    public static Runner.Result uninstallPackageWithoutData(String packageName) {
        return Runner.runCommand(String.format(CMD_UNINSTALL_PACKAGE, packageName));
    }

    @NonNull
    public static Runner.Result uninstallPackageWithData(String packageName) {
        return Runner.runCommand(String.format(CMD_UNINSTALL_PACKAGE_WITH_DATA, packageName));
    }

    @NonNull
    public static Runner.Result installPackage(String packageLocation) {
        return Runner.runCommand(String.format(CMD_INSTALL_PACKAGE, packageLocation));
    }

    @NonNull
    public static Runner.Result disableComponent(String packageName, String componentName) {
        return Runner.runCommand(String.format(CMD_COMPONENT_DISABLE, packageName, componentName));
    }

    @NonNull
    public static Runner.Result enableComponent(String packageName, String componentName) {
        return Runner.runCommand(String.format(CMD_COMPONENT_ENABLE, packageName, componentName));
    }

    @NonNull
    public static Runner.Result grantPermission(String packageName, String permissionName) {
        return Runner.runCommand(String.format(CMD_PERMISSION_GRANT, packageName, permissionName));
    }

    @NonNull
    public static Runner.Result revokePermission(String packageName, String permissionName) {
        return Runner.runCommand(String.format(CMD_PERMISSION_REVOKE, packageName, permissionName));
    }

    public static boolean fileExists(String fileName) {
        return Runner.runCommand(String.format("test -e \"%s\"", fileName)).isSuccessful();
    }
}
