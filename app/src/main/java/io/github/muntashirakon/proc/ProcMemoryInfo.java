// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.proc;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.compat.ObjectsCompat;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class ProcMemoryInfo {
    private static final String MEMINFO = "meminfo";

    private static final String MEMINFO_MEM_TOTAL = "MemTotal";
    private static final String MEMINFO_MEM_FREE = "MemFree";
    private static final String MEMINFO_MEM_AVAILABLE = "MemAvailable";
    private static final String MEMINFO_BUFFERS = "Buffers";
    private static final String MEMINFO_CACHED = "Cached";
    private static final String MEMINFO_SWAP_CACHED = "SwapCached";
    private static final String MEMINFO_ACTIVE = "Active";
    private static final String MEMINFO_INACTIVE = "Inactive";
    private static final String MEMINFO_ACTIVE_ANON = "Active(anon)";
    private static final String MEMINFO_INACTIVE_ANON = "Inactive(anon)";
    private static final String MEMINFO_ACTIVE_FILE = "Active(file)";
    private static final String MEMINFO_INACTIVE_FILE = "Inactive(file)";
    private static final String MEMINFO_UNEVICTABLE = "Unevictable";
    private static final String MEMINFO_MLOCKED = "Mlocked";
    private static final String MEMINFO_SWAP_TOTAL = "SwapTotal";
    private static final String MEMINFO_SWAP_FREE = "SwapFree";
    private static final String MEMINFO_DIRTY = "Dirty";
    private static final String MEMINFO_WRITEBACK = "Writeback";
    private static final String MEMINFO_ANON_PAGES = "AnonPages";
    private static final String MEMINFO_MAPPED = "Mapped";
    private static final String MEMINFO_SHMEM = "Shmem";
    private static final String MEMINFO_SLAB = "Slab";
    private static final String MEMINFO_S_RECLAIMABLE = "SReclaimable";
    private static final String MEMINFO_S_UNRECLAIM = "SUnreclaim";
    private static final String MEMINFO_KERNEL_STACK = "KernelStack";
    private static final String MEMINFO_PAGE_TABLES = "PageTables";
    private static final String MEMINFO_NFS_UNSTABLE = "NFS_Unstable";
    private static final String MEMINFO_BOUNCE = "Bounce";
    private static final String MEMINFO_WRITEBACK_TMP = "WritebackTmp";
    private static final String MEMINFO_COMMIT_LIMIT = "CommitLimit";
    private static final String MEMINFO_COMMITTED_AS = "Committed_AS";
    private static final String MEMINFO_VMALLOC_TOTAL = "VmallocTotal";
    private static final String MEMINFO_VMALLOC_USED = "VmallocUsed";
    private static final String MEMINFO_VMALLOC_CHUNK = "VmallocChunk";
    private static final String MEMINFO_CMA_TOTAL = "CmaTotal";
    private static final String MEMINFO_CMA_FREE = "CmaFree";

    @NonNull
    public static ProcMemoryInfo parse(@NonNull String data) {
        Map<String, Long> result = new HashMap<>(36);
        for (String line : data.split("\\n")) {
            int idxOfColon = line.indexOf(':');
            int idxOfKb = line.lastIndexOf("kB");
            if (idxOfColon != -1 && idxOfKb != -1) {
                result.put(line.substring(0, idxOfColon).trim(), Long.decode(line.substring(idxOfColon + 1, idxOfKb).trim()) << 10);
            }
        }
        return new ProcMemoryInfo(result);
    }

    private final Map<String, Long> mMemInfo;

    public ProcMemoryInfo(Map<String, Long> memInfo) {
        mMemInfo = memInfo;
    }

    public long get(@NonNull String key, long defaultValue) {
        return ObjectsCompat.requireNonNullElse(mMemInfo.get(key), defaultValue);
    }

    public long getTotalMemory() {
        return get(MEMINFO_MEM_TOTAL, 0);
    }

    public long getFreeMemory() {
        return get(MEMINFO_MEM_FREE, 0);
    }

    public long getUsedMemory() {
        return getTotalMemory() - getFreeMemory();
    }

    public long getBuffers() {
        return get(MEMINFO_BUFFERS, 0);
    }

    public long getCachedMemory() {
        return get(MEMINFO_CACHED, 0) + get(MEMINFO_SLAB, 0);
    }

    public long getApplicationMemory() {
        return getUsedMemory() - getBuffers() - getCachedMemory();
    }

    public long getTotalSwap() {
        return get(MEMINFO_SWAP_TOTAL, 0);
    }

    public long getFreeSwap() {
        return get(MEMINFO_SWAP_FREE, 0);
    }

    public long getUsedSwap() {
        return getTotalSwap() - getFreeSwap();
    }

    public long getTotalZram() {
        // https://www.kernel.org/doc/html/latest/_sources/admin-guide/blockdev/zram.rst.txt
        Path[] zramPaths = Paths.get("/sys/block/").listFiles((dir, name) -> name.startsWith("zram"));
        long totalZram = 0;
        for (Path zramPath : zramPaths) {
            Path mmStat = Paths.build(zramPath, "mm_stat");
            if (mmStat != null && mmStat.canRead()) {
                totalZram += Long.decode(mmStat.getContentAsString().split("\\s")[2].trim());
            }
        }
        return totalZram;
    }
}
