// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.progress;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

public abstract class QueuedProgressHandler extends ProgressHandler {
    /**
     * Call when items are added to queue. This can be unrelated to progress, but useful in situations where queues
     * need to be handled.
     */
    @MainThread
    public abstract void onQueue(@Nullable Object message);
}
