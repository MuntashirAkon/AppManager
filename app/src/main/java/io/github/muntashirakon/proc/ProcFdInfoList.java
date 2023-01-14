// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.proc;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.Path;

public class ProcFdInfoList {
    public static class ProcFdInfo {
        public final int id;
        public final long offset;
        public final int mode;
        public final int mountId;
        @NonNull
        public final Path realPath;
        @NonNull
        public final String[] extras;

        private ProcFdInfo(int id, @NonNull Path realPath, @NonNull String[] info) {
            this.id = id;
            this.realPath = realPath;
            this.extras = new String[info.length - 3]; // Ignore three mandatory fields
            int i = 0;
            long offset = -1;
            int mode = -1;
            int mountId = -1;
            for (String line : info) {
                if (line.startsWith("pos:")) {
                    offset = Long.decode(line.substring(4).trim());
                } else if (line.startsWith("flags:")) {
                    mode = Integer.decode(line.substring(6).trim());
                } else if (line.startsWith("mnt_id:")) {
                    mountId = Integer.decode(line.substring(7).trim());
                } else {
                    extras[i++] = line;
                }
            }
            assert offset != -1;
            assert mode != -1;
            assert mountId != -1;

            this.offset = offset;
            this.mode = mode;
            this.mountId = mountId;
        }

        public String getModeString() {
            return FileUtils.translateModePosixToString(mode);
        }
    }

    private final Map<Integer, ProcFdInfo> mFdInfoMap;

    ProcFdInfoList(@NonNull Path[] fdFiles, @NonNull String[] fdInfoList) {
        assert fdFiles.length == fdInfoList.length;
        mFdInfoMap = new HashMap<>(fdFiles.length);
        for (int i = 0; i < fdFiles.length; ++i) {
            String fdInfo = fdInfoList[i];
            if (fdInfo == null) {
                // FD no longer exists
                continue;
            }
            Path fdFile = fdFiles[i];
            int fd = Integer.decode(fdFile.getName());
            ProcFdInfo procFdInfo = new ProcFdInfo(fd, fdFile, fdInfo.split("\\n"));
            mFdInfoMap.put(fd, procFdInfo);
        }
    }

    public Collection<Integer> getFds() {
        return mFdInfoMap.keySet();
    }

    @Nullable
    public ProcFdInfo getFdInfo(int fd) {
        return mFdInfoMap.get(fd);
    }
}
