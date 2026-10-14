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

    @Override
    public final int getProgressMax(@NonNull ActionContext context) {
        return Math.max(1, resolveTargets(context).size());
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
        return super.actionResult(target, status, null, null, message);
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        return super.toLocalizedString(context);
    }
}
