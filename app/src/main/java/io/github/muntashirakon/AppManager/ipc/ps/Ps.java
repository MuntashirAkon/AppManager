// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc.ps;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;

import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.proc.ProcFs;
import io.github.muntashirakon.proc.ProcMemStat;
import io.github.muntashirakon.proc.ProcStat;
import io.github.muntashirakon.proc.ProcStatus;

/**
 * This is a generic Java-way of parsing processes from /proc. This is a work in progress and by no means perfect. To
 * create this class, I extensively followed the documentation located at https://www.kernel.org/doc/Documentation/filesystems/proc.txt.
 */
public final class Ps {
    public static final String TAG = Ps.class.getSimpleName();

    @NonNull
    private final ProcFs mProcFs;
    @GuardedBy("processEntries")
    private final ArrayList<ProcessEntry> mProcessEntries = new ArrayList<>(256);
    private long mUptime;
    private long mClockTicks;

    public Ps() {
        this(Paths.get("/proc"));
    }

    @VisibleForTesting
    public Ps(@NonNull Path procPath) {
        mProcFs = new ProcFs(procPath);
    }

    @AnyThread
    @GuardedBy("processEntries")
    @NonNull
    public ArrayList<ProcessEntry> getProcesses() {
        synchronized (mProcessEntries) {
            return mProcessEntries;
        }
    }

    @WorkerThread
    @GuardedBy("processEntries")
    public void loadProcesses() {
        synchronized (mProcessEntries) {
            mProcessEntries.clear();
            mUptime = mProcFs.getUptime() / 1000;
            if (!Utils.isRoboUnitTest()) {
                mClockTicks = CpuUtils.getClockTicksPerSecond();
            } else mClockTicks = 100; // To prevent error due to native library
            // Get process info for each PID
            for (int pid : mProcFs.getPids()) {
                ProcItem procItem = new ProcItem();
                ProcStat procStat = mProcFs.getStat(pid);
                ProcMemStat procMemStat = mProcFs.getMemStat(pid);
                ProcStatus procStatus = mProcFs.getStatus(pid);
                if (procStat == null) {
                    Log.w(TAG, "Could not read /proc/" + pid + "/stat");
                    continue;
                }
                if (procMemStat == null) {
                    Log.w(TAG, "Could not read /proc/" + pid + "/statm");
                    continue;
                }
                if (procStatus == null) {
                    Log.w(TAG, "Could not read /proc/" + pid + "/status");
                    continue;
                }
                procItem.stat = procStat;
                procItem.memStat = procMemStat;
                procItem.status = procStatus;
                procItem.name = mProcFs.getCmdline(pid);
                procItem.sepol = mProcFs.getCurrentContext(pid);
                procItem.wchan = mProcFs.getWchan(pid);
                mProcessEntries.add(newProcess(procItem));
            }
        }
    }

    @NonNull
    private ProcessEntry newProcess(@NonNull ProcItem procItem) {
        ProcessEntry processEntry = new ProcessEntry();
        processEntry.pid = procItem.stat.getInteger(ProcStat.STAT_PID);
        processEntry.ppid = procItem.stat.getInteger(ProcStat.STAT_PPID);
        processEntry.priority = procItem.stat.getInteger(ProcStat.STAT_PRIORITY);
        processEntry.niceness = procItem.stat.getInteger(ProcStat.STAT_NICE);
        processEntry.instructionPointer = procItem.stat.getLong(ProcStat.STAT_EIP);
        processEntry.virtualMemorySize = procItem.stat.getLong(ProcStat.STAT_VSIZE);
        processEntry.residentSetSize = procItem.stat.getLong(ProcStat.STAT_RSS);
        processEntry.sharedMemory = procItem.memStat.getLong(ProcMemStat.MEM_STAT_SHARED);
        processEntry.processGroupId = procItem.stat.getInteger(ProcStat.STAT_PGRP);
        processEntry.majorPageFaults = procItem.stat.getInteger(ProcStat.STAT_MAJ_FLT);
        processEntry.minorPageFaults = procItem.stat.getInteger(ProcStat.STAT_MIN_FLT);
        processEntry.realTimePriority = procItem.stat.getInteger(ProcStat.STAT_RT_PRIORITY);
        processEntry.schedulingPolicy = procItem.stat.getInteger(ProcStat.STAT_POLICY);
        processEntry.cpu = procItem.stat.getInteger(ProcStat.STAT_TASK_CPU);
        processEntry.threadCount = procItem.stat.getInteger(ProcStat.STAT_NUM_THREADS);
        processEntry.tty = procItem.stat.getInteger(ProcStat.STAT_TTY_NR);
        processEntry.seLinuxPolicy = procItem.sepol;
        processEntry.name = TextUtils.isEmpty(procItem.name) ? procItem.status.getString(ProcStatus.STATUS_NAME) : procItem.name;
        processEntry.users = new ProcessUsers(procItem.status.getString(ProcStatus.STATUS_UID), procItem.status.getString(ProcStatus.STATUS_GID));
        processEntry.cpuTimeConsumed = (procItem.stat.getInteger(ProcStat.STAT_UTIME)
                + procItem.stat.getInteger(ProcStat.STAT_STIME)) / mClockTicks;
        processEntry.cCpuTimeConsumed = (procItem.stat.getInteger(ProcStat.STAT_CUTIME)
                + procItem.stat.getInteger(ProcStat.STAT_CSTIME)) / mClockTicks;
        processEntry.elapsedTime = mUptime - (procItem.stat.getInteger(ProcStat.STAT_START_TIME) / mClockTicks);
        String state = procItem.status.getString(ProcStatus.STATUS_STATE);
        if (state == null) {
            throw new RuntimeException("Process state cannot be empty!");
        }
        processEntry.processState = state.substring(0, 1);
        StringBuilder stateExtra = new StringBuilder();
        if (procItem.stat.getInteger(ProcStat.STAT_NICE) < 0) {
            stateExtra.append("<");
        } else if (procItem.stat.getInteger(ProcStat.STAT_NICE) > 0) {
            stateExtra.append("N");
        }
        if (procItem.stat.getInteger(ProcStat.STAT_SID) == processEntry.pid) {
            stateExtra.append("s");
        }
        String vmLck = procItem.status.getString(ProcStatus.STATUS_VM_LCK);
        if (vmLck != null && Integer.decode(vmLck.substring(0, 1)) > 0) {
            stateExtra.append("L");
        }
        if (procItem.stat.getInteger(ProcStat.STAT_TTY_PGRP) == processEntry.pid) {
            stateExtra.append("+");
        }
        processEntry.processStatePlus = stateExtra.toString();
        return processEntry;
    }

    private static class ProcItem {
        public ProcStat stat;
        public ProcMemStat memStat;
        public ProcStatus status;
        @Nullable
        public String name;
        @Nullable
        public String sepol;
        @Nullable
        public String wchan;
    }
}
