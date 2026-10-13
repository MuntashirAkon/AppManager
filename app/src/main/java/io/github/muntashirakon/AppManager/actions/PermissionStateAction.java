// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.permission.PermissionChangeResult;
import io.github.muntashirakon.AppManager.permission.PermissionControllerRegistry;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

public final class PermissionStateAction extends PackageScopedAction {
    public PermissionStateAction(@NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                                 @Nullable ActionParameters offParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, onParameters,
                offParameters);
    }

    public PermissionStateAction(@NonNull String id, @Nullable String name, int schemaVersion,
                                 @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                                 @Nullable ActionParameters offParameters) {
        this(id, name, schemaVersion, target, onParameters, offParameters, Strictness.STRICT,
                offParameters == null ? RevertMode.NONE : RevertMode.EXPLICIT);
    }

    public PermissionStateAction(@NonNull String id, @Nullable String name, int schemaVersion,
                                 @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                                 @Nullable ActionParameters offParameters, @NonNull Strictness strictness,
                                 @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.PERMISSION_STATE, target, onParameters,
                offParameters, strictness, revertMode);
    }

    private PermissionStateAction(@NonNull Parcel in, @NonNull ActionKind kind) {
        super(in, kind);
    }

    @NonNull
    static PermissionStateAction fromParcel(@NonNull Parcel in, @NonNull ActionKind kind) {
        return new PermissionStateAction(in, kind);
    }

    @NonNull
    @Override
    public List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                      @NonNull List<ActionExecutionRecord> previousRecords) {
        ActionParameters parameters = requireParameters(mode);
        List<UserPackagePair> targets = resolveTargets(context);
        List<ActionResult> results = new ArrayList<>(targets.size());
        String[] permissions = requireStringArray(parameters, "permissions");
        boolean granted = requireBoolean(parameters, "granted");
        PermissionControllerRegistry controllers = PermissionControllerRegistry.getInstance();
        AppOpsManagerCompat appOpsManager = new AppOpsManagerCompat();
        for (UserPackagePair target : targets) {
            try {
                PackageInfo packageInfo = PackageManagerCompat.getPackageInfo(target.getPackageName(),
                        PackageManager.GET_PERMISSIONS
                                | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES,
                        target.getUserId());
                for (String permission : permissions) {
                    PermissionChangeResult change = controllers.trySetGrantedForBatch(packageInfo,
                            permission, target.getUserId(), appOpsManager, granted);
                    if (!change.isSuccessful()) {
                        throw new IllegalStateException(change.getMessage(), change.getCause());
                    }
                }
                results.add(actionResult(target, ActionResultStatus.SUCCESS, null));
            } catch (Throwable e) {
                results.add(actionResult(target, ActionResultStatus.FAILED, e.getMessage()));
            }
        }
        return results;
    }
}
