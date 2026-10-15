// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.content.pm.PackageManager;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

public final class BackupApkAction extends PackageScopedAction {
    public BackupApkAction(@NonNull ActionTargetSpec target) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target,
                new ActionParameters(), Strictness.STRICT, RevertMode.NONE);
    }

    public BackupApkAction(@NonNull String id, @Nullable String name, int schemaVersion,
                           @NonNull ActionTargetSpec target,
                           @NonNull ActionParameters onParameters,
                           @NonNull Strictness strictness, @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.BACKUP_APK, target, onParameters, null,
                strictness, revertMode);
    }

    private BackupApkAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
    }

    @NonNull
    static BackupApkAction fromParcel(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        return new BackupApkAction(parcel, kind);
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull UserPackagePair target,
                                     @NonNull List<ActionExecutionRecord> previousRecords)
            throws PackageManager.NameNotFoundException, IOException, RemoteException {
        ApkUtils.backupApk(ContextUtils.getContext(), target.getPackageName(), target.getUserId());
        return actionResult(target, ActionResultStatus.SUCCESS, null);
    }
}
