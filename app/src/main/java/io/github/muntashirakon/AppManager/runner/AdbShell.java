
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

class AdbShell extends Runner {
    private String deviceId;

    public AdbShell(String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public boolean isRoot() {
        return false;
    }

    @WorkerThread
    @NonNull
    @Override
    protected synchronized Result runCommand() {
        // To be implemented
        return new Result();
    }
}
