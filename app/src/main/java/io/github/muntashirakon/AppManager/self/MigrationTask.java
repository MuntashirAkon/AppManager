// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.content.Context;

import io.github.muntashirakon.AppManager.utils.ContextUtils;

abstract class MigrationTask implements Runnable {
    public final long fromVersion;
    public final long toVersion;
    public final boolean mainThread;
    public final Context context;

    public MigrationTask(long fromVersion, int toVersion) {
        this(fromVersion, toVersion, false);
    }

    public MigrationTask(long fromVersion, long toVersion, boolean mainThread) {
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.mainThread = mainThread;
        this.context = ContextUtils.getContext();
    }
}
