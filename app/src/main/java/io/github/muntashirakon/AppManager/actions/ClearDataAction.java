// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;
import android.util.AndroidException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull UserPackagePair target,
                                     @NonNull List<ActionExecutionRecord> previousRecords)
            throws AndroidException {
        PackageManagerCompat.clearApplicationUserData(target);
        return actionResult(target, ActionResultStatus.SUCCESS, null);
    }
}
