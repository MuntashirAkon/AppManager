// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.proc;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Used for accessing contents from /proc/$PID/status. See Table 1-2 in <a href="https://www.kernel.org/doc/Documentation/filesystems/proc.txt">The /proc filesystem</a>
 */
public class ProcStatus {
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

    @NonNull
    public static ProcStatus parse(@NonNull String data) {
        Map<String, String> result = new HashMap<>(56);
        for (String line : data.split("\\n")) {
            int idxOfColon = line.indexOf(':');
            if (idxOfColon != -1) {
                result.put(line.substring(0, idxOfColon).trim(), line.substring(idxOfColon + 1).trim());
            }
        }
        return new ProcStatus(result);
    }

    @NonNull
    private final Map<String, String> mStatus;

    private ProcStatus(@NonNull Map<String, String> status) {
        mStatus = status;
    }

    @Nullable
    public String getString(@NonNull String key) {
        return mStatus.get(key);
    }

    public int getInteger(@NonNull String key, int defaultValue) {
        String string = getString(key);
        return string != null ? Integer.decode(string) : defaultValue;
    }

    public long getLong(@NonNull String key, long defaultValue) {
        String string = getString(key);
        return string != null ? Long.decode(string) : defaultValue;
    }
}
