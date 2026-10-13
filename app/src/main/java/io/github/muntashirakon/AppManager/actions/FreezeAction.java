// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

public final class FreezeAction extends PackageScopedAction {
    public FreezeAction(@NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                        @Nullable ActionParameters offParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, onParameters,
                offParameters, Strictness.STRICT,
                offParameters == null ? RevertMode.NONE : RevertMode.EXPLICIT);
    }

    public FreezeAction(@NonNull String id, @Nullable String name, int schemaVersion,
                        @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                        @Nullable ActionParameters offParameters, @NonNull Strictness strictness,
                        @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.FREEZE, target, onParameters, offParameters,
                strictness, revertMode);
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
        ActionParameters params = requireParameters(mode);
        List<UserPackagePair> targets = resolveTargets(context);
        List<ActionResult> results = new ArrayList<>(targets.size());
        boolean freeze = requireBoolean(params, "freeze");
        int freezeType = getFreezeType(requireInt(params, "freeze_type"));
        for (UserPackagePair target : targets) {
            try {
                if (freeze) {
                    FreezeUtils.freeze(target.getPackageName(), target.getUserId(), freezeType);
                } else {
                    FreezeUtils.unfreeze(target.getPackageName(), target.getUserId());
                }
                results.add(actionResult(target, ActionResultStatus.SUCCESS, null));
            } catch (Throwable e) {
                results.add(actionResult(target, ActionResultStatus.FAILED, e.getMessage()));
            }
        }
        return results;
    }

    @VisibleForTesting
    public int getFreezeType(@NonNull ActionMode mode) {
        ActionParameters params = requireParameters(mode);
        return getFreezeType(requireInt(params, "freeze_type"));
    }

    private int getFreezeType(int freezeType) {
        if (freezeType != FreezeUtils.FREEZE_DISABLE
                && freezeType != FreezeUtils.FREEZE_HIDE
                && freezeType != FreezeUtils.FREEZE_SUSPEND
                && freezeType != FreezeUtils.FREEZE_ADV_SUSPEND) {
            throw new IllegalArgumentException("Invalid freeze type: " + freezeType);
        }
        return freezeType;
    }
}
