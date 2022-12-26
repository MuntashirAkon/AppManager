// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc.ps;

import android.os.SystemClock;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.internal.util.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.proc.ProcMemStat;
import io.github.muntashirakon.proc.ProcStat;
import io.github.muntashirakon.proc.ProcStatus;

/**
 * This is a generic Java-way of parsing processes from /proc. This is a work in progress and by no means perfect. To
 * create this class, I extensively followed the documentation located at https://www.kernel.org/doc/Documentation/filesystems/proc.txt.
 */
public class Ps {
    private static final String STAT = "stat";
    private static final String STATUS = "status";
    private static final String MEM_STAT = "statm";
    private static final String SEPOL = "attr/current";
    private static final String NAME = "cmdline";
    private static final String UPTIME = "uptime";
    private static final String WCHAN = "wchan";

    private final File procFile;
    @GuardedBy("processEntries")
    private final ArrayList<ProcessEntry> processEntries = new ArrayList<>(256);
    private long uptime;
    private long clockTicks;

    public Ps() {
        this(new File("/proc"));
    }

    @VisibleForTesting
    public Ps(File procFile) {
        this.procFile = procFile;
    }

    @AnyThread
    @GuardedBy("processEntries")
    @NonNull
    public ArrayList<ProcessEntry> getProcesses() {
        synchronized (processEntries) {
            return processEntries;
        }
    }

    @WorkerThread
    @GuardedBy("processEntries")
    public void loadProcesses() {
        synchronized (processEntries) {
            ArrayList<File> procPidFiles = new ArrayList<>(256);
            processEntries.clear();
            // Gather proc files
            File[] procFileArr = procFile.listFiles((dir, name) -> TextUtils.isDigitsOnly(name));
            if (procFileArr != null) {
                procPidFiles.addAll(Arrays.asList(procFileArr));
            }
            long currentTimeSeconds = SystemClock.elapsedRealtime() / 1000;
            Path uptimePath = Paths.build(procFile, UPTIME);
            uptime = uptimePath != null ? Double.valueOf(uptimePath.getContentAsString("" + currentTimeSeconds)
                    .split("\\s")[0]).longValue() : currentTimeSeconds;
            if (!Utils.isRoboUnitTest()) {
                clockTicks = CpuUtils.getClockTicksPerSecond();
            } else clockTicks = 100; // To prevent error due to native library
            // Get process info for each PID
            for (File pidFile : procPidFiles) {
                ProcItem procItem = new ProcItem();
                // Parse stat
                Path statFile = Paths.get(new File(pidFile, STAT));
                procItem.stat = ProcStat.parseStat(statFile.getContentAsString().toCharArray());
                // Parse statm
                Path memStatFile = Paths.get(new File(pidFile, MEM_STAT));
                procItem.memStat = ProcMemStat.parseMemStat(memStatFile.getContentAsString());
                // Parse status
                Path statusFile = Paths.get(new File(pidFile, STATUS));
                procItem.status = ProcStatus.parseStatus(statusFile.getContentAsString());
                procItem.name = Paths.get(new File(pidFile, NAME)).getContentAsString().trim();
                procItem.sepol = Paths.get(new File(pidFile, SEPOL)).getContentAsString().trim();
                procItem.wchan = Paths.get(new File(pidFile, WCHAN)).getContentAsString().trim();
                processEntries.add(newProcess(procItem));
            }
        }
    }

    @NonNull
    private ProcessEntry newProcess(@NonNull ProcItem procItem) {
        ProcessEntry processEntry = new ProcessEntry();
        processEntry.pid = Integer.decode(procItem.stat[ProcStat.STAT_PID]);
        processEntry.ppid = Integer.decode(procItem.stat[ProcStat.STAT_PPID]);
        processEntry.priority = Integer.decode(procItem.stat[ProcStat.STAT_PRIORITY]);
        processEntry.niceness = Integer.decode(procItem.stat[ProcStat.STAT_NICE]);
        processEntry.instructionPointer = Long.decode(procItem.stat[ProcStat.STAT_EIP]);
        processEntry.virtualMemorySize = Long.decode(procItem.stat[ProcStat.STAT_VSIZE]);
        processEntry.residentSetSize = Long.decode(procItem.stat[ProcStat.STAT_RSS]);
        processEntry.sharedMemory = Long.decode(procItem.memStat[ProcMemStat.MEM_STAT_SHARED]);
        processEntry.processGroupId = Integer.decode(procItem.stat[ProcStat.STAT_PGRP]);
        processEntry.majorPageFaults = Integer.decode(procItem.stat[ProcStat.STAT_MAJ_FLT]);
        processEntry.minorPageFaults = Integer.decode(procItem.stat[ProcStat.STAT_MIN_FLT]);
        processEntry.realTimePriority = Integer.decode(procItem.stat[ProcStat.STAT_RT_PRIORITY]);
        processEntry.schedulingPolicy = Integer.decode(procItem.stat[ProcStat.STAT_POLICY]);
        processEntry.cpu = Integer.decode(procItem.stat[ProcStat.STAT_TASK_CPU]);
        processEntry.threadCount = Integer.decode(procItem.stat[ProcStat.STAT_NUM_THREADS]);
        processEntry.tty = Integer.decode(procItem.stat[ProcStat.STAT_TTY_NR]);
        processEntry.seLinuxPolicy = procItem.sepol;
        processEntry.name = procItem.name.equals("") ? procItem.status.get(ProcStatus.STATUS_NAME) : procItem.name;
        processEntry.users = new ProcessUsers(procItem.status.get(ProcStatus.STATUS_UID), procItem.status.get(ProcStatus.STATUS_GID));
        processEntry.cpuTimeConsumed = (Integer.decode(procItem.stat[ProcStat.STAT_UTIME])
                + Integer.decode(procItem.stat[ProcStat.STAT_STIME])) / clockTicks;
        processEntry.cCpuTimeConsumed = (Integer.decode(procItem.stat[ProcStat.STAT_CUTIME])
                + Integer.decode(procItem.stat[ProcStat.STAT_CSTIME])) / clockTicks;
        processEntry.elapsedTime = uptime - (Integer.decode(procItem.stat[ProcStat.STAT_START_TIME]) / clockTicks);
        String state = procItem.status.get(ProcStatus.STATUS_STATE);
        if (state == null) {
            throw new RuntimeException("Process state cannot be empty!");
        }
        processEntry.processState = state.substring(0, 1);
        StringBuilder stateExtra = new StringBuilder();
        if (Integer.decode(procItem.stat[ProcStat.STAT_NICE]) < 0) {
            stateExtra.append("<");
        } else if (Integer.decode(procItem.stat[ProcStat.STAT_NICE]) > 0) {
            stateExtra.append("N");
        }
        if (procItem.stat[ProcStat.STAT_SID].equals(procItem.stat[ProcStat.STAT_PID])) {
            stateExtra.append("s");
        }
        String vmLck = procItem.status.get(ProcStatus.STATUS_VM_LCK);
        if (vmLck != null && Integer.decode(vmLck.substring(0, 1)) > 0) {
            stateExtra.append("L");
        }
        if (procItem.stat[ProcStat.STAT_TTY_PGRP].equals(procItem.stat[ProcStat.STAT_PID])) {
            stateExtra.append("+");
        }
        processEntry.processStatePlus = stateExtra.toString();
        return processEntry;
    }

    private static class ProcItem {
        private String[] stat;
        private String[] memStat;
        private Map<String, String> status;
        private String name;
        private String sepol;
        private String wchan;
    }
}
