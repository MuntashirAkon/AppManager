// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.backup.BackupManager;
import io.github.muntashirakon.AppManager.batchops.struct.BatchBackupOptions;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

public final class DeleteBackupAction extends PackageScopedAction {
    public DeleteBackupAction(@NonNull ActionTargetSpec target,
                              @NonNull ActionParameters onParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, onParameters,
                Strictness.STRICT, RevertMode.NONE);
    }

    public DeleteBackupAction(@NonNull String id, @Nullable String name, int schemaVersion,
                              @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                              @NonNull Strictness strictness, @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.DELETE_BACKUP, target, onParameters, null,
                strictness, revertMode);
    }

    private DeleteBackupAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
    }

    @NonNull
    static DeleteBackupAction fromParcel(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        return new DeleteBackupAction(parcel, kind);
    }

    private BatchBackupOptions mOptions;
    private BackupManager mBackupManager;

    @Override
    protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                @NonNull List<UserPackagePair> targets,
                                @NonNull List<ActionExecutionRecord> previousRecords) {
        if (mode == ActionMode.OFF) return;
        mOptions = createOptions(getOnParameters());
        mBackupManager = new BackupManager();
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull UserPackagePair target,
                                     @NonNull List<ActionExecutionRecord> previousRecords)
            throws BackupException {
        mBackupManager.deleteBackup(mOptions.getDeleteOpOptions(target.getPackageName(),
                target.getUserId()));
        return actionResult(target, ActionResultStatus.SUCCESS, null);
    }

    @NonNull
    private BatchBackupOptions createOptions(@NonNull ActionParameters parameters) {
        return new BatchBackupOptions(requireInt(parameters, "flags"),
                optionalStringArray(parameters, "backup_names"),
                optionalStringArray(parameters, "relative_dirs"));
    }
}
