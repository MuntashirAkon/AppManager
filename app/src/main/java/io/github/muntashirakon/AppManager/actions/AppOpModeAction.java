// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.content.pm.ApplicationInfo;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.rules.compontents.ExternalComponentsImporter;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

public final class AppOpModeAction extends PackageScopedAction {
    public AppOpModeAction(@NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                           @Nullable ActionParameters offParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, onParameters,
                offParameters);
    }

    public AppOpModeAction(@NonNull String id, @Nullable String name, int schemaVersion,
                           @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                           @Nullable ActionParameters offParameters) {
        this(id, name, schemaVersion, target, onParameters, offParameters, Strictness.STRICT,
                offParameters == null ? RevertMode.NONE : RevertMode.EXPLICIT);
    }

    public AppOpModeAction(@NonNull String id, @Nullable String name, int schemaVersion,
                           @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                           @Nullable ActionParameters offParameters, @NonNull Strictness strictness,
                           @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.APP_OP_MODE, target, onParameters, offParameters,
                strictness, revertMode);
    }

    private AppOpModeAction(@NonNull Parcel in, @NonNull ActionKind kind) {
        super(in, kind);
    }

    @NonNull
    static AppOpModeAction fromParcel(@NonNull Parcel in, @NonNull ActionKind kind) {
        return new AppOpModeAction(in, kind);
    }

    @NonNull
    @Override
    public List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                      @NonNull List<ActionExecutionRecord> previousRecords) {
        ActionParameters params = requireParameters(mode);
        List<UserPackagePair> targets = resolveTargets(context);
        List<ActionResult> results = new ArrayList<>(targets.size());
        int[] appOps = requireIntArray(params, "app_ops");
        int modeValue = requireInt(params, "mode");
        AppOpsManagerCompat appOpsManager = new AppOpsManagerCompat();
        for (UserPackagePair target : targets) {
            try {
                int[] requestedOps = appOps;
                if (requestedOps.length == 1 && requestedOps[0] == AppOpsManagerCompat.OP_NONE) {
                    ApplicationInfo applicationInfo = PackageManagerCompat.getApplicationInfo(
                            target.getPackageName(),
                            PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES,
                            target.getUserId());
                    List<Integer> configuredOps = new ArrayList<>();
                    for (AppOpsManagerCompat.OpEntry entry
                            : AppOpsManagerCompat.getConfiguredOpsForPackage(appOpsManager,
                            applicationInfo.packageName, applicationInfo.uid)) {
                        configuredOps.add(entry.getOp());
                    }
                    requestedOps = ArrayUtils.convertToIntArray(configuredOps);
                }
                ExternalComponentsImporter.setModeToFilteredAppOps(appOpsManager, target,
                        requestedOps, (Integer) modeValue);
                results.add(actionResult(target, ActionResultStatus.SUCCESS, null));
            } catch (Throwable e) {
                results.add(actionResult(target, ActionResultStatus.FAILED, e.getMessage()));
            }
        }
        return results;
    }
}
