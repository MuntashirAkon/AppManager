// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.proc;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.util.TextUtils;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class ProcFs {
    // Files in /proc/ directory
    private static final String CPU_INFO = "cpuinfo";
    private static final String MEM_INFO = "meminfo";
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

    private static ProcFs instance;

    @NonNull
    public static ProcFs getInstance() {
        if (instance == null) {
            instance = new ProcFs();
        }
        return instance;
    }

    private final Path procRoot;

    public ProcFs() {
        procRoot = Paths.get("/proc");
    }

    @VisibleForTesting
    public ProcFs(Path procRoot) {
        this.procRoot = procRoot;
    }

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
        return getStringOrNull(Paths.build(procRoot, String.valueOf(pid), ATTR, CURRENT));
    }

    @Nullable
    public String getPreviousContext(int pid) {
        return getStringOrNull(Paths.build(procRoot, String.valueOf(pid), ATTR, PREV));
    }

    private String getStringOrNull(@Nullable Path file) {
        return file != null ? file.getContentAsString() : null;
    }
}
