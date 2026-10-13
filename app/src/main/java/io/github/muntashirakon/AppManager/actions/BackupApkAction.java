// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
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
    public List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                      @NonNull List<ActionExecutionRecord> previousRecords) {
        List<UserPackagePair> targets = resolveTargets(context);
        if (mode == ActionMode.OFF) {
            return unsupportedResults(targets, "APK backup has no reverse operation");
        }

        List<ActionResult> results = new ArrayList<>(targets.size());
        for (UserPackagePair target : targets) {
            try {
                ApkUtils.backupApk(ContextUtils.getContext(), target.getPackageName(),
                        target.getUserId());
                results.add(actionResult(target, ActionResultStatus.SUCCESS, null));
            } catch (Throwable exception) {
                results.add(actionResult(target, ActionResultStatus.FAILED,
                        exception.getMessage()));
            }
        }
        return results;
    }
}
