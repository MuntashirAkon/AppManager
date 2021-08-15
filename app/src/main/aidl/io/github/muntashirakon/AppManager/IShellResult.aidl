// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import aosp.android.content.pm.StringParceledListSlice;

interface IShellResult {
    StringParceledListSlice getStdout();
    StringParceledListSlice getStderr();
    int getExitCode();
    boolean isSuccessful();
}
