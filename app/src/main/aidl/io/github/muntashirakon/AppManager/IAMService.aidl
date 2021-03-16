/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager;

import io.github.muntashirakon.AppManager.IRemoteFile;
import io.github.muntashirakon.AppManager.IRemoteProcess;
import io.github.muntashirakon.AppManager.IRemoteShell;
import io.github.muntashirakon.io.FileStatus;

// Transact code starts from 3
interface IAMService {
    IRemoteProcess newProcess(in String[] cmd, in String[] env, in String dir) = 3;
    IRemoteShell getShell(in String[] cmd) = 4;
    IRemoteFile getFile(in String file) = 5;
    List getRunningProcesses() = 6;
    void chmod(in String path, in int mode) = 7;
    void chown(in String path, in int uid, in int gid) = 8;
    FileStatus stat(in String path) = 9;
}
