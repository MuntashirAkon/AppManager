// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import android.os.IBinder;

import aosp.android.content.pm.ParceledListSlice;
import io.github.muntashirakon.AppManager.IRemoteProcess;
import io.github.muntashirakon.AppManager.IRemoteShell;

// Transact code starts from 3
interface IAMService {
    IRemoteProcess newProcess(in String[] cmd, in String[] env, in String dir) = 3;
    IRemoteShell getShell(in String[] cmd) = 4;
    ParceledListSlice getRunningProcesses() = 6;
    int getUid() = 12;
    void symlink(in String file, in String link) = 13;
}
