// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

public final class ClearDataAction extends PackageScopedAction {
    public ClearDataAction(@NonNull ActionTargetSpec target,
                           @NonNull ActionParameters onParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, onParameters,
                Strictness.STRICT, RevertMode.NONE);
    }

    public ClearDataAction(@NonNull String id,
                           @Nullable String name,
                           int schemaVersion,
                           @NonNull ActionTargetSpec target,
                           @NonNull ActionParameters onParameters,
                           @NonNull Strictness strictness,
                           @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.CLEAR_DATA, target, onParameters, null,
                strictness, revertMode);
    }

    private ClearDataAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
    }

    @NonNull
    static ClearDataAction fromParcel(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        return new ClearDataAction(parcel, kind);
    }

    @NonNull
    @Override
    public List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                      @NonNull List<ActionExecutionRecord> previousRecords) {
        List<UserPackagePair> targets = resolveTargets(context);
        List<ActionResult> results = new ArrayList<>(targets.size());
        if (mode == ActionMode.OFF) {
            return unsupportedResults(targets, "Clear data has no reverse operation");
        }
        for (UserPackagePair target : targets)
            try {
                PackageManagerCompat.clearApplicationUserData(target);
                results.add(actionResult(target, ActionResultStatus.SUCCESS, null));
            } catch (Throwable exception) {
                results.add(actionResult(target, ActionResultStatus.FAILED, exception.getMessage()));
            }
        return results;
    }
}
