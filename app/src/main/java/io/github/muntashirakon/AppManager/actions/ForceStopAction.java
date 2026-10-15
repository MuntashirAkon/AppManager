// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

public final class ForceStopAction extends PackageScopedAction {
    public ForceStopAction(@NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, onParameters);
    }

    public ForceStopAction(@NonNull String id, @Nullable String name, int schemaVersion,
                           @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters) {
        this(id, name, schemaVersion, target, onParameters, Strictness.STRICT, RevertMode.NONE);
    }

    public ForceStopAction(@NonNull String id, @Nullable String name, int schemaVersion,
                           @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                           @NonNull Strictness strictness, @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.FORCE_STOP, target, onParameters, null,
                strictness, revertMode);
    }

    private ForceStopAction(@NonNull Parcel in, @NonNull ActionKind kind) {
        super(in, kind);
    }

    @NonNull
    static ForceStopAction fromParcel(@NonNull Parcel in, @NonNull ActionKind kind) {
        return new ForceStopAction(in, kind);
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull UserPackagePair target,
                                     @NonNull List<ActionExecutionRecord> previousRecords)
            throws SecurityException {
        PackageManagerCompat.forceStopPackage(target.getPackageName(), target.getUserId());
        return actionResult(target, ActionResultStatus.SUCCESS, null);
    }
}
