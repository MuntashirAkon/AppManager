// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc.ps;

import androidx.annotation.AnyThread;
import androidx.annotation.VisibleForTesting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

@AnyThread
public class DeviceMemoryInfo {
    private static final String MEMINFO = "meminfo";

    private long mTotalMemory;
    private long mFreeMemory;
    private long mBuffers;

    private long mTotalSwap;
    private long mFreeSwap;
    private long mCached;

    private final File mMeminfoFile;

    public DeviceMemoryInfo() {
        mMeminfoFile = new File("/proc", MEMINFO);
    }

    @VisibleForTesting
    public DeviceMemoryInfo(File procFile) {
        mMeminfoFile = new File(procFile, MEMINFO);
    }

    public void reload() {
        try (BufferedReader reader = new BufferedReader(new FileReader(mMeminfoFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] splits = line.split("\\s+");
                switch (splits[0]) {
                    case "MemTotal:":
                        mTotalMemory = Long.decode(splits[1]) << 10;
                        break;
                    case "MemFree:":
                        mFreeMemory = Long.decode(splits[1]) << 10;
                        break;
                    case "Buffers:":
                        mBuffers = Long.decode(splits[1]) << 10;
                        break;
                    case "SwapTotal:":
                        mTotalSwap = Long.decode(splits[1]) << 10;
                        break;
                    case "SwapFree:":
                        mFreeSwap = Long.decode(splits[1]) << 10;
                        break;
                    case "Cached:":
                        mCached = Long.decode(splits[1]) << 10;
                        break;
                }
            }
        } catch (IOException ignore) {
        }
    }

    public long getTotalMemory() {
        return mTotalMemory;
    }

    public long getFreeMemory() {
        return mFreeMemory;
    }

    public long getUsedMemory() {
        return mTotalMemory - mFreeMemory;
    }

    public long getBuffers() {
        return mBuffers;
    }

    public long getCachedMemory() {
        return mCached;
    }

    public long getApplicationMemory() {
        return getUsedMemory() - getBuffers() - getCachedMemory();
    }

    public long getTotalSwap() {
        return mTotalSwap;
    }

    public long getFreeSwap() {
        return mFreeSwap;
    }

    public long getUsedSwap() {
        return mTotalSwap - mFreeSwap;
    }

}
