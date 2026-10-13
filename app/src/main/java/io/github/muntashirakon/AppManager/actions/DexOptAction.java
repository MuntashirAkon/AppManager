// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Build;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.apk.dexopt.DexOptimizer;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

public final class DexOptAction extends PackageScopedAction {
    public DexOptAction(@NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, onParameters,
                Strictness.STRICT, RevertMode.NONE);
    }

    public DexOptAction(@NonNull String id, @Nullable String name, int schemaVersion,
                        @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                        @NonNull Strictness strictness, @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.DEXOPT, target, onParameters, null,
                strictness, revertMode);
    }

    private DexOptAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
    }

    @NonNull
    static DexOptAction fromParcel(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        return new DexOptAction(parcel, kind);
    }

    @NonNull
    @Override
    public List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                      @NonNull List<ActionExecutionRecord> previousRecords) {
        List<UserPackagePair> targets = resolveTargets(context);
        List<ActionResult> results = new ArrayList<>(targets.size());
        if (mode == ActionMode.OFF) {
            for (UserPackagePair target : targets) {
                results.add(actionResult(target, ActionResultStatus.UNSUPPORTED, "Dex optimization has no reverse operation"));
            }
            return results;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            for (UserPackagePair target : targets) {
                results.add(actionResult(target, ActionResultStatus.UNSUPPORTED, "Dex optimization is unavailable"));
            }
            return results;
        }
        ActionParameters parameters = getOnParameters();
        String compilerFilter = optionalString(parameters, "compiler_filter");
        boolean clearApplicationProfileData = optionalBoolean(parameters, "clear_profile_data", false);
        boolean checkProfiles = optionalBoolean(parameters, "check_profiles", false);
        boolean forceCompilation = optionalBoolean(parameters, "force_compilation", false);
        boolean bootComplete = optionalBoolean(parameters, "boot_complete", false);
        for (UserPackagePair target : targets)
            try {
                DexOptimizer optimizer = new DexOptimizer(PackageManagerCompat.getPackageManager(), target.getPackageName());
                boolean successful = true;
                if (clearApplicationProfileData) {
                    successful &= optimizer.clearApplicationProfileData();
                }
                if (compilerFilter != null) {
                    successful &= optimizer.performDexOptMode(checkProfiles, compilerFilter, forceCompilation, bootComplete, null);
                } else successful &= optimizer.forceDexOpt();
                results.add(actionResult(target, successful ? ActionResultStatus.SUCCESS : ActionResultStatus.FAILED, null));
            } catch (Throwable exception) {
                results.add(actionResult(target, ActionResultStatus.FAILED, exception.getMessage()));
            }
        return results;
    }
}
