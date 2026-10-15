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
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

public final class BackupAction extends PackageScopedAction {
    public BackupAction(@NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, onParameters,
                Strictness.STRICT, RevertMode.NONE);
    }

    public BackupAction(@NonNull String id, @Nullable String name, int schemaVersion,
                        @NonNull ActionTargetSpec target,
                        @NonNull ActionParameters onParameters,
                        @NonNull Strictness strictness, @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.BACKUP, target, onParameters, null, strictness,
                revertMode);
    }

    private BackupAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
    }

    @NonNull
    static BackupAction fromParcel(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        return new BackupAction(parcel, kind);
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
        ProgressHandler subProgressHandler = newSubProgressHandler(context);
        try {
            mBackupManager.backup(mOptions.getBackupOpOptions(target.getPackageName(),
                    target.getUserId()), subProgressHandler);
            return actionResult(target, ActionResultStatus.SUCCESS, null);
        } finally {
            finishSubProgressHandler(subProgressHandler);
        }
    }

    @NonNull
    private BatchBackupOptions createOptions(@NonNull ActionParameters parameters) {
        return new BatchBackupOptions(requireInt(parameters, "flags"),
                optionalStringArray(parameters, "backup_names"),
                optionalStringArray(parameters, "relative_dirs"));
    }
}
