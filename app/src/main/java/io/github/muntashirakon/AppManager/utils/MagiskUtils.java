// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

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
import java.util.Objects;
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

    public static class MagiskProcess {
        @NonNull
        public final String packageName;
        @NonNull
        public final String name;

        private boolean mIsolatedProcess;
        private boolean mIsRunning;
        private boolean mIsEnabled;

        public MagiskProcess(@NonNull String packageName, @NonNull String name) {
            this.packageName = packageName;
            this.name = name;
        }

        public MagiskProcess(@NonNull String packageName) {
            this.packageName = packageName;
            this.name = packageName;
        }

        public void setEnabled(boolean enabled) {
            mIsEnabled = enabled;
        }

        public boolean isEnabled() {
            return mIsEnabled;
        }

        public void setIsolatedProcess(boolean isolatedProcess) {
            mIsolatedProcess = isolatedProcess;
        }

        public void setRunning(boolean running) {
            mIsRunning = running;
        }

        public boolean isIsolatedProcess() {
            return mIsolatedProcess;
        }

        public boolean isRunning() {
            return mIsRunning;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MagiskProcess)) return false;
            MagiskProcess that = (MagiskProcess) o;
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }

    @NonNull
    public static Collection<MagiskProcess> getMagiskHiddenProcesses(@NonNull PackageInfo packageInfo) {
        String packageName = packageInfo.packageName;
        Collection<String> magiskHiddenProcesses = getMagiskHiddenProcesses(packageName);
        Map<String, MagiskProcess> magiskHiddenProcessEnableMap = new HashMap<>();
        {
            // Add default process
            MagiskProcess mp = new MagiskProcess(packageName);
            mp.setEnabled(magiskHiddenProcesses.contains(packageName));
            magiskHiddenProcessEnableMap.put(packageName, mp);
        }
        // Add other processes: order must be preserved
        if (packageInfo.services != null) {
            for (ServiceInfo info : packageInfo.services) {
                if (!packageName.equals(info.processName) && magiskHiddenProcessEnableMap.get(info.processName) == null) {
                    MagiskProcess mp = new MagiskProcess(packageName, info.processName);
                    mp.setEnabled(magiskHiddenProcesses.contains(info.processName));
                    mp.setIsolatedProcess((info.flags & ServiceInfo.FLAG_ISOLATED_PROCESS) != 0);
                    magiskHiddenProcessEnableMap.put(info.processName, mp);
                }
            }
        }
        if (packageInfo.activities != null) {
            for (ComponentInfo info : packageInfo.activities) {
                if (!packageName.equals(info.processName) && magiskHiddenProcessEnableMap.get(info.processName) == null) {
                    MagiskProcess mp = new MagiskProcess(packageName, info.processName);
                    mp.setEnabled(magiskHiddenProcesses.contains(info.processName));
                    magiskHiddenProcessEnableMap.put(info.processName, mp);
                }
            }
        }
        if (packageInfo.providers != null) {
            for (ComponentInfo info : packageInfo.providers) {
                if (!packageName.equals(info.processName) && magiskHiddenProcessEnableMap.get(info.processName) == null) {
                    MagiskProcess mp = new MagiskProcess(packageName, info.processName);
                    mp.setEnabled(magiskHiddenProcesses.contains(info.processName));
                    magiskHiddenProcessEnableMap.put(info.processName, mp);
                }
            }
        }
        if (packageInfo.receivers != null) {
            for (ComponentInfo info : packageInfo.receivers) {
                if (!packageName.equals(info.processName) && magiskHiddenProcessEnableMap.get(info.processName) == null) {
                    MagiskProcess mp = new MagiskProcess(packageName, info.processName);
                    mp.setEnabled(magiskHiddenProcesses.contains(info.processName));
                    magiskHiddenProcessEnableMap.put(info.processName, mp);
                }
            }
        }
        return magiskHiddenProcessEnableMap.values();
    }

    @NonNull
    public static Collection<String> getMagiskHiddenProcesses(@NonNull String packageName) {
        Runner.Result result = Runner.runCommand("magiskhide ls | grep " + packageName);
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

    public static boolean apply(@NonNull MagiskProcess magiskProcess) {
        if (magiskProcess.isEnabled()) {
            return hide(magiskProcess.packageName, magiskProcess.name);
        }
        return unhide(magiskProcess.packageName, magiskProcess.name);
    }

    public static boolean hide(String packageName, String processName) {
        // Check MagiskHide status
        if (!isMagiskHideEnabled(true)) return false;
        // MagiskHide is enabled, enable hide for the package
        return Runner.runCommand(new String[]{"magiskhide", "add", packageName, processName}).isSuccessful();
    }

    public static boolean unhide(String packageName, String processName) {
        // Disable hide for the package (don't need to check for status)
        return Runner.runCommand(new String[]{"magiskhide", "rm", packageName, processName}).isSuccessful();
    }

    public static boolean isMagiskHideEnabled(boolean forceEnable) {
        // Check MagiskHide status
        if (!Runner.runCommand(new String[]{"magiskhide", "status"}).isSuccessful()) {
            // Enable MagiskHide first
            if (forceEnable) {
                return Runner.runCommand(new String[]{"magiskhide", "enable"}).isSuccessful();
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
