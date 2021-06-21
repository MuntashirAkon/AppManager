// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;

public class AppProcessItem extends ProcessItem {
    public final PackageInfo packageInfo;

    public AppProcessItem(int pid, @NonNull PackageInfo packageInfo) {
        super(pid);
        this.packageInfo = packageInfo;
    }
}
