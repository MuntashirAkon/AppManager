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

public final class TrackerStateAction extends PackageScopedAction {
    public TrackerStateAction(@NonNull ActionTargetSpec target, @NonNull ActionParameters on,
                              @Nullable ActionParameters off) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, on, off,
                Strictness.STRICT, off == null ? RevertMode.NONE : RevertMode.EXPLICIT);
    }

    public TrackerStateAction(@NonNull String id, @Nullable String name, int schema,
                              @NonNull ActionTargetSpec target, @NonNull ActionParameters on,
                              @Nullable ActionParameters off, @NonNull Strictness strict,
                              @NonNull RevertMode revert) {
        super(id, name, schema, ActionKind.TRACKER_BLOCKING, target, on, off, strict, revert);
    }

    private TrackerStateAction(@NonNull Parcel in, @NonNull ActionKind kind) {
        super(in, kind);
    }

    @NonNull
    static TrackerStateAction fromParcel(@NonNull Parcel in, @NonNull ActionKind kind) {
        return new TrackerStateAction(in, kind);
    }

    @NonNull
    @Override
    public List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                      @NonNull List<ActionExecutionRecord> previous) {
        ActionParameters parameters = requireParameters(mode);
        List<UserPackagePair> targets = resolveTargets(context);
        List<ActionResult> results = new ArrayList<>(targets.size());
        boolean blocked = requireBoolean(parameters, "blocked");
        for (UserPackagePair target : targets) {
            try {
                if (blocked) {
                    ComponentUtils.blockTrackingComponents(target);
                } else ComponentUtils.unblockTrackingComponents(target);
                results.add(actionResult(target, ActionResultStatus.SUCCESS, null));
            } catch (Throwable e) {
                results.add(actionResult(target, ActionResultStatus.FAILED, e.getMessage()));
            }
        }
        return results;
    }
}
