// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

public final class InstallExistingAction extends PackageScopedAction {
    public InstallExistingAction(@NonNull ActionTargetSpec target) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target,
                new ActionParameters(), Strictness.STRICT, RevertMode.NONE);
    }

    public InstallExistingAction(@NonNull String id, @Nullable String name, int schemaVersion,
                                 @NonNull ActionTargetSpec target,
                                 @NonNull ActionParameters onParameters,
                                 @NonNull Strictness strictness,
                                 @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.INSTALL_EXISTING, target, onParameters, null,
                strictness, revertMode);
    }

    private InstallExistingAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
    }

    static InstallExistingAction fromParcel(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        return new InstallExistingAction(parcel, kind);
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull UserPackagePair target,
                                     @NonNull List<ActionExecutionRecord> previousRecords) {
        boolean installed = PackageInstallerCompat.getNewInstance()
                .installExisting(target.getPackageName(), target.getUserId());
        return actionResult(target,
                installed ? ActionResultStatus.SUCCESS : ActionResultStatus.FAILED,
                installed ? null : "Package installation failed");
    }
}
