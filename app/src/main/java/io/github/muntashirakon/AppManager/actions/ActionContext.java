// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.AnyThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.ArrayDeque;

import io.github.muntashirakon.AppManager.logs.Logger;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.progress.ActionProgressText;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * Runtime-only inputs used when resolving configured and inherited targets.
 */
public final class ActionContext implements Parcelable {
    @NonNull
    public final String executionId;
    @NonNull
    public final String planId;
    @NonNull
    public final ActionExecutionMode executionMode;
    @Nullable
    public final UserPackagePair triggerTarget;
    @NonNull
    public final List<UserPackagePair> selectedTargets;
    public final long startedAt;
    public final boolean automatic;
    @Nullable
    public final transient ProgressHandler progressHandler;
    private final Map<ProgressListener, Object> mProgressListeners = new LinkedHashMap<>();
    private final ArrayDeque<Runnable> mPendingProgress = new ArrayDeque<>();
    @Nullable
    private ActionProgress mLatestProgress;
    private final Map<String, ActionProgress> mChildProgress = new LinkedHashMap<>();
    private boolean mDispatchingProgress;
    private int mPlanProgressMax;
    private int mActionProgressOffset;
    private int mActionProgressWorkload = -1;
    private final Map<Action, List<UserPackagePair>> mResolvedTargets;

    public ActionContext(@Nullable UserPackagePair triggerTarget,
                         @NonNull List<UserPackagePair> selectedTargets,
                         @NonNull String planId, @NonNull ActionExecutionMode executionMode,
                         long startedAt, boolean automatic) {
        this(triggerTarget, selectedTargets, planId, executionMode, startedAt, automatic, null);
    }

    public ActionContext(@Nullable UserPackagePair triggerTarget,
                         @NonNull List<UserPackagePair> selectedTargets,
                         @NonNull String planId, @NonNull ActionExecutionMode executionMode,
                         long startedAt, boolean automatic,
                         @Nullable ProgressHandler progressHandler) {
        this(triggerTarget, selectedTargets, planId, executionMode, startedAt, automatic,
                progressHandler, null);
    }

    public ActionContext(@Nullable UserPackagePair triggerTarget,
                         @NonNull List<UserPackagePair> selectedTargets,
                         @NonNull String planId, @NonNull ActionExecutionMode executionMode,
                         long startedAt, boolean automatic,
                         @Nullable ProgressHandler progressHandler,
                         @Nullable ProgressListener progressListener) {
        executionId = UUID.randomUUID().toString();
        this.planId = planId;
        this.executionMode = executionMode;
        this.triggerTarget = triggerTarget;
        this.selectedTargets = Collections.unmodifiableList(new ArrayList<>(selectedTargets));
        this.startedAt = startedAt;
        this.automatic = automatic;
        this.progressHandler = progressHandler;
        if (progressListener != null) {
            mProgressListeners.put(progressListener, new Object());
        }
        mResolvedTargets = new IdentityHashMap<>();
    }

    @NonNull
    List<UserPackagePair> resolveTargets(@NonNull PackageScopedAction action) {
        synchronized (mResolvedTargets) {
            List<UserPackagePair> cached = mResolvedTargets.get(action);
            if (cached != null) {
                return cached;
            }
            PackageTargetSpec spec = action.getTarget().getPackageTarget();
            LinkedHashSet<UserPackagePair> targets = new LinkedHashSet<>(spec.getConfiguredTargets());
            if (spec.includesSelected()) {
                targets.addAll(selectedTargets);
            }
            if (spec.includesInherited() && triggerTarget != null) {
                targets.add(triggerTarget);
            }
            cached = Collections.unmodifiableList(new ArrayList<>(targets));
            mResolvedTargets.put(action, cached);
            return cached;
        }
    }

