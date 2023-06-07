// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.progress;

import android.annotation.SuppressLint;
import android.app.Service;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * A generic class to handle any kind of progress. Progress can be handled in various ways such as using notifications
 * or progress indicator or both.
 */
public abstract class ProgressHandler {
    @IntDef({PROGRESS_NON_DETERMINATE, PROGRESS_DETERMINATE})
    @Retention(RetentionPolicy.SOURCE)
    protected @interface ProgressType {
    }

    protected static final int PROGRESS_NON_DETERMINATE = -1;
    protected static final int PROGRESS_DETERMINATE = 0;

    /**
     * Call this function if the progress handler is backed by a foreground service and a progressbar is needed to be
     * initiated right away. After finished working with it, call {@link #onDetach(Service)}
     */
    @MainThread
    public abstract void onAttach(@Nullable Service service, @NonNull Object message);

    /**
     * Initialise progress. Arguments here can be modified by calling {@link #onProgressUpdate(int, int, Object)}.
     *
     * @param max     Maximum progress value. Use {@code -1} to switch to non-determinate mode.
     * @param current Current progress value. Should be {@code 0}. Irrelevant in non-determinate mode.
     * @param message Additional arguments to pass on. Depends on implementation.
     */
    @MainThread
    public abstract void onProgressStart(int max, int current, @Nullable Object message);

    /**
     * Update progress
     *
     * @param max     Maximum progress value. Use {@code -1} to switch to non-determinate mode.
     * @param current Current progress value. Irrelevant in non-determinate mode.
     * @param message Additional arguments to pass on. Depends on implementation.
     */
    @MainThread
    public abstract void onProgressUpdate(int max, int current, @Nullable Object message);

    /**
     * Call when the progress is finished. If this is not attached to a foreground service, the progress also stops.
     */
    @MainThread
    public abstract void onResult(@Nullable Object message);

    /**
     * Call this function to stop progress when this is attached to a foreground service.
     */
    @MainThread
    public abstract void onDetach(@Nullable Service service);

    @Nullable
    public abstract Object getLastMessage();

    public abstract int getLastMax();

    public abstract int getLastProgress();

    /**
     * Update progress from any thread. Arguments from the last time are used.
     *
     * @param current Current progress value. Irrelevant in non-determinate mode.
     */
    @AnyThread
    public void postUpdate(int current) {
        postUpdate(getLastMax(), current, getLastMessage());
    }

    /**
     * Update progress from any thread. Arguments from the last time are used.
     *
     * @param max     Max progress values. Use {@code -1} to switch to non-determinate mode.
     * @param current Current progress value. Irrelevant in non-determinate mode.
     */
    @AnyThread
    public void postUpdate(int max, int current) {
        postUpdate(max, current, getLastMessage());
    }

    /**
     * Update progress from any thread.
     *
     * @param max     Max progress values. Use {@code -1} to switch to non-determinate mode.
     * @param current Current progress value. Irrelevant in non-determinate mode.
     * @param message Additional arguments to pass on. Depends on implementation.
     */
    @SuppressLint("WrongThread")
    @AnyThread
    public void postUpdate(int max, int current, @Nullable Object message) {
        if (ThreadUtils.isMainThread()) {
            onProgressUpdate(max, current, message);
        } else {
            ThreadUtils.postOnMainThread(() -> onProgressUpdate(max, current, message));
        }
    }
}
