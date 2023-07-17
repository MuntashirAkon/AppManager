// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.progress;

import android.annotation.SuppressLint;
import android.app.Service;

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * A generic class to handle any kind of progress. Progress can be handled in various ways such as using notifications
 * or progress indicator or both.
 */
public abstract class ProgressHandler {
    public interface ProgressTextInterface {
        @Nullable
        CharSequence getProgressText(@NonNull ProgressHandler progressHandler);
    }

    public static final ProgressTextInterface PROGRESS_PERCENT = progressHandler -> {
        float current = progressHandler.getLastProgress() / progressHandler.getLastMax() * 100;
        return String.format(Locale.getDefault(), "%d%%", (int) current);
    };
    public static final ProgressTextInterface PROGRESS_REGULAR = progressHandler ->
            String.format(Locale.getDefault(), "%d/%d", (int) progressHandler.getLastProgress(),
                    progressHandler.getLastMax());
    protected static final ProgressTextInterface PROGRESS_DEFAULT = progressHandler -> null;

    protected static final int MAX_INDETERMINATE = -1;
    protected static final int MAX_FINISHED = -2;


    @NonNull
    protected ProgressTextInterface progressTextInterface = PROGRESS_DEFAULT;

    /**
     * Call this function if the progress handler is backed by a foreground service and a progressbar is needed to be
     * initiated right away. After finished working with it, call {@link #onDetach(Service)}
     */
    @MainThread
    public abstract void onAttach(@Nullable Service service, @NonNull Object message);

    /**
     * Initialise progress. Arguments here can be modified by calling {@link #onProgressUpdate(int, float, Object)}.
     *
     * @param max     Maximum progress value. Use {@code -1} to switch to non-determinate mode.
     * @param current Current progress value. Should be {@code 0}. Irrelevant in non-determinate mode.
     * @param message Additional arguments to pass on. Depends on implementation.
     */
    @MainThread
    public abstract void onProgressStart(int max, float current, @Nullable Object message);

    /**
     * Update progress
     *
     * @param max     Maximum progress value. Use {@code -1} to switch to non-determinate mode.
     * @param current Current progress value. Irrelevant in non-determinate mode.
     * @param message Additional arguments to pass on. Depends on implementation.
     */
    @MainThread
    public abstract void onProgressUpdate(int max, float current, @Nullable Object message);

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

    /**
     * Get a new progress handler from this handler. The handler will never have a queue handler.
     */
    @NonNull
    public abstract ProgressHandler newSubProgressHandler();

    @Nullable
    public abstract Object getLastMessage();

    public abstract int getLastMax();

    public abstract float getLastProgress();

    public void setProgressTextInterface(@Nullable ProgressTextInterface progressTextInterface) {
        this.progressTextInterface = progressTextInterface != null ? progressTextInterface : PROGRESS_DEFAULT;
    }

    /**
     * Update progress from any thread. Arguments from the last time are used.
     *
     * @param current Current progress value. Irrelevant in non-determinate mode.
     */
    @AnyThread
    public final void postUpdate(float current) {
        postUpdate(getLastMax(), current, getLastMessage());
    }

    /**
     * Update progress from any thread. Arguments from the last time are used.
     *
     * @param max     Max progress values. Use {@code -1} to switch to non-determinate mode.
     * @param current Current progress value. Irrelevant in non-determinate mode.
     */
    @AnyThread
    public final void postUpdate(int max, float current) {
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
    @CallSuper
    public void postUpdate(int max, float current, @Nullable Object message) {
        if (ThreadUtils.isMainThread()) {
            onProgressUpdate(max, current, message);
        } else {
            ThreadUtils.postOnMainThread(() -> onProgressUpdate(max, current, message));
        }
    }
}
