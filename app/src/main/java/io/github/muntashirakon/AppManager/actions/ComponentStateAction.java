// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

public final class ComponentStateAction extends PackageScopedAction {
    public ComponentStateAction(@NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                                @Nullable ActionParameters offParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, onParameters,
                offParameters);
    }

    public ComponentStateAction(@NonNull String id, @Nullable String name, int schemaVersion,
                                @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                                @Nullable ActionParameters offParameters) {
        this(id, name, schemaVersion, target, onParameters, offParameters, Strictness.STRICT,
                offParameters == null ? RevertMode.NONE : RevertMode.EXPLICIT);
    }

    public ComponentStateAction(@NonNull String id, @Nullable String name, int schemaVersion,
                                @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                                @Nullable ActionParameters offParameters, @NonNull Strictness strictness,
                                @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.COMPONENT_STATE, target, onParameters,
                offParameters, strictness, revertMode);
    }

    private ComponentStateAction(@NonNull Parcel in, @NonNull ActionKind kind) {
        super(in, kind);
    }

    @NonNull
    static ComponentStateAction fromParcel(@NonNull Parcel in, @NonNull ActionKind kind) {
        return new ComponentStateAction(in, kind);
    }

    @NonNull
    @Override
    public List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                      @NonNull List<ActionExecutionRecord> previousRecords) {
        ActionParameters parameters = requireParameters(mode);
        List<UserPackagePair> targets = resolveTargets(context);
        List<ActionResult> results = new ArrayList<>(targets.size());
        boolean blocked = requireBoolean(parameters, "blocked");
        String[] signatures = requireStringArray(parameters, "signatures");
        for (UserPackagePair target : targets) {
            try {
                if (blocked) {
                    ComponentUtils.blockFilteredComponents(target, signatures);
                } else {
                    ComponentUtils.unblockFilteredComponents(target, signatures);
                }
                results.add(actionResult(target, ActionResultStatus.SUCCESS, null));
            } catch (Throwable e) {
                results.add(actionResult(target, ActionResultStatus.FAILED, e.getMessage()));
            }
        }
        return results;
    }
}
