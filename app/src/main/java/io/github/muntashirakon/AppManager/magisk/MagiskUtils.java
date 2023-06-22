// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.magisk;

import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class MagiskUtils {
    // FIXME(20/9/20): This isn't always true, see check_data in util_functions.sh
    public static final String NVBASE = "/data/adb";
    private static boolean sBootMode = false;

    public static final String ISOLATED_MAGIC = "isolated";

    private static final String[] SCAN_PATHS = new String[]{
            "/system/app", "/system/priv-app", "/system/preload",
            "/system/product/app", "/system/product/priv-app", "/system/product/overlay",
            "/system/vendor/app", "/system/vendor/overlay",
            "/system/system_ext/app", "/system/system_ext/priv-app",
            "/system_ext/app", "/system_ext/priv-app",

            "/vendor/app", "/vendor/overlay",

            "/product/app", "/product/priv-app", "/product/overlay",
    };

    @NonNull
    public static Path getModDir() {
        return Paths.get(NVBASE + "/modules" + (sBootMode ? "_update" : ""));
    }

    public static void setBootMode(boolean bootMode) {
        MagiskUtils.sBootMode = bootMode;
    }

    private static List<String> sSystemlessPaths;

    @NonNull
    private static List<String> getSystemlessPaths() {
        if (sSystemlessPaths == null) {
            sSystemlessPaths = new ArrayList<>();
            Path modDir = getModDir();
            if (!modDir.canRead()) {
                // No permission or no-magisk
                return Collections.emptyList();
            }
            // Get module paths
            Path[] modulePaths = modDir.listFiles(Path::isDirectory);
            // Scan module paths
            for (Path file : modulePaths) {
                // Get system apk files
                for (String sysPath : SCAN_PATHS) {
                    // Always NonNull since it's a Linux FS
                    Path[] paths = Objects.requireNonNull(Paths.build(file, sysPath)).listFiles(Path::isDirectory);
                    for (Path path : paths) {
                        if (hasApkFile(path)) {
                            sSystemlessPaths.add(sysPath + "/" + path.getName());
                        }
                    }
                }
            }
        }
        return sSystemlessPaths;
    }

    public static boolean isSystemlessPath(@NonNull String path) {
        return getSystemlessPaths().contains(path);
    }

    private static boolean hasApkFile(@NonNull Path file) {
        if (file.isDirectory()) {
            Path[] files = file.listFiles((dir, name) -> name.endsWith(".apk"));
            return files.length > 0;
        }
        return false;
    }

    @NonNull
    static List<MagiskProcess> getProcesses(@NonNull PackageInfo packageInfo,
                                                  @NonNull Collection<String> enabledProcesses) {
        String packageName = packageInfo.packageName;
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
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
                if ((info.flags & ServiceInfo.FLAG_ISOLATED_PROCESS) != 0) {
                    // Isolated process
                    if ((info.flags & ServiceInfo.FLAG_USE_APP_ZYGOTE) != 0) {
                        // Uses app zygote
                        String processName = (applicationInfo.processName == null ? applicationInfo.packageName : applicationInfo.processName) + "_zygote";
                        if (processNameProcessMap.get(processName) == null) {
                            MagiskProcess mp = new MagiskProcess(packageName, processName);
                            mp.setEnabled(enabledProcesses.contains(processName));
                            mp.setIsolatedProcess(true);
                            mp.setAppZygote(true);
                            processNameProcessMap.put(processName, mp);
                        }
                    } else {
                        String processName = getProcessName(applicationInfo, info)
                                + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? (":" + packageName) : "");
                        if (processNameProcessMap.get(processName) == null) {
                            MagiskProcess mp = new MagiskProcess(packageName, processName);
                            mp.setEnabled(enabledProcesses.contains(processName));
                            mp.setIsolatedProcess(true);
                            processNameProcessMap.put(processName, mp);
                        }
                    }
                } else {
                    String processName = getProcessName(applicationInfo, info);
                    if (processNameProcessMap.get(processName) == null) {
                        MagiskProcess mp = new MagiskProcess(packageName, processName);
                        mp.setEnabled(enabledProcesses.contains(processName));
                        processNameProcessMap.put(processName, mp);
                    }
                }
            }
        }
        if (packageInfo.activities != null) {
            for (ComponentInfo info : packageInfo.activities) {
                String processName = getProcessName(applicationInfo, info);
                if (processNameProcessMap.get(processName) == null) {
                    MagiskProcess mp = new MagiskProcess(packageName, processName);
                    mp.setEnabled(enabledProcesses.contains(processName));
                    processNameProcessMap.put(processName, mp);
                }
            }
        }
        if (packageInfo.providers != null) {
            for (ComponentInfo info : packageInfo.providers) {
                String processName = getProcessName(applicationInfo, info);
                if (processNameProcessMap.get(processName) == null) {
                    MagiskProcess mp = new MagiskProcess(packageName, processName);
                    mp.setEnabled(enabledProcesses.contains(processName));
                    processNameProcessMap.put(processName, mp);
                }
            }
        }
        if (packageInfo.receivers != null) {
            for (ComponentInfo info : packageInfo.receivers) {
                String processName = getProcessName(applicationInfo, info);
                if (processNameProcessMap.get(processName) == null) {
                    MagiskProcess mp = new MagiskProcess(packageName, processName);
                    mp.setEnabled(enabledProcesses.contains(processName));
                    processNameProcessMap.put(processName, mp);
                }
            }
        }
        List<MagiskProcess> magiskProcesses = new ArrayList<>(processNameProcessMap.values());
        Collections.sort(magiskProcesses, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        return magiskProcesses;
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
                // New style output
                if (splits[0].equals(packageName) || splits[0].equals(ISOLATED_MAGIC)) {
                    processes.add(splits[1]);
                } // else mismatch due to greedy algorithm
            } // else unknown match
        }
        return processes;
    }

    @NonNull
    private static String getProcessName(@NonNull ApplicationInfo applicationInfo, @NonNull ComponentInfo info) {
        // Priority: component process name > application process name > package name
        return info.processName != null ? info.processName : (applicationInfo.processName != null
                ? applicationInfo.processName : applicationInfo.packageName);
    }
}
