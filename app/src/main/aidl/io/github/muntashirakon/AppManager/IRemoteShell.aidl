// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import io.github.muntashirakon.AppManager.IShellResult;

interface IRemoteShell {
    void addCommand(in String[] commands);
    void addInputStream(in ParcelFileDescriptor inputStream);
    IShellResult exec();
}
