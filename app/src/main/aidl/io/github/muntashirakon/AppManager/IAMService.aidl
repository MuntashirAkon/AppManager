// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import io.github.muntashirakon.AppManager.IRemoteFile;
import io.github.muntashirakon.AppManager.IRemoteProcess;
import io.github.muntashirakon.AppManager.IRemoteShell;
import io.github.muntashirakon.io.FileStatus;
import io.github.muntashirakon.io.IFileDescriptor;

// Transact code starts from 3
interface IAMService {
    IRemoteProcess newProcess(in String[] cmd, in String[] env, in String dir) = 3;
    IRemoteShell getShell(in String[] cmd) = 4;
    IRemoteFile getFile(in String file) = 5;
    List getRunningProcesses() = 6;
    void chmod(in String path, in int mode) = 7;
    void chown(in String path, in int uid, in int gid) = 8;
    FileStatus stat(in String path) = 9;
    FileStatus lstat(in String path) = 10;
    IFileDescriptor getFD(in String path, in String mode) = 11;
}
