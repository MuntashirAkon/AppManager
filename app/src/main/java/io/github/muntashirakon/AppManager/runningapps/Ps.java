/*
 * Copyright (c) 2021 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.runningapps;

import androidx.annotation.*;
import com.android.internal.util.TextUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.io.ProxyFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class Ps {
    /**
     * Used for accessing contents from /proc/$PID/stat. See Table 1-4 in https://www.kernel.org/doc/Documentation/filesystems/proc.txt
     */
    @IntDef({STAT_PID, STAT_TCOMM, STAT_STATE, STAT_PPID, STAT_PGRP, STAT_SID, STAT_TTY_NR, STAT_TTY_PGRP, STAT_FLAGS,
            STAT_MIN_FLT, STAT_CMIN_FLT, STAT_MAJ_FLT, STAT_CMAJ_FLT, STAT_UTIME, STAT_STIME, STAT_CUTIME, STAT_CSTIME,
            STAT_PRIORITY, STAT_NICE, STAT_NUM_THREADS, STAT_IT_REAL_VALUE, STAT_START_TIME, STAT_VSIZE, STAT_RSS,
            STAT_RSSLIM, STAT_START_CODE, STAT_END_CODE, STAT_START_STACK, STAT_ESP, STAT_EIP, STAT_PENDING,
            STAT_BLOCKED, STAT_SIGIGN, STAT_SIGCATCH, STAT_PLACEHOLDER_0, STAT_PLACEHOLDER_1, STAT_PLACEHOLDER_2,
            STAT_EXIT_SIGNAL, STAT_TASK_CPU, STAT_RT_PRIORITY, STAT_POLICY, STAT_BLKIO_TICKS, STAT_GTIME, STAT_CGTIME,
            STAT_START_DATA, STAT_END_DATA, STAT_START_BRK, STAT_ARG_START, STAT_ARG_END, STAT_ENV_START, STAT_ENV_END,
            STAT_EXIT_CODE})
    public @interface ProcStats {}
    // /proc/$PID/stat fields
    public static final int STAT_PID = 0;
    public static final int STAT_TCOMM = 1;
    public static final int STAT_STATE = 2;
    public static final int STAT_PPID = 3;
    public static final int STAT_PGRP = 4;
    public static final int STAT_SID = 5;
    public static final int STAT_TTY_NR = 6;
    public static final int STAT_TTY_PGRP = 7;
    public static final int STAT_FLAGS = 8;
    public static final int STAT_MIN_FLT = 9;
    public static final int STAT_CMIN_FLT = 10;
    public static final int STAT_MAJ_FLT = 11;
    public static final int STAT_CMAJ_FLT = 12;
    public static final int STAT_UTIME = 13;
    public static final int STAT_STIME = 14;
    public static final int STAT_CUTIME = 15;
    public static final int STAT_CSTIME = 16;
    public static final int STAT_PRIORITY = 17;
    public static final int STAT_NICE = 18;
    public static final int STAT_NUM_THREADS = 19;
    public static final int STAT_IT_REAL_VALUE = 20;
    public static final int STAT_START_TIME = 21;
    public static final int STAT_VSIZE = 22;
    public static final int STAT_RSS = 23;
    public static final int STAT_RSSLIM = 24;
    public static final int STAT_START_CODE = 25;
    public static final int STAT_END_CODE = 26;
    public static final int STAT_START_STACK = 27;
    public static final int STAT_ESP = 28;
    public static final int STAT_EIP = 29;
    public static final int STAT_PENDING = 30;
    public static final int STAT_BLOCKED = 31;
    public static final int STAT_SIGIGN = 32;
    public static final int STAT_SIGCATCH = 33;
    public static final int STAT_PLACEHOLDER_0 = 34;
    public static final int STAT_PLACEHOLDER_1 = 35;
    public static final int STAT_PLACEHOLDER_2 = 36;
    public static final int STAT_EXIT_SIGNAL = 37;
    public static final int STAT_TASK_CPU = 38;
    public static final int STAT_RT_PRIORITY = 39;
    public static final int STAT_POLICY = 40;
    public static final int STAT_BLKIO_TICKS = 41;
    public static final int STAT_GTIME = 42;
    public static final int STAT_CGTIME = 43;
    public static final int STAT_START_DATA = 44;
    public static final int STAT_END_DATA = 45;
    public static final int STAT_START_BRK = 46;
    public static final int STAT_ARG_START = 47;
    public static final int STAT_ARG_END = 48;
    public static final int STAT_ENV_START = 49;
    public static final int STAT_ENV_END = 50;
    public static final int STAT_EXIT_CODE = 51;

    private static final int STAT_COUNT = 52;

    /**
     * Used for accessing contents from /proc/$PID/statm. See Table 1-3 in https://www.kernel.org/doc/Documentation/filesystems/proc.txt
     */
    @IntDef({MEM_STAT_SIZE, MEM_STAT_RESIDENT, MEM_STAT_SHARED, MEM_STAT_TRS, MEM_STAT_LRS, MEM_STAT_DRS, MEM_STAT_DT})
    public @interface ProcMemStats{}

    public static final int MEM_STAT_SIZE = 0;
    public static final int MEM_STAT_RESIDENT = 1;
    public static final int MEM_STAT_SHARED = 2;
    public static final int MEM_STAT_TRS = 3;
    public static final int MEM_STAT_LRS = 4;
    public static final int MEM_STAT_DRS = 5;
    public static final int MEM_STAT_DT = 6;

    private static final int MEM_STAT_COUNT = 7;

    private static final String STAT = "stat";
    private static final String STATUS = "status";
    private static final String MEM_STAT = "statm";
    private static final String SEPOL = "att/current";
    private static final String NAME = "cmdline";
    private static final String WCHAN = "wchan";

    private final File procFile;
    @GuardedBy("processes")
    private final ArrayList<Process> processes = new ArrayList<>(256);

    public Ps() {
        this(new ProxyFile("/proc"));
    }

    @VisibleForTesting
    public Ps(File procFile) {
        this.procFile = procFile;
    }

    @AnyThread
    @GuardedBy("processes")
    @NonNull
    public ArrayList<Process> getProcesses() {
        synchronized (processes) {
            return processes;
        }
    }

    @WorkerThread
    @GuardedBy("processes")
    public void loadProcesses() {
        synchronized (processes) {
            ArrayList<File> procPidFiles = new ArrayList<>(256);
            processes.clear();
            // Gather proc files
            File[] procFileArr = procFile.listFiles((dir, name) -> TextUtils.isDigitsOnly(name));
            if (procFileArr != null) {
                procPidFiles.addAll(Arrays.asList(procFileArr));
            }
            // Get process info for each PID
            for (File pidFile : procPidFiles) {
                ProcItem procItem = new ProcItem();
                // Parse stat
                File statFile = new ProxyFile(pidFile, STAT);
                procItem.stat = IOUtils.getFileContent(statFile).split("\\s");
                if (procItem.stat.length != STAT_COUNT) continue;
                File memStatFile = new ProxyFile(pidFile, MEM_STAT);
                if (procItem.memStat.length != MEM_STAT_COUNT) continue;
                procItem.memStat = IOUtils.getFileContent(memStatFile).split("\\s");
                procItem.name = IOUtils.getFileContent(new ProxyFile(pidFile, NAME));
                procItem.sepol = IOUtils.getFileContent(new ProxyFile(pidFile, SEPOL));
                procItem.wchan = IOUtils.getFileContent(new ProxyFile(pidFile, WCHAN));
                processes.add(newProcess(procItem));
            }
        }
    }

    @NonNull
    private static Process newProcess(@NonNull ProcItem procItem) {
        Process process = new Process();
        process.pid = Integer.decode(procItem.stat[STAT_PID]);
        process.ppid = Integer.decode(procItem.stat[STAT_PPID]);
        process.priority = Integer.decode(procItem.stat[STAT_PRIORITY]);
        process.niceness = Integer.decode(procItem.stat[STAT_NICE]);
        process.instructionPointer = Long.decode(procItem.stat[STAT_EIP]);
        process.virtualMemorySize = Long.decode(procItem.stat[STAT_VSIZE]);
        process.residentSetSize = Long.decode(procItem.stat[STAT_RSS]);
        process.processGroupId = Integer.decode(procItem.stat[STAT_PGRP]);
        process.majorPageFaults = Integer.decode(procItem.stat[STAT_MAJ_FLT]);
        process.minorPageFaults = Integer.decode(procItem.stat[STAT_MIN_FLT]);
        process.realTimePriority = Integer.decode(procItem.stat[STAT_RT_PRIORITY]);
        process.schedulingPolicy = Integer.decode(procItem.stat[STAT_POLICY]);
        process.cpu = Integer.decode(procItem.stat[STAT_TASK_CPU]);
        process.threadCount = Integer.decode(procItem.stat[STAT_NUM_THREADS]);
        process.tty = Integer.decode(procItem.stat[STAT_TTY_NR]);
        process.waitLocationInKernel = Integer.decode(procItem.wchan);
        process.seLinuxPolicy = procItem.sepol;
        process.name = procItem.name;
        //
        return process;
    }

    private static class ProcItem {
        private String[] stat;
        private String[] memStat;
        private String name;
        private String sepol;
        private String wchan;
    }

    public static class Process {
        public int pid;
        public int ppid;
        public int priority;
        public int niceness;
        public long instructionPointer;
        public long virtualMemorySize;
        public long residentSetSize;
        public int processGroupId;
        public int majorPageFaults;
        public int minorPageFaults;
        public int realTimePriority;
        public int schedulingPolicy;
        public int cpu;
        public int threadCount;
        public int tty;
        public int waitLocationInKernel;
        public String seLinuxPolicy;
        public String name;

        // TODO: 29/1/21 Get UIDs by parsing #STATUS
        public int uid;
        public String user;
        public int ruid;  // Real UID
        public String realUser;
        public int gid;
        public String group;
        public int rgid;
        public String realGroup;

        public long cpuTimeConsumed;
        public long elapsedTime;
        public String processState;
        public String processStatePlus;
        public int androidSchedulingPolicy;
    }
}
