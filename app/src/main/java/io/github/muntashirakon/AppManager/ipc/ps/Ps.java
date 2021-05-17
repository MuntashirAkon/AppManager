// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc.ps;

import androidx.annotation.*;
import androidx.collection.ArrayMap;
import com.android.internal.util.TextUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * This is a generic Java-way of parsing processes from /proc. This is a work in progress and by no means perfect. To
 * create this class, I extensively followed the documentation located at https://www.kernel.org/doc/Documentation/filesystems/proc.txt.
 */
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

    // See Table 1-2 in https://www.kernel.org/doc/Documentation/filesystems/proc.txt
    public static final String STATUS_NAME = "Name";
    public static final String STATUS_UMASK = "Umask";
    public static final String STATUS_STATE = "State";
    public static final String STATUS_TGID = "Tgid";
    public static final String STATUS_NGID = "Ngid";
    public static final String STATUS_PID = "Pid";
    public static final String STATUS_PPID = "PPid";
    public static final String STATUS_TRACER_PID = "TracerPid";
    public static final String STATUS_UID = "Uid";  // Real, effective, saved set, and file system UIDs
    public static final String STATUS_GID = "Gid";  // Real, effective, saved set, and file system GIDs
    public static final String STATUS_FD_SIZE = "FDSize";
    public static final String STATUS_GROUPS = "FDSize";
    public static final String STATUS_NS_TGID = "NStgid";
    public static final String STATUS_NS_PID = "NSpid";
    public static final String STATUS_NS_PGID = "NSpgid";
    public static final String STATUS_NS_SID = "NSsid";
    public static final String STATUS_VM_PEAK = "VmPeak";
    public static final String STATUS_VM_SIZE = "VmSize";
    public static final String STATUS_VM_LCK = "VmLck";
    public static final String STATUS_VM_PIN = "VmPin";
    public static final String STATUS_VM_HWM = "VmHWM";
    public static final String STATUS_VM_RSS = "VmRSS";
    public static final String STATUS_RSS_ANON = "RssAnon";
    public static final String STATUS_RSS_FILE = "RssFile";
    public static final String STATUS_RSS_SHMEM = "RssShmem";
    public static final String STATUS_VM_DATA = "VmData";
    public static final String STATUS_VM_STK = "VmStk";
    public static final String STATUS_VM_EXE = "VmExe";
    public static final String STATUS_VM_LIB = "VmLib";
    public static final String STATUS_VM_PTE = "VmPTE";
    public static final String STATUS_VM_SWAP = "VmSwap";
    public static final String STATUS_HUGETLB_PAGES = "HugetlbPages";
    public static final String STATUS_CORE_DUMPING = "CoreDumping";
    public static final String STATUS_THP_ENABLED = "THP_enabled";
    public static final String STATUS_THREADS = "Threads";
    public static final String STATUS_SIG_Q = "SigQ";
    public static final String STATUS_SIG_PND = "SigPnd";
    public static final String STATUS_SHD_PND = "ShdPnd";
    public static final String STATUS_SIG_BLK = "SigBlk";
    public static final String STATUS_SIG_IGN = "SigIgn";
    public static final String STATUS_SIG_CGT = "SigCgt";
    public static final String STATUS_CAP_INH = "CapInh";
    public static final String STATUS_CAP_PRM = "CapPrm";
    public static final String STATUS_CAP_EFF = "CapEff";
    public static final String STATUS_CAP_BND = "CapBnd";
    public static final String STATUS_CAP_AMB = "CapAmb";
    public static final String STATUS_NO_NEW_PRIVS = "NoNewPrivs";
    public static final String STATUS_SECCOMP = "Seccomp";
    public static final String STATUS_SPECULATION_STORE_BYPASS = "Speculation_Store_Bypass";
    public static final String STATUS_CPUS_ALLOWED = "Cpus_allowed";
    public static final String STATUS_CPUS_ALLOWED_LIST = "Cpus_allowed_list";
    public static final String STATUS_MEMS_ALLOWED = "Mems_allowed";
    public static final String STATUS_MEMS_ALLOWED_LIST = "Mems_allowed_list";
    public static final String STATUS_VOLUNTARY_CTX_SWITCHES = "voluntary_ctxt_switches";
    public static final String STATUS_NON_VOLUNTARY_CTX_SWITCHES = "nonvoluntary_ctxt_switches";

    private static final String STAT = "stat";
    private static final String STATUS = "status";
    private static final String MEM_STAT = "statm";
    private static final String SEPOL = "attr/current";
    private static final String NAME = "cmdline";
    private static final String WCHAN = "wchan";

    private final File procFile;
    @GuardedBy("processes")
    private final ArrayList<ProcessEntry> processEntries = new ArrayList<>(256);

    public Ps() {
        this(new File("/proc"));
    }

    @VisibleForTesting
    public Ps(File procFile) {
        this.procFile = procFile;
    }

    @AnyThread
    @GuardedBy("processes")
    @NonNull
    public ArrayList<ProcessEntry> getProcesses() {
        synchronized (processEntries) {
            return processEntries;
        }
    }

    @WorkerThread
    @GuardedBy("processes")
    public void loadProcesses() {
        synchronized (processEntries) {
            ArrayList<File> procPidFiles = new ArrayList<>(256);
            processEntries.clear();
            // Gather proc files
            File[] procFileArr = procFile.listFiles((dir, name) -> TextUtils.isDigitsOnly(name));
            if (procFileArr != null) {
                procPidFiles.addAll(Arrays.asList(procFileArr));
            }
            // Get process info for each PID
            for (File pidFile : procPidFiles) {
                ProcItem procItem = new ProcItem();
                // Parse stat
                File statFile = new File(pidFile, STAT);
                procItem.stat = IOUtils.getFileContent(statFile).split("\\s");
                if (procItem.stat.length != STAT_COUNT) continue;
                // Parse statm
                File memStatFile = new File(pidFile, MEM_STAT);
                procItem.memStat = IOUtils.getFileContent(memStatFile).split("\\s");
                if (procItem.memStat.length != MEM_STAT_COUNT) continue;
                // Parse status
                File statusFile = new File(pidFile, STATUS);
                for (String line : IOUtils.getFileContent(statusFile).split("\\n")) {
                    int idxOfColon = line.indexOf(':');
                    if (idxOfColon != -1) {
                        procItem.status.put(line.substring(0, idxOfColon), line.substring(idxOfColon + 1).trim());
                    }
                }
                procItem.name = IOUtils.getFileContent(new File(pidFile, NAME)).trim();
                procItem.sepol = IOUtils.getFileContent(new File(pidFile, SEPOL)).trim();
                procItem.wchan = IOUtils.getFileContent(new File(pidFile, WCHAN)).trim();
                processEntries.add(newProcess(procItem));
            }
        }
    }

    @NonNull
    private static ProcessEntry newProcess(@NonNull ProcItem procItem) {
        ProcessEntry processEntry = new ProcessEntry();
        processEntry.pid = Integer.decode(procItem.stat[STAT_PID]);
        processEntry.ppid = Integer.decode(procItem.stat[STAT_PPID]);
        processEntry.priority = Integer.decode(procItem.stat[STAT_PRIORITY]);
        processEntry.niceness = Integer.decode(procItem.stat[STAT_NICE]);
        processEntry.instructionPointer = Long.decode(procItem.stat[STAT_EIP]);
        processEntry.virtualMemorySize = Long.decode(procItem.stat[STAT_VSIZE]);
        processEntry.residentSetSize = Long.decode(procItem.stat[STAT_RSS]);
        processEntry.processGroupId = Integer.decode(procItem.stat[STAT_PGRP]);
        processEntry.majorPageFaults = Integer.decode(procItem.stat[STAT_MAJ_FLT]);
        processEntry.minorPageFaults = Integer.decode(procItem.stat[STAT_MIN_FLT]);
        processEntry.realTimePriority = Integer.decode(procItem.stat[STAT_RT_PRIORITY]);
        processEntry.schedulingPolicy = Integer.decode(procItem.stat[STAT_POLICY]);
        processEntry.cpu = Integer.decode(procItem.stat[STAT_TASK_CPU]);
        processEntry.threadCount = Integer.decode(procItem.stat[STAT_NUM_THREADS]);
        processEntry.tty = Integer.decode(procItem.stat[STAT_TTY_NR]);
        processEntry.seLinuxPolicy = procItem.sepol;
        processEntry.name = procItem.name;
        processEntry.users = new ProcessUsers(procItem.status.get(STATUS_UID), procItem.status.get(STATUS_GID));
        processEntry.cpuTimeConsumed = Integer.decode(procItem.stat[STAT_UTIME]);
        processEntry.elapsedTime = Integer.decode(procItem.stat[STAT_START_TIME]);
        String state = procItem.status.get(STATUS_STATE);
        if (state == null) {
            throw new RuntimeException("Process state cannot be empty!");
        }
        processEntry.processState = state.substring(0, 1);
        StringBuilder stateExtra = new StringBuilder();
        if (Integer.decode(procItem.stat[STAT_NICE]) < 0) {
            stateExtra.append("<");
        } else if (Integer.decode(procItem.stat[STAT_NICE])>0) {
            stateExtra.append("N");
        }
        if (procItem.stat[STAT_SID].equals(procItem.stat[STAT_PID])) {
            stateExtra.append("s");
        }
        String vmLck = procItem.status.get(STATUS_VM_LCK);
        if (vmLck != null && Integer.decode(vmLck.substring(0, 1)) > 0) {
            stateExtra.append("L");
        }
        if (procItem.stat[STAT_TTY_PGRP].equals(procItem.stat[STAT_PID])) {
            stateExtra.append("+");
        }
        processEntry.processStatePlus = stateExtra.toString();
        return processEntry;
    }

    private static class ProcItem {
        private String[] stat;
        private String[] memStat;
        private final ArrayMap<String, String> status = new ArrayMap<>(56);
        private String name;
        private String sepol;
        private String wchan;
    }
}
