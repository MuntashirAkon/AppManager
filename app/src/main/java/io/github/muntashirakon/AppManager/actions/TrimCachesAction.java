// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.StorageManagerCompat;

/**
 * Trims application caches on internal storage.
 */
public final class TrimCachesAction extends GlobalScopedAction {
    public TrimCachesAction() {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, emptyGlobalTarget(),
                new ActionParameters(), Strictness.STRICT, RevertMode.NONE);
    }

    public TrimCachesAction(@NonNull String id, @Nullable String name, int schemaVersion,
                            @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                            @NonNull Strictness strictness, @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.TRIM_CACHES, target, onParameters, null,
                strictness, revertMode);
    }

    private TrimCachesAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
    }

    @NonNull
    static TrimCachesAction fromParcel(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        return new TrimCachesAction(parcel, kind);
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull List<ActionExecutionRecord> previousRecords) throws Throwable {
        // Free up to 1 TiB internally.
        long requestedFreeBytes = 1024L * 1024L * 1024L * 1024L;
        PackageManagerCompat.freeStorageAndNotify(null, requestedFreeBytes,
                StorageManagerCompat.FLAG_ALLOCATE_DEFY_ALL_RESERVED);
        return globalResult(ActionResultStatus.SUCCESS, null);
    }
}
