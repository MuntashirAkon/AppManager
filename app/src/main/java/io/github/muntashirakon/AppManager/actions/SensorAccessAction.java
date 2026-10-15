// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Build;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
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

    private boolean mEnabled;

    @Override
    protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                @NonNull List<UserPackagePair> targets,
                                @NonNull List<ActionExecutionRecord> previousRecords) {
        mEnabled = requireBoolean(requireParameters(mode), "enabled");
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull UserPackagePair target,
                                     @NonNull List<ActionExecutionRecord> previousRecords)
            throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return actionResult(target, ActionResultStatus.UNSUPPORTED,
                    "Sensor settings is unavailable");
        }
        SensorServiceCompat.enableSensor(target.getPackageName(), target.getUserId(), mEnabled);
        return actionResult(target, ActionResultStatus.SUCCESS, null);
    }

    @NonNull
    @Override
    protected ActionState getCurrentState(@NonNull UserPackagePair target) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return state(target, SensorServiceCompat.isSensorEnabled(target.getPackageName(),
                    target.getUserId()));
        }
        // Fall back is enabled
        return state(target, true);
    }

    @NonNull
    @Override
    protected ActionState getOnState(@NonNull UserPackagePair target) {
        return state(target, requireBoolean(requireParameters(ActionMode.ON), "enabled"));
    }

    @NonNull
    @Override
    protected ActionState getOffState(@NonNull UserPackagePair target) {
        return state(target, requireBoolean(requireParameters(ActionMode.OFF), "enabled"));
    }

    @NonNull
    private static ActionState state(@NonNull UserPackagePair target, boolean enabled) {
        return new ActionState("package:" + target.getPackageName() + ":"
                + target.getUserId() + ":sensor_access", ActionValue.of(enabled));
    }
}
