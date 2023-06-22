// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.proc;

import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.PathReader;
import io.github.muntashirakon.io.Paths;

public class ProcFs {
    // Files in /proc/ directory
    private static final String CPU_INFO = "cpuinfo";
    private static final String MEM_INFO = "meminfo";
    private static final String UID_STAT = "uid_stat";
    private static final String UPTIME = "uptime";

    // Files in /proc/$PID/ and /proc/$PID/task/$TID/ directory
    private static final String ATTR = "attr";
    private static final String AUXV = "auxv";
    private static final String CGROUP = "cgroup";
    private static final String CMDLINE = "cmdline";
    private static final String COMM = "comm";
    private static final String COREDUMP_FILTER = "coredump_filter"; // Only for PID
    private static final String CPUSET = "cpuset";
    private static final String CWD = "cwd";
    private static final String ENVIRON = "environ";
    private static final String EXE = "exe";
    private static final String FD = "fd";
    private static final String FD_INFO = "fdinfo";
    private static final String IO = "io";
    private static final String LIMITS = "limits";
    private static final String MAP_FILES = "map_files"; // Only for PID
    private static final String MAPS = "maps";
    private static final String MOUNT_INFO = "mountinfo";
    private static final String MOUNTS = "mounts";
    private static final String MOUNT_STATS = "mountstats"; // Only for PID
    private static final String NET = "net";
    private static final String OOM_ADJ = "oom_adj";
    private static final String OOM_SCORE = "oom_score";
    private static final String OOM_SCORE_ADJ = "oom_score_adj";
    private static final String PAGE_MAP = "pagemap";
    private static final String PERSONALITY = "personality";
    private static final String ROOT = "root";
    private static final String SCHED = "sched";
    private static final String SCHED_GROUP_ID = "sched_group_id"; // Only for PID
    private static final String SCHED_INIT_TASK_LOAD = "sched_init_task_load"; // Only for PID
    private static final String SCHED_WAKE_UP_IDLE = "sched_wake_up_idle"; // Only for PID
    private static final String SCHED_STAT = "schedstat";
    private static final String SMAPS = "smaps";
    private static final String SMAPS_ROLLUP = "smaps_rollup";
    private static final String STACK = "stack";
    private static final String STAT = "stat";
    private static final String STAT_MEM = "statm";
    private static final String STATUS = "status";
    private static final String SYSCALL = "syscall";
    private static final String TASK = "task"; // Only for PID
    private static final String TIME_IN_STATE = "time_in_state";
    private static final String TIMER_SLACK_NS = "timerslack_ns"; // Only for PID
    private static final String WCHAN = "wchan";

    // Files in /proc/$PID/attr
    private static final String CURRENT = "current"; // Current context
    private static final String PREV = "prev"; // Previous context

    // TODO: Files in /proc/$PID/net

    // Files in /proc/uid_stat/$UID
    private static final String TCP_SND = "tcp_snd";
    private static final String TCP_RCV = "tcp_rcv";

    private static ProcFs sInstance;

    @NonNull
    public static ProcFs getInstance() {
        if (sInstance == null) {
            sInstance = new ProcFs();
        }
        return sInstance;
    }

    private final Path procRoot;

    private ProcFs() {
        this(Paths.get("/proc"));
    }

    @VisibleForTesting
    public ProcFs(Path procRoot) {
        this.procRoot = procRoot;
    }

