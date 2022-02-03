// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.magisk;

import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ServiceInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        Runner.Result result = Runner.runCommand(new String[]{"magiskhide", "ls"});
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

    @NonNull
    static Collection<MagiskProcess> getProcesses(@NonNull PackageInfo packageInfo,
                                                  @NonNull Collection<String> enabledProcesses) {
        String packageName = packageInfo.packageName;
        Map<String, MagiskProcess> processNameProcessMap = new HashMap<>();
        {
            // Add default process
            MagiskProcess mp = new MagiskProcess(packageName);
            mp.setEnabled(enabledProcesses.contains(packageName));
            processNameProcessMap.put(packageName, mp);
        }
        // Add other processes: order must be preserved
        if (packageInfo.services != null) {
            for (ServiceInfo info : packageInfo.services) {
                if (!packageName.equals(info.processName) && processNameProcessMap.get(info.processName) == null) {
                    MagiskProcess mp = new MagiskProcess(packageName, info.processName);
                    mp.setEnabled(enabledProcesses.contains(info.processName));
                    mp.setIsolatedProcess((info.flags & ServiceInfo.FLAG_ISOLATED_PROCESS) != 0);
                    processNameProcessMap.put(info.processName, mp);
                }
            }
        }
        if (packageInfo.activities != null) {
            for (ComponentInfo info : packageInfo.activities) {
                if (!packageName.equals(info.processName) && processNameProcessMap.get(info.processName) == null) {
                    MagiskProcess mp = new MagiskProcess(packageName, info.processName);
                    mp.setEnabled(enabledProcesses.contains(info.processName));
                    processNameProcessMap.put(info.processName, mp);
                }
            }
        }
        if (packageInfo.providers != null) {
            for (ComponentInfo info : packageInfo.providers) {
                if (!packageName.equals(info.processName) && processNameProcessMap.get(info.processName) == null) {
                    MagiskProcess mp = new MagiskProcess(packageName, info.processName);
                    mp.setEnabled(enabledProcesses.contains(info.processName));
                    processNameProcessMap.put(info.processName, mp);
                }
            }
        }
        if (packageInfo.receivers != null) {
            for (ComponentInfo info : packageInfo.receivers) {
                if (!packageName.equals(info.processName) && processNameProcessMap.get(info.processName) == null) {
                    MagiskProcess mp = new MagiskProcess(packageName, info.processName);
                    mp.setEnabled(enabledProcesses.contains(info.processName));
                    processNameProcessMap.put(info.processName, mp);
                }
            }
        }
        return processNameProcessMap.values();
    }

    @NonNull
    static Collection<String> parseProcesses(@NonNull String packageName, @NonNull Runner.Result result) {
        if (!result.isSuccessful()) {
            // No matches
            return Collections.emptyList();
        }
        Set<String> processes = new HashSet<>();
        for (String line : result.getOutputAsList()) {
            String[] splits = line.split("\\|", 2);
            if (splits.length == 1) {
                // Old style outputs
                if (splits[0].equals(packageName)) {
                    processes.add(packageName);
                } // else mismatch due to greedy algorithm
            } else if (splits.length == 2) {
                // New-style output
                if (splits[0].equals(packageName)) {
                    processes.add(splits[1]);
                } // else mismatch due to greedy algorithm
            } // else unknown match
        }
        return processes;
    }
}
