// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.compat.DeviceIdleManagerCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

public final class BatteryOptimizationAction extends PackageScopedAction {
    public BatteryOptimizationAction(@NonNull ActionTargetSpec target,
                                     @NonNull ActionParameters onParameters,
                                     @NonNull ActionParameters offParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, onParameters,
                offParameters, Strictness.STRICT, RevertMode.EXPLICIT);
    }

    public BatteryOptimizationAction(@NonNull String id, @Nullable String name, int schemaVersion,
                                     @NonNull ActionTargetSpec target,
                                     @NonNull ActionParameters onParameters,
                                     @NonNull ActionParameters offParameters,
                                     @NonNull Strictness strictness,
                                     @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.BATTERY_OPTIMIZATION, target, onParameters,
                offParameters, strictness, revertMode);
    }

    private BatteryOptimizationAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
    }

    @NonNull
    static BatteryOptimizationAction fromParcel(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        return new BatteryOptimizationAction(parcel, kind);
    }

    private boolean mOptimized;

    @Override
    protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                @NonNull List<UserPackagePair> targets,
                                @NonNull List<ActionExecutionRecord> previousRecords) {
        mOptimized = requireBoolean(requireParameters(mode), "optimized");
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull UserPackagePair target,
                                     @NonNull List<ActionExecutionRecord> previousRecords) {
        boolean successful;
        if (mOptimized) {
            successful = DeviceIdleManagerCompat.enableBatteryOptimization(target.getPackageName());
        } else {
            successful = DeviceIdleManagerCompat.disableBatteryOptimization(target.getPackageName());
        }
        return actionResult(target, successful
                ? ActionResultStatus.SUCCESS : ActionResultStatus.FAILED, null);
    }

    @NonNull
    @Override
    protected ActionState getCurrentState(@NonNull UserPackagePair target) {
        return state(DeviceIdleManagerCompat.isBatteryOptimizedApp(target.getPackageName()));
    }

    @NonNull
    @Override
    protected ActionState getOnState(@NonNull UserPackagePair target) {
        return state(requireBoolean(requireParameters(ActionMode.ON), "optimized"));
    }

    @NonNull
    @Override
    protected ActionState getOffState(@NonNull UserPackagePair target) {
        return state(requireBoolean(requireParameters(ActionMode.OFF), "optimized"));
    }

    @NonNull
    private static ActionState state(boolean optimized) {
        return new ActionState("package:battery_optimization",
                ActionValue.of(optimized));
    }
}
