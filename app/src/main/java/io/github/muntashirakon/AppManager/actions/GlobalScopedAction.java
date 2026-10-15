// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public abstract class GlobalScopedAction extends Action {
    protected GlobalScopedAction(@NonNull ActionKind kind, @NonNull ActionTargetSpec target,
                                 @NonNull ActionParameters onParameters,
                                 @Nullable ActionParameters offParameters) {
        super(kind, target, onParameters, offParameters);
        assert getTarget().getScope() == ActionScope.GLOBAL;
    }

    protected GlobalScopedAction(@NonNull String id, @Nullable String name, int schemaVersion,
                                 @NonNull ActionKind kind, @NonNull ActionTargetSpec target,
                                 @NonNull ActionParameters onParameters,
                                 @Nullable ActionParameters offParameters,
                                 @NonNull Strictness strictness,
                                 @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, kind, target, onParameters, offParameters, strictness,
                revertMode);
        assert getTarget().getScope() == ActionScope.GLOBAL;
    }

    protected GlobalScopedAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
        assert getTarget().getScope() == ActionScope.GLOBAL;
    }

    @NonNull
    protected final JSONObject globalTargetParameters() {
        return getTarget().getGlobalTarget().getParameters();
    }

    @NonNull
    protected final String requireTargetString(@NonNull String key) {
        String value = globalTargetParameters().optString(key, null);
        if (TextUtils.isEmpty(value)) {
            throw new IllegalArgumentException("Missing global target parameter: " + key);
        }
        return value;
    }

    @NonNull
    protected final ActionResult globalResult(@NonNull ActionResultStatus status,
                                              @Nullable String message) {
        return globalResult(status, null, null, message);
    }

    @NonNull
    protected final ActionResult globalResult(@NonNull ActionResultStatus status,
                                              @Nullable ActionState observedState,
                                              @Nullable ActionState writtenState,
                                              @Nullable String message) {
        return actionResult(null, status, observedState, writtenState, message);
    }

    @NonNull
    protected final ActionResult globalResult(@NonNull ActionResultStatus status,
                                              @NonNull ActionConflict conflict,
                                              @Nullable ActionState observedState,
                                              @Nullable ActionState writtenState,
                                              @Nullable String message) {
        return actionResult(null, status, conflict, observedState, writtenState, message);
    }

    protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                @NonNull List<ActionExecutionRecord> previousRecords)
            throws Throwable {
    }

    /**
     * Performs the action after the lifecycle has determined that execution is required.
     */
    @NonNull
    protected abstract ActionResult onExecute(@NonNull ActionContext context,
                                              @NonNull ActionMode mode,
                                              @NonNull List<ActionExecutionRecord> previousRecords)
            throws Throwable;

    protected void onPostExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                 @NonNull List<ActionResult> results) throws Throwable {
    }

    @Nullable
    protected ActionState getCurrentState() throws Throwable {
        return null;
    }

    @Nullable
    protected ActionState getOnState() throws Throwable {
        return null;
    }

    @Nullable
    protected ActionState getOffState() throws Throwable {
        return null;
    }

    @Override
    @NonNull
    public final List<ActionResult> execute(@NonNull ActionContext context,
                                            @NonNull ActionMode mode,
                                            @NonNull List<ActionExecutionRecord> previousRecords) {
        List<ActionResult> results = new java.util.ArrayList<>(1);
        ActionState currentState = null;
        try {
            onPreExecute(context, mode, previousRecords);
            ActionResult result;
            ActionState onState = getOnState();
            ActionState offState = getOffState();
            if (mode == ActionMode.OFF && getOffParameters() == null) {
                result = globalResult(ActionResultStatus.UNSUPPORTED,
                        "Action has no reverse operation");
            } else if (onState == null && offState == null) {
                // Stateless actions, such as reboot, have no state to compare. They still use
                // the same lifecycle and must implement their operation in onExecute().
                result = onExecute(context, mode, previousRecords);
            } else {
                currentState = getCurrentState();
                if (currentState == null || onState == null || offState == null) {
                    throw new IllegalStateException("Global action must provide all states or none");
                }
                ActionState requestedState = mode == ActionMode.ON ? onState : offState;
                if (currentState.isSame(requestedState)) {
                    result = globalResult(ActionResultStatus.UNCHANGED, currentState, currentState,
                            null);
                } else if (mode == ActionMode.OFF && getStrictness() == Strictness.STRICT
                        && !currentState.isSame(onState)) {
                    result = globalResult(ActionResultStatus.SKIPPED_STRICT_MISMATCH,
                            ActionConflict.EXTERNAL_CHANGE, currentState, null,
                            "Current state does not match the expected ON state");
                } else {
                    result = onExecute(context, mode, previousRecords);
                    ActionState writtenState = result.writtenState;
                    if (writtenState == null && result.status == ActionResultStatus.SUCCESS) {
                        try {
                            writtenState = getCurrentState();
                        } catch (Throwable ignored) {
                            // The mutation succeeded, but its resulting state was unreadable.
                        }
                    }
                    result = result.withStateSnapshots(currentState, writtenState);
                }
            }
            results.add(result);
        } catch (Throwable throwable) {
            results.add(globalResult(ActionResultStatus.FAILED, currentState, null,
                    throwable.getMessage()));
        } finally {
            try {
                onPostExecute(context, mode, results);
            } catch (Throwable ignored) {
                // Cleanup failures must not discard the execution result.
            }
        }
        return results;
    }

    @NonNull
    protected static ActionTargetSpec globalTarget(@NonNull JSONObject parameters) {
        return new ActionTargetSpec(new GlobalTargetSpec(parameters));
    }

    @NonNull
    protected static ActionTargetSpec emptyGlobalTarget() {
        return globalTarget(new JSONObject());
    }

    @NonNull
    protected static ActionTargetSpec globalTargetWith(@NonNull String key, @NonNull String value) {
        try {
            return globalTarget(new JSONObject().put(key, value));
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Could not create global target", exception);
        }
    }
}
