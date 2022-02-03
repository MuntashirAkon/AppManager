// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.magisk;

import androidx.annotation.NonNull;

import java.util.Objects;

public class MagiskProcess {
    @NonNull
    public final String packageName;
    @NonNull
    public final String name;

    private boolean mIsolatedProcess;
    private boolean mIsRunning;
    private boolean mIsEnabled;

    public MagiskProcess(@NonNull String packageName, @NonNull String name) {
        this.packageName = packageName;
        this.name = name;
    }

    public MagiskProcess(@NonNull String packageName) {
        this.packageName = packageName;
        this.name = packageName;
    }

    public void setEnabled(boolean enabled) {
        mIsEnabled = enabled;
    }

    public boolean isEnabled() {
        return mIsEnabled;
    }

    public void setIsolatedProcess(boolean isolatedProcess) {
        mIsolatedProcess = isolatedProcess;
    }

    public void setRunning(boolean running) {
        mIsRunning = running;
    }

    public boolean isIsolatedProcess() {
        return mIsolatedProcess;
    }

    public boolean isRunning() {
        return mIsRunning;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MagiskProcess)) return false;
        MagiskProcess that = (MagiskProcess) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}