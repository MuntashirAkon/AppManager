// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.types.UserPackagePair;

/**
 * Progress emitted by an {@link Action} during execution.
 */
public final class ActionProgress {
    public enum State {STARTED, UPDATED, COMPLETED}

    public static final class Child {
        @NonNull
        public final String id = UUID.randomUUID().toString();
        @NonNull
        public final String actionId;
        @NonNull
        public final UserPackagePair target;
        @NonNull
        public final String operationName;
        @NonNull
        public final String title;

        public Child(@NonNull String actionId, @NonNull UserPackagePair target,
                     @NonNull CharSequence operationName, @NonNull CharSequence title) {
            this.actionId = actionId;
            this.target = target;
            this.operationName = operationName.toString();
            this.title = title.toString();
        }
    }

    /**
     * Null for parent progress. Never null for child progress.
     */
    @Nullable
    public final Child child;

    @NonNull
    public final State state;
    @NonNull
    public final ActionExecutionMode executionMode;
    @NonNull
    public final List<ActionResult> results;
    /**
     * Maximum value used when the current operation has no measurable progress.
     */
    public static final int INDETERMINATE = -1;

    @Nullable
    public final CharSequence label;
    public final int max;
    public final float current;

    public ActionProgress(@Nullable CharSequence label, int max, float current) {
        this(label, max, current, State.UPDATED, ActionExecutionMode.BATCH,
                Collections.emptyList());
    }

    public ActionProgress(@Nullable CharSequence label, int max, float current,
                          @NonNull State state, @NonNull ActionExecutionMode executionMode,
                          @NonNull List<ActionResult> results) {
        this(label, max, current, state, executionMode, results, null);
    }

    public ActionProgress(@Nullable CharSequence label, int max, float current,
                          @NonNull State state, @NonNull ActionExecutionMode executionMode,
                          @NonNull List<ActionResult> results, @Nullable Child child) {
        if (max < 0 && max != INDETERMINATE) {
            throw new IllegalArgumentException("Invalid progress maximum: " + max);
        }
        if (Float.isNaN(current) || Float.isInfinite(current)) {
            throw new IllegalArgumentException("Invalid progress value: " + current);
        }
        if (max == INDETERMINATE && current != 0) {
            throw new IllegalArgumentException("Indeterminate progress has no numeric position");
        }
        if (max != INDETERMINATE && (current < 0 || current > max)) {
            throw new IllegalArgumentException(
                    "Progress value must be within [0, max]: " + current + "/" + max);
        }
        this.label = label == null ? null : label.toString();
        this.max = max;
        this.current = current;
        this.state = state;
        this.executionMode = executionMode;
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
        this.child = child;
    }

    @VisibleForTesting
    @NonNull
    public static ActionProgress indeterminate(@Nullable CharSequence label) {
        return new ActionProgress(label, INDETERMINATE, 0);
    }

    @VisibleForTesting
    @NonNull
    public static ActionProgress determinate(@Nullable CharSequence label, int max, float current) {
        if (max < 0) {
            throw new IllegalArgumentException("Determinate progress requires a non-negative max");
        }
        return new ActionProgress(label, max, current);
    }

    public boolean isIndeterminate() {
        return max == INDETERMINATE;
    }
}
