// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.compat.SensorServiceCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

public final class SensorAccessAction extends PackageScopedAction {
    public SensorAccessAction(@NonNull ActionTargetSpec target, @NonNull ActionParameters on,
                              @NonNull ActionParameters off) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, on, off,
                Strictness.STRICT, RevertMode.EXPLICIT);
    }

    public SensorAccessAction(@NonNull String id, @Nullable String name, int schema,
                              @NonNull ActionTargetSpec target, @NonNull ActionParameters on,
                              @NonNull ActionParameters off, @NonNull Strictness strict,
                              @NonNull RevertMode revert) {
        super(id, name, schema, ActionKind.SENSOR_ACCESS, target, on, off, strict, revert);
    }

    private SensorAccessAction(@NonNull Parcel in, @NonNull ActionKind kind) {
        super(in, kind);
    }

    @NonNull
    static SensorAccessAction fromParcel(@NonNull Parcel in, @NonNull ActionKind kind) {
        return new SensorAccessAction(in, kind);
    }

    @NonNull
    @Override
    public List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                      @NonNull List<ActionExecutionRecord> previous) {
        ActionParameters parameters = requireParameters(mode);
        List<UserPackagePair> targets = resolveTargets(context);
        List<ActionResult> results = new ArrayList<>(targets.size());
        boolean enabledValue = requireBoolean(parameters, "enabled");
        for (UserPackagePair target : targets)
            try {
                SensorServiceCompat.enableSensor(target.getPackageName(), target.getUserId(), enabledValue);
                results.add(actionResult(target, ActionResultStatus.SUCCESS, null));
            } catch (Throwable exception) {
                results.add(actionResult(target, ActionResultStatus.FAILED, exception.getMessage()));
            }
        return results;
    }
}
