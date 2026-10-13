// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.content.Context;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import io.github.muntashirakon.AppManager.types.UserPackagePair;

/**
 * Common base for Actions whose state belongs to a package/user pair.
 */
public abstract class PackageScopedAction extends Action {
    protected PackageScopedAction(@NonNull ActionKind kind, @NonNull ActionTargetSpec target,
                                  @NonNull ActionParameters onParameters,
                                  @Nullable ActionParameters offParameters) {
        super(kind, target, onParameters, offParameters);
    }

    protected PackageScopedAction(@NonNull String id, @Nullable String name, int schemaVersion,
                                  @NonNull ActionKind kind, @NonNull ActionTargetSpec target,
                                  @NonNull ActionParameters onParameters,
                                  @Nullable ActionParameters offParameters,
                                  @NonNull Strictness strictness,
                                  @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, kind, target, onParameters, offParameters,
                strictness, revertMode);
    }

    protected PackageScopedAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
    }

    @NonNull
    protected final List<UserPackagePair> resolveTargets(@NonNull ActionContext context) {
        PackageTargetSpec targetSpec = getTarget().getPackageTarget();
        LinkedHashSet<UserPackagePair> targets = new LinkedHashSet<>(targetSpec.getConfiguredTargets());
        if (targetSpec.includesSelected()) {
            targets.addAll(context.selectedTargets);
        }
        if (targetSpec.includesInherited() && context.triggerTarget != null) {
            targets.add(context.triggerTarget);
        }
        return new ArrayList<>(targets);
    }

    @NonNull
    protected final ActionParameters requireParameters(@NonNull ActionMode mode) {
        ActionParameters params = mode == ActionMode.ON ? getOnParameters() : getOffParameters();
        if (params == null) {
            throw new IllegalStateException("Missing parameters for " + mode + " transition");
        }
        return params;
    }

    protected final boolean requireBoolean(@NonNull ActionParameters parameters,
                                           @NonNull String key) {
        Object value = parameters.get(key);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("Expected boolean parameter: " + key);
        }
        return (Boolean) value;
    }

    protected final int requireInt(@NonNull ActionParameters parameters, @NonNull String key) {
        Object value = parameters.get(key);
        if (!(value instanceof Integer)) {
            throw new IllegalArgumentException("Expected int parameter: " + key);
        }
        return (Integer) value;
    }

    @NonNull
    protected final int[] requireIntArray(@NonNull ActionParameters parameters, @NonNull String key) {
        Object value = parameters.get(key);
        if (!(value instanceof int[])) {
            throw new IllegalArgumentException("Expected int[] parameter: " + key);
        }
        return (int[]) value;
    }

    @NonNull
    protected final String[] requireStringArray(@NonNull ActionParameters parameters, @NonNull String key) {
        Object value = parameters.get(key);
        if (!(value instanceof String[])) {
            throw new IllegalArgumentException("Expected String[] parameter: " + key);
        }
        return (String[]) value;
    }

    protected final boolean optionalBoolean(@NonNull ActionParameters parameters, @NonNull String key, boolean defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("Expected boolean parameter: " + key);
        }
        return (boolean) value;
    }

    @Nullable
    protected final String optionalString(@NonNull ActionParameters parameters, @NonNull String key) {
        Object value = parameters.get(key);
        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException("Expected String parameter: " + key);
        }
        return (String) value;
    }

    @Nullable
    protected final String[] optionalStringArray(@NonNull ActionParameters parameters, @NonNull String key) {
        Object value = parameters.get(key);
        if (value != null && !(value instanceof String[])) {
            throw new IllegalArgumentException("Expected String[] parameter: " + key);
        }
        return (String[]) value;
    }

    @NonNull
    protected final List<ActionResult> unsupportedResults(@NonNull List<UserPackagePair> targets,
                                                          @NonNull String message) {
        List<ActionResult> results = new ArrayList<>(targets.size());
        for (UserPackagePair target : targets) {
            results.add(actionResult(target, ActionResultStatus.UNSUPPORTED, message));
        }
        return results;
    }

    @NonNull
    protected final ActionResult actionResult(@NonNull UserPackagePair target,
                                              @NonNull ActionResultStatus status,
                                              @Nullable String message) {
        return new ActionResult(getId(), target, status, ActionConflict.NO_CONFLICT,
                null, null, message);
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        return super.toLocalizedString(context);
    }
}
