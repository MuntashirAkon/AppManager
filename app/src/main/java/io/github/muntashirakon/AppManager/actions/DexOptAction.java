// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Build;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    @Nullable
    private String mCompilerFilter;
    private boolean mClearApplicationProfileData;
    private boolean mCheckProfiles;
    private boolean mForceCompilation;
    private boolean mBootComplete;

    @Override
    protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                @NonNull List<UserPackagePair> targets,
                                @NonNull List<ActionExecutionRecord> previousRecords) {
        if (mode == ActionMode.OFF) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        ActionParameters parameters = getOnParameters();
        mCompilerFilter = optionalString(parameters, "compiler_filter");
        mClearApplicationProfileData = optionalBoolean(parameters, "clear_profile_data", false);
        mCheckProfiles = optionalBoolean(parameters, "check_profiles", false);
        mForceCompilation = optionalBoolean(parameters, "force_compilation", false);
        mBootComplete = optionalBoolean(parameters, "boot_complete", false);
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull UserPackagePair target,
                                     @NonNull List<ActionExecutionRecord> previousRecords) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return actionResult(target, ActionResultStatus.UNSUPPORTED,
                    "Dex optimization is unavailable");
        }
        DexOptimizer optimizer = new DexOptimizer(PackageManagerCompat.getPackageManager(),
                target.getPackageName());
        boolean successful = true;
        if (mClearApplicationProfileData) {
            successful &= optimizer.clearApplicationProfileData();
        }
        if (mCompilerFilter != null) {
            successful &= optimizer.performDexOptMode(mCheckProfiles, mCompilerFilter,
                    mForceCompilation, mBootComplete, null);
        } else successful &= optimizer.forceDexOpt();
        return actionResult(target,
                successful ? ActionResultStatus.SUCCESS : ActionResultStatus.FAILED, null);
    }
}
