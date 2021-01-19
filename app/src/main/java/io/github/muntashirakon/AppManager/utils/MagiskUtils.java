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

package io.github.muntashirakon.AppManager.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.io.ProxyFile;

public class MagiskUtils {
    // FIXME(20/9/20): This isn't always true, see check_data in util_functions.sh
    public static final String NVBASE = "/data/adb";
    private static boolean bootMode = false;

    private static final String[] SCAN_PATHS = new String[]{
            "/system/app", "/system/priv-app", "/system/product/app", "/system/product/priv-app"
    };

    @NonNull
    public static String getModDir() {
        return NVBASE + "/modules" + (bootMode ? "_update" : "");
    }

    public static void setBootMode(boolean bootMode) {
        MagiskUtils.bootMode = bootMode;
    }

    private static List<String> systemlessPaths;

    @NonNull
    public static List<String> getSystemlessPaths() {
        if (systemlessPaths == null) {
            systemlessPaths = new ArrayList<>();
            // Get module paths
            ProxyFile[] modulePaths = getDirectories(new ProxyFile(getModDir()));
            if (modulePaths != null) {
                // Scan module paths
                ProxyFile[] paths;
                for (ProxyFile file : modulePaths) {
                    // Get system apk files
                    for (String sysPath : SCAN_PATHS) {
                        paths = getDirectories(new ProxyFile(file, sysPath));
                        if (paths != null) {
                            for (ProxyFile path : paths) {
                                if (hasApkFile(path)) {
                                    systemlessPaths.add(sysPath + "/" + path.getName());
                                }
                            }
                        }
                    }
                }
            }
        }
        return systemlessPaths;
    }

    public static boolean isSystemlessPath(String path) {
        getSystemlessPaths();
        return systemlessPaths.contains(path);
    }

    @NonNull
    public static Set<String> listHiddenPackages() {
        Runner.Result result = Runner.runCommand(Runner.getRootInstance(), new String[]{"magiskhide", "ls"});
        Set<String> packages = new ArraySet<>();
        if (result.isSuccessful()) {
            for (String hideInfo : result.getOutputAsList()) {
                int pipeLoc = hideInfo.indexOf('|');
                if (pipeLoc == -1) {
                    packages.add(hideInfo);
                } else {
                    packages.add(hideInfo.substring(0, pipeLoc));
                }
            }
        }
        return packages;
    }

    public static boolean isHidden(String packageName) {
        return Runner.runCommand(Runner.getRootInstance(), "magiskhide ls | " +
                Runner.TOYBOX + " grep " + packageName).isSuccessful();
    }

    public static boolean hide(String packageName) {
        // Check MagiskHide status
        if (!isMagiskHideEnabled(true)) return false;
        // MagiskHide is enabled, enable hide for the package
        return Runner.runCommand(Runner.getRootInstance(), new String[]{"magiskhide", "add", packageName}).isSuccessful();
    }

    public static boolean unhide(String packageName) {
        // Disable hide for the package (don't need to check for status)
        return Runner.runCommand(Runner.getRootInstance(), new String[]{"magiskhide", "rm", packageName}).isSuccessful();
    }

    public static boolean isMagiskHideEnabled(boolean forceEnable) {
        // Check MagiskHide status
        if (!Runner.runCommand(Runner.getRootInstance(), new String[]{"magiskhide", "status"}).isSuccessful()) {
            // Enable MagiskHide first
            if (forceEnable) {
                return Runner.runCommand(Runner.getRootInstance(), new String[]{"magiskhide", "enable"}).isSuccessful();
            } else return false;
        } else return true;
    }

    @Nullable
    private static ProxyFile[] getDirectories(@NonNull ProxyFile file) {
        if (file.isDirectory()) {
            return file.listFiles(pathname -> new ProxyFile(pathname).isDirectory());
        }
        return null;
    }

    private static boolean hasApkFile(@NonNull ProxyFile file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles((dir, name) -> name.endsWith(".apk"));
            return files != null && files.length > 0;
        }
        return false;
    }
}