    private ActionContext(@NonNull Parcel in) {
        executionId = in.readString();
        planId = in.readString();
        executionMode = ActionExecutionMode.values()[in.readInt()];
        triggerTarget = in.readParcelable(UserPackagePair.class.getClassLoader());
        selectedTargets = Collections.unmodifiableList(in.createTypedArrayList(UserPackagePair.CREATOR));
        startedAt = in.readLong();
        automatic = in.readInt() != 0;
        progressHandler = null;
        mResolvedTargets = new IdentityHashMap<>();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(executionId);
        dest.writeString(planId);
        dest.writeInt(executionMode.ordinal());
        dest.writeParcelable(triggerTarget, flags);
        dest.writeTypedList(selectedTargets);
        dest.writeLong(startedAt);
        dest.writeInt(automatic ? 1 : 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @FunctionalInterface
    public interface ProgressListener {
        @AnyThread
        void onProgress(@NonNull ActionProgress progress);
    }

    /**
     * Attach an special listener to the ProgressHandler capable of handling the progress of its
     * children. The listener is unregistered automatically upon completion.
     * <p>
     * Intended for {@link ActionPlan#execute(ActionContext, ActionMode, Logger)}.
     *
     * @return Registered listener.
     */
    @NonNull
    public ProgressListener addProgressHandler(@NonNull ProgressHandler handler) {
        ProgressListener listener = new ProgressListenerWithChildren(handler);
        addProgressListener(listener);
        return listener;
    }

    private final class ProgressListenerWithChildren implements ProgressListener {
        private final ProgressHandler mHandler;
        // Presentation state is accessed only on the main thread.
        private final Map<String, ProgressHandler> mChildren = new LinkedHashMap<>();
        private volatile boolean mClosed;

        ProgressListenerWithChildren(ProgressHandler handler) {
            mHandler = handler;
        }

        @Override
        public void onProgress(@NonNull ActionProgress progress) {
            ThreadUtils.postOrRunOnMainThread(() -> {
                if (mClosed) {
                    return;
                }
                try {
                    ExUtils.exceptionAsIgnored(() -> {
                        if (progress.child == null) {
                            mHandler.postActionProgress(progress);
                        } else {
                            deliverChild(progress, progress.child);
                        }
                    });
                } finally {
                    if (progress.child == null && progress.state == ActionProgress.State.COMPLETED) {
                        removeProgressListener(this);
                    }
                }
            });
        }

        private void deliverChild(@NonNull ActionProgress progress, @NonNull ActionProgress.Child childProgress) {
            String id = childProgress.id;
            ProgressHandler child = mChildren.get(id);
            if (child == null) {
                // A child was expected but doesn't exist.
                if (progress.state == ActionProgress.State.COMPLETED) {
                    // Progress already finished, no need to create a child anymore
                    return;
                }
                // Create a new child
                child = mHandler.newSubProgressHandler(childProgress.operationName);
                child.setProgressTextInterface(new ActionProgressText(ContextUtils.getContext(), childProgress.title));
                mChildren.put(id, child);
                if (progress.state != ActionProgress.State.STARTED) {
                    // Initialize a late subscriber at the active child's current position.
                    child.postActionProgress(new ActionProgress(progress.label, progress.max,
                            progress.current, ActionProgress.State.STARTED, progress.executionMode,
                            progress.results, progress.child));
                }
            }
            try {
                child.postActionProgress(progress);
            } finally {
                if (progress.state == ActionProgress.State.COMPLETED) {
                    mChildren.remove(id);
                }
            }
        }

        void close() {
            mClosed = true;
            ThreadUtils.postOrRunOnMainThread(() -> {
                for (ProgressHandler child : mChildren.values()) {
                    ExUtils.exceptionAsIgnored(() -> child.onResult(null));
                }
                mChildren.clear();
            });
        }
    }

    /**
     * Registers an observer and replays the latest snapshot in publication order.
     */
    public void addProgressListener(@NonNull ProgressListener listener) {
        synchronized (mProgressListeners) {
            if (mProgressListeners.containsKey(listener)) {
                return;
            }
            Object registration = new Object();
            mProgressListeners.put(listener, registration);
            ActionProgress latest = mLatestProgress;
            if (latest != null) {
                mPendingProgress.add(() -> notifyProgressListener(listener, registration, latest));
            }
            for (ActionProgress child : mChildProgress.values()) {
                mPendingProgress.add(() -> notifyProgressListener(listener, registration, child));
            }
        }
        drainProgress();
    }

    /**
     * Unregisters an observer. A callback already being delivered may finish.
     */
    public void removeProgressListener(@NonNull ProgressListener listener) {
        boolean removed;
        synchronized (mProgressListeners) {
            removed = mProgressListeners.remove(listener) != null;
        }
        if (removed && listener instanceof ProgressListenerWithChildren) {
            ((ProgressListenerWithChildren) listener).close();
        }
    }

    @Nullable
    public ActionProgress getLatestProgress() {
        synchronized (mProgressListeners) {
            return mLatestProgress;
        }
    }

    /**
     * Applies an Action's numeric range before notifying the observers.
     */
    void beginActionProgress(int planMax, int offset, int workload) {
        synchronized (mProgressListeners) {
            if (mActionProgressWorkload >= 0) {
                throw new IllegalStateException("An Action progress range is already active");
            }
            mPlanProgressMax = planMax;
            mActionProgressOffset = offset;
            mActionProgressWorkload = workload;
        }
    }

    void endActionProgress() {
        synchronized (mProgressListeners) {
            mActionProgressWorkload = -1;
        }
    }

    @AnyThread
    public void dispatch(@NonNull ActionProgress progress) {
        synchronized (mProgressListeners) {
            ActionProgress event = progress;
            if (progress.child == null && mActionProgressWorkload >= 0) {
                float localProgress;
                if (progress.max == ActionProgress.INDETERMINATE) {
                    localProgress = progress.state == ActionProgress.State.COMPLETED
                            ? mActionProgressWorkload : 0;
                } else {
                    localProgress = progress.max == 0 ? 0
                            : progress.current * mActionProgressWorkload / progress.max;
                }
                event = new ActionProgress(progress.label, mPlanProgressMax,
                        mPlanProgressMax == ActionProgress.INDETERMINATE
                                ? 0 : mActionProgressOffset + localProgress,
                        ActionProgress.State.UPDATED, executionMode, progress.results);
            }
            if (event.child == null) {
                mLatestProgress = event;
            } else if (event.state == ActionProgress.State.COMPLETED) {
                mChildProgress.remove(event.child.id);
            } else {
                mChildProgress.put(event.child.id, event);
            }
            ActionProgress snapshot = event;
            Map<ProgressListener, Object> listeners = new LinkedHashMap<>(mProgressListeners);
            mPendingProgress.add(() -> {
                for (Map.Entry<ProgressListener, Object> listener : listeners.entrySet()) {
                    notifyProgressListener(listener.getKey(), listener.getValue(), snapshot);
                }
            });
        }
        drainProgress();
    }

    private void notifyProgressListener(@NonNull ProgressListener listener,
                                        @NonNull Object registration,
                                        @NonNull ActionProgress progress) {
        synchronized (mProgressListeners) {
            if (mProgressListeners.get(listener) != registration) {
                // Token didn't match: old listener?
                return;
            }
        }
        try {
            listener.onProgress(progress);
        } catch (RuntimeException ignored) {
            // Observer failures must not affect execution or other observers.
        }
    }

    private void drainProgress() {
        synchronized (mProgressListeners) {
            if (mDispatchingProgress) {
                return;
            }
            mDispatchingProgress = true;
        }
        while (true) {
            Runnable delivery;
            synchronized (mProgressListeners) {
                delivery = mPendingProgress.poll();
                if (delivery == null) {
                    mDispatchingProgress = false;
                    return;
                }
            }
            delivery.run();
        }
    }

    public static final Creator<ActionContext> CREATOR = new Creator<ActionContext>() {
        @NonNull
        @Override
        public ActionContext createFromParcel(Parcel in) {
            return new ActionContext(in);
        }

        @NonNull
        @Override
        public ActionContext[] newArray(int size) {
            return new ActionContext[size];
        }
    };
}
