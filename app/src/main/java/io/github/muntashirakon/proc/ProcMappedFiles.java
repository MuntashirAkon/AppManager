// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.proc;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.io.Path;
import kotlin.collections.ArrayDeque;

public class ProcMappedFiles {
    public static class MappedFile {
        public final Path memoryPath;
        public final String realPath;
        public final long vmStart;
        public final long vmEnd;

        private MappedFile(@NonNull Path memoryPath, @NonNull String realPath) {
            this.memoryPath = memoryPath;
            this.realPath = realPath;
            String[] vmAreaStruct = memoryPath.getName().split("-");
            vmStart = Long.decode("0x" + vmAreaStruct[0]);
            vmEnd = Long.decode("0x" + vmAreaStruct[1]);
        }
    }

    private final Map<String, List<MappedFile>> mMappedFiles;

    ProcMappedFiles(@NonNull Path[] mappedFiles) {
        mMappedFiles = new HashMap<>(mappedFiles.length);
        for (Path file : mappedFiles) {
            try {
                String realPath = Objects.requireNonNull(file.getRealFilePath());
                MappedFile mappedFile = new MappedFile(file, realPath);
                List<MappedFile> mappedFileList = mMappedFiles.get(realPath);
                if (mappedFileList == null) {
                    mappedFileList = new ArrayDeque<>();
                    mMappedFiles.put(realPath, mappedFileList);
                }
                mappedFileList.add(mappedFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @NonNull
    public Collection<String> getRealFiles() {
        return mMappedFiles.keySet();
    }

    @Nullable
    public List<MappedFile> getMappedFiles(@NonNull String realPath) {
        return mMappedFiles.get(realPath);
    }
}