    @Nullable
    public String getCpuInfoHardware() {
        // Only hardware is the relevant output, other outputs can be parsed from
        // /sys/devices/system/cpu/
        Path cpuInfoPath = Paths.build(procRoot, CPU_INFO);
        if (cpuInfoPath == null) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new PathReader(cpuInfoPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("Hardware")) {
                    int colonLoc = line.indexOf(':');
                    if (colonLoc == -1) continue;
                    colonLoc += 2;
                    return line.substring(colonLoc).trim();
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    public ProcMemoryInfo getMemoryInfo() {
        String statFileContents = getStringOrNull(Paths.build(procRoot, MEM_INFO));
        if (statFileContents == null) {
            return null;
        }
        return ProcMemoryInfo.parse(statFileContents);
    }

    @NonNull
    public int[] getPids() {
        Path[] pidFiles = procRoot.listFiles((dir, name) -> TextUtils.isDigitsOnly(name));
        int[] pids = new int[pidFiles.length];
        for (int i = 0; i < pidFiles.length; ++i) {
            pids[i] = Integer.decode(pidFiles[i].getName());
        }
        return pids;
    }

    public long getUptime() {
        Path uptimePath = Paths.build(procRoot, UPTIME); // Uptime is in seconds
        if (uptimePath != null) {
            String uptimeString = uptimePath.getContentAsString(null);
            if (uptimeString != null) {
                return Double.valueOf(uptimeString.split("\\s+")[0]).longValue() * 1000;
            }
        }
        return SystemClock.elapsedRealtime();
    }

    @Nullable
    public String getCmdline(int pid) {
        return getStringOrNull(Paths.build(procRoot, String.valueOf(pid), CMDLINE));
    }

    @Nullable
    public String getCwd(int pid) {
        Path cwdPath = Paths.build(procRoot, String.valueOf(pid), CWD);
        if (cwdPath != null) {
            try {
                return cwdPath.getRealFilePath();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Nullable
    public String[] getEnvVars(int pid) {
        String data = getStringOrNull(Paths.build(procRoot, String.valueOf(pid), ENVIRON));
        return data != null ? data.split("\0") : null;
    }

    @Nullable
    public String getExe(int pid) {
        Path exePath = Paths.build(procRoot, String.valueOf(pid), EXE);
        if (exePath != null) {
            try {
                return exePath.getRealFilePath();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Nullable
    public ProcFdInfoList getFdInfo(int pid) {
        Path fdDir = Paths.build(procRoot, String.valueOf(pid), FD);
        Path fdInfoDir = Paths.build(procRoot, String.valueOf(pid), FD_INFO);
        if (fdDir == null || fdInfoDir == null) {
            return null;
        }
        Path[] fdList = fdDir.listFiles();
        String[] fdInfoList = new String[fdList.length];
        for (int i = 0; i < fdList.length; ++i) {
            Path fdInfoFile = Paths.build(fdInfoDir, fdList[i].getName());
            fdInfoList[i] = fdInfoFile != null ? fdInfoFile.getContentAsString(null) : null;
        }
        return new ProcFdInfoList(fdList, fdInfoList);
    }

    @Nullable
    public ProcMappedFiles getMapFiles(int pid) {
        Path mapFilesDir = Paths.build(procRoot, String.valueOf(pid), MAP_FILES);
        return mapFilesDir != null ? new ProcMappedFiles(mapFilesDir.listFiles()) : null;
    }

    @Nullable
    public ProcStat getStat(int pid) {
        String statFileContents = getStringOrNull(Paths.build(procRoot, String.valueOf(pid), STAT));
        if (statFileContents == null) {
            return null;
        }
        return ProcStat.parse(statFileContents.toCharArray());
    }

    @Nullable
    public ProcMemStat getMemStat(int pid) {
        String statFileContents = getStringOrNull(Paths.build(procRoot, String.valueOf(pid), STAT_MEM));
        if (statFileContents == null) {
            return null;
        }
        return ProcMemStat.parse(statFileContents);
    }

    @Nullable
    public String getRoot(int pid) {
        Path rootPath = Paths.build(procRoot, String.valueOf(pid), ROOT);
        if (rootPath != null) {
            try {
                return rootPath.getRealFilePath();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Nullable
    public ProcStatus getStatus(int pid) {
        String statFileContents = getStringOrNull(Paths.build(procRoot, String.valueOf(pid), STATUS));
        if (statFileContents == null) {
            return null;
        }
        return ProcStatus.parse(statFileContents);
    }

    @Nullable
    public String getWchan(int pid) {
        return getStringOrNull(Paths.build(procRoot, String.valueOf(pid), WCHAN));
    }

    @Nullable
    public String getCurrentContext(int pid) {
        String context = getStringOrNull(Paths.build(procRoot, String.valueOf(pid), ATTR, CURRENT));
        if (context == null) {
            return null;
        }
        return context.trim();
    }

    @Nullable
    public String getPreviousContext(int pid) {
        String context = getStringOrNull(Paths.build(procRoot, String.valueOf(pid), ATTR, PREV));
        if (context == null) {
            return null;
        }
        return context.trim();
    }

    /**
     * @deprecated Removed in API 23 (Android M). Use usage stats API instead
     */
    @Deprecated
    @Nullable
    public ProcUidNetStat getUidNetStat(int uid) {
        Path uidPath = Paths.build(procRoot, UID_STAT, String.valueOf(uid));
        if (uidPath == null) {
            return null;
        }
        return getUidNetStatInternal(uid, uidPath);
    }

    /**
     * @deprecated Removed in API 23 (Android M). Use usage stats API instead
     */
    @Deprecated
    public List<ProcUidNetStat> getAllUidNetStat() {
        Path[] uidPaths = procRoot.listFiles((dir, name) -> TextUtils.isDigitsOnly(name));
        List<ProcUidNetStat> netStats = new ArrayList<>(uidPaths.length);
        for (Path uidPath : uidPaths) {
            int uid = Integer.parseInt(uidPath.getName());
            ProcUidNetStat netStat = getUidNetStatInternal(uid, uidPath);
            if (netStat != null) {
                netStats.add(netStat);
            }
        }
        return netStats;
    }

    @Nullable
    private ProcUidNetStat getUidNetStatInternal(int uid, @NonNull Path uidPath) {
        try {
            Path txFile = uidPath.findFile(TCP_SND);
            Path rxFile = uidPath.findFile(TCP_RCV);
            long tx = Long.parseLong(txFile.getContentAsString("0").trim());
            long rx = Long.parseLong(rxFile.getContentAsString("0").trim());
            return new ProcUidNetStat(uid, tx, rx);
        } catch (FileNotFoundException ignore) {
        }
        return null;
    }

    private String getStringOrNull(@Nullable Path file) {
        return file != null ? file.getContentAsString(null) : null;
    }
}
