// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
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

    @NonNull
    @Override
    public List<ActionResult> execute(@NonNull ActionContext context,
                                      @NonNull ActionMode mode,
                                      @NonNull List<ActionExecutionRecord> previousRecords) {
        ActionParameters parameters = requireParameters(mode);
        List<UserPackagePair> targets = resolveTargets(context);
        List<ActionResult> results = new ArrayList<>(targets.size());
        boolean optimizedValue = requireBoolean(parameters, "optimized");
        for (UserPackagePair target : targets) {
            try {
                boolean successful;
                if (optimizedValue) {
                    successful = DeviceIdleManagerCompat.enableBatteryOptimization(
                            target.getPackageName());
                } else {
                    successful = DeviceIdleManagerCompat.disableBatteryOptimization(
                            target.getPackageName());
                }
                results.add(actionResult(target, successful
                        ? ActionResultStatus.SUCCESS : ActionResultStatus.FAILED, null));
            } catch (Throwable exception) {
                results.add(actionResult(target, ActionResultStatus.FAILED, exception.getMessage()));
            }
        }
        return results;
    }
}
