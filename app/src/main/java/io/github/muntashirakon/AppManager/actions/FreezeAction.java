// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

public final class FreezeAction extends Action {
    public FreezeAction(@NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                        @Nullable ActionParameters offParameters) {
        this(java.util.UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION,
                target, onParameters, offParameters,
                Strictness.STRICT, offParameters == null ? RevertMode.NONE : RevertMode.EXPLICIT);
    }

    public FreezeAction(@NonNull String id, @Nullable String name, int schemaVersion,
                        @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                        @Nullable ActionParameters offParameters, @NonNull Strictness strictness,
                        @NonNull RevertMode revertMode) {
        super(id == null ? java.util.UUID.randomUUID().toString() : id, name, schemaVersion,
                ActionKind.FREEZE, target, onParameters, offParameters, strictness, revertMode);
    }

    private FreezeAction(@NonNull Parcel in, @NonNull ActionKind kind) {
        super(in, kind);
    }

    @NonNull
    static FreezeAction fromParcel(@NonNull Parcel in, @NonNull ActionKind kind) {
        return new FreezeAction(in, kind);
    }

    @NonNull
    @Override
    public List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                      @NonNull List<ActionExecutionRecord> previousRecords) {
        if (getTarget().getScope() != ActionScope.PACKAGE) {
            throw new IllegalStateException("FreezeAction requires PACKAGE scope");
        }
        ActionParameters parameters = mode == ActionMode.ON ? getOnParameters() : getOffParameters();
        List<UserPackagePair> targets = new DefaultPackageTargetResolver().resolve(
                context, getTarget().getPackageTarget());
        List<ActionResult> results = new ArrayList<>(targets.size());
        if (parameters == null) {
            for (UserPackagePair target : targets) {
                results.add(result(target, ActionResultStatus.UNSUPPORTED,
                        "FreezeAction has no OFF value"));
            }
            return results;
        }
        boolean freeze = asBoolean(parameters, "freeze");
        int freezeType = getFreezeType(parameters);
        for (UserPackagePair target : targets) {
            try {
                if (freeze) {
                    FreezeUtils.freeze(target.getPackageName(), target.getUserId(), freezeType);
                } else {
                    FreezeUtils.unfreeze(target.getPackageName(), target.getUserId());
                }
                results.add(result(target, ActionResultStatus.SUCCESS, null));
            } catch (Throwable e) {
                results.add(result(target, ActionResultStatus.FAILED, e.getMessage()));
            }
        }
        return results;
    }

    @NonNull
    private ActionResult result(@NonNull UserPackagePair target, @NonNull ActionResultStatus status,
                                @Nullable String message) {
        return new ActionResult(getId(), target, status, ActionConflict.NO_CONFLICT,
                null, null, message);
    }

    private static boolean asBoolean(@NonNull ActionParameters parameters, @NonNull String key) {
        Object raw = parameters.get(key);
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof String) {
            return Boolean.parseBoolean((String) raw);
        }
        throw new IllegalArgumentException("Expected boolean freeze parameter: " + key);
    }

    @VisibleForTesting
    public int getFreezeType(@NonNull ActionMode mode) {
        ActionParameters parameters = mode == ActionMode.ON ? getOnParameters() : getOffParameters();
        if (parameters == null) throw new IllegalStateException("Missing freeze parameters");
        return getFreezeType(parameters);
    }

    private static int getFreezeType(@NonNull ActionParameters parameters) {
        Object raw = parameters.get("freeze_type");
        if (!(raw instanceof Integer)) {
            throw new IllegalArgumentException("Expected integer freeze_type parameter");
        }
        int freezeType = (Integer) raw;
        if (freezeType != FreezeUtils.FREEZE_DISABLE
                && freezeType != FreezeUtils.FREEZE_SUSPEND
                && freezeType != FreezeUtils.FREEZE_HIDE
                && freezeType != FreezeUtils.FREEZE_ADV_SUSPEND) {
            throw new IllegalArgumentException("Invalid freeze type: " + freezeType);
        }
        return freezeType;
    }
}
