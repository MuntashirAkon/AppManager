// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;

import java.util.Objects;

public class AppProcessItem extends ProcessItem {
    @NonNull
    public final PackageInfo packageInfo;

    public AppProcessItem(int pid, @NonNull PackageInfo packageInfo) {
        super(pid);
        this.packageInfo = packageInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppProcessItem)) {
            if (o instanceof ProcessItem) {
                return super.equals(o);
            }
            return false;
        }
        if (!super.equals(o)) return false;
        AppProcessItem that = (AppProcessItem) o;
        return packageInfo.equals(that.packageInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), packageInfo);
    }
}
