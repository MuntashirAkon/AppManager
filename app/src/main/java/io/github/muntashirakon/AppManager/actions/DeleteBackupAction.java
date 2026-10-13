// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    @NonNull
    @Override
    public List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode, @NonNull List<ActionExecutionRecord> previousRecords) {
        List<UserPackagePair> targets = resolveTargets(context);
        List<ActionResult> results = new ArrayList<>(targets.size());
        if (mode == ActionMode.OFF) {
            for (UserPackagePair target : targets)
                results.add(actionResult(target, ActionResultStatus.UNSUPPORTED, "Delete backup has no reverse operation"));
            return results;
        }
        BatchBackupOptions options = createOptions(getOnParameters());
        BackupManager backupManager = new BackupManager();
        for (UserPackagePair target : targets) {
            try {
                backupManager.deleteBackup(options.getDeleteOpOptions(target.getPackageName(),
                        target.getUserId()));
                results.add(actionResult(target, ActionResultStatus.SUCCESS, null));
            } catch (Throwable exception) {
                results.add(actionResult(target, ActionResultStatus.FAILED, exception.getMessage()));
            }
        }
        return results;
    }

    @NonNull
    private BatchBackupOptions createOptions(@NonNull ActionParameters parameters) {
        return new BatchBackupOptions(requireInt(parameters, "flags"),
                optionalStringArray(parameters, "backup_names"),
                optionalStringArray(parameters, "relative_dirs"));
    }
}
