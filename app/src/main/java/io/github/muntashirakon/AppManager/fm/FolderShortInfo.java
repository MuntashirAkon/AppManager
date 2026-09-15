// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

final class FolderShortInfo {
    public int folderCount;
    public int fileCount;
    public boolean canRead;
    public boolean canWrite;
    public long size = -1;

    FolderShortInfo() {
    }

    FolderShortInfo(FolderShortInfo other) {
        folderCount = other.folderCount;
        fileCount = other.fileCount;
        canRead = other.canRead;
        canWrite = other.canWrite;
        size = other.size;
    }
}
