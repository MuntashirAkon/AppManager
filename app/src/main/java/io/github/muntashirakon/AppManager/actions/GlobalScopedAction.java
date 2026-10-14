// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

public abstract class GlobalScopedAction extends Action {
    protected GlobalScopedAction(@NonNull ActionKind kind, @NonNull ActionTargetSpec target,
                                 @NonNull ActionParameters onParameters,
                                 @Nullable ActionParameters offParameters) {
        super(kind, target, onParameters, offParameters);
    }

    protected GlobalScopedAction(@NonNull String id, @Nullable String name, int schemaVersion,
                                 @NonNull ActionKind kind, @NonNull ActionTargetSpec target,
                                 @NonNull ActionParameters onParameters,
                                 @Nullable ActionParameters offParameters,
                                 @NonNull Strictness strictness,
                                 @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, kind, target, onParameters, offParameters, strictness,
                revertMode);
    }

    protected GlobalScopedAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
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
    protected final List<ActionResult> unsupportedResults(@NonNull String message) {
        return Collections.singletonList(globalResult(ActionResultStatus.UNSUPPORTED, message));
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
