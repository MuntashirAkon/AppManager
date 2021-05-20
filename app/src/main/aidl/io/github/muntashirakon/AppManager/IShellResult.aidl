// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

interface IShellResult {
    List<String> getStdout();
    List<String> getStderr();
    int getExitCode();
    boolean isSuccessful();
}