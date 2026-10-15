// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public final class TrackerStateAction extends PackageScopedAction {
    public static final String PARAM_BLOCKED = "blocked";

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

    private final Map<UserPackagePair, Map<String, Boolean>> mTrackersByTarget =
            new LinkedHashMap<>();

    @Override
    protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                @NonNull List<UserPackagePair> targets,
                                @NonNull List<ActionExecutionRecord> previousRecords) {
        validateParameters();
        mTrackersByTarget.clear();
        for (UserPackagePair target : targets) {
            Map<String, Boolean> trackers = new TreeMap<>();
            for (String tracker : ComponentUtils.getTrackerComponentsForPackage(
                    target.getPackageName(), target.getUserId()).keySet()) {
                trackers.put(tracker, false);
            }
            mTrackersByTarget.put(target, trackers);
        }
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull UserPackagePair target,
                                     @NonNull List<ActionExecutionRecord> previousRecords) {
        Map<String, Boolean> trackers = trackersFor(target);
        if (trackers.isEmpty()) {
            return actionResult(target, ActionResultStatus.SUCCESS,
                    "No tracker components found");
        }
        try {
            if (requireBoolean(requireParameters(mode), PARAM_BLOCKED)) {
                ComponentUtils.blockTrackingComponents(target);
            } else {
                ComponentUtils.unblockTrackingComponents(target);
            }
            return actionResult(target, ActionResultStatus.SUCCESS, null);
        } catch (Throwable exception) {
            return actionResult(target, ActionResultStatus.FAILED, exception.getMessage());
        }
    }

    @Nullable
    @Override
    protected ActionState getCurrentState(@NonNull UserPackagePair target) throws Throwable {
        Map<String, Boolean> trackers = trackersFor(target);
        if (trackers.isEmpty()) {
            return null;
        }
        try (ComponentsBlocker blocker = ComponentsBlocker.getInstance(target.getPackageName(),
                target.getUserId())) {
            for (String tracker : trackers.keySet()) {
                boolean blocked = blocker.isComponentBlocked(tracker)
                        || PackageUtils.isComponentDisabledByUser(target.getPackageName(), tracker,
                        target.getUserId());
                trackers.put(tracker, blocked);
            }
        }
        return state(target, trackers);
    }

    @Nullable
    @Override
    protected ActionState getOnState(@NonNull UserPackagePair target) {
        return desiredState(target, requireBoolean(getOnParameters(), PARAM_BLOCKED));
    }

    @Nullable
    @Override
    protected ActionState getOffState(@NonNull UserPackagePair target) {
        return desiredState(target, requireBoolean(requireOffParameters(), PARAM_BLOCKED));
    }

    @NonNull
    private Map<String, Boolean> trackersFor(@NonNull UserPackagePair target) {
        Map<String, Boolean> trackers = mTrackersByTarget.get(target);
        if (trackers == null) {
            throw new IllegalStateException("Target was not prepared");
        }
        return trackers;
    }

    @Nullable
    private ActionState desiredState(@NonNull UserPackagePair target, boolean blocked) {
        Map<String, Boolean> trackers = trackersFor(target);
        if (trackers.isEmpty()) {
            return null;
        }
        Map<String, Boolean> desired = new TreeMap<>();
        for (String tracker : trackers.keySet()) {
            desired.put(tracker, blocked);
        }
        return state(target, desired);
    }

    @NonNull
    private static ActionState state(@NonNull UserPackagePair target,
                                     @NonNull Map<String, Boolean> trackers) {
        JSONObject value = new JSONObject();
        try {
            for (Map.Entry<String, Boolean> entry : new TreeMap<>(trackers).entrySet()) {
                value.put(entry.getKey(), entry.getValue());
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("Could not build tracker state", exception);
        }
        return new ActionState("package:" + target.getPackageName() + ":"
                + target.getUserId() + ":trackers", ActionValue.of(value.toString()));
    }

    private void validateParameters() {
        boolean onBlocked = requireBoolean(getOnParameters(), PARAM_BLOCKED);
        boolean offBlocked = requireBoolean(requireOffParameters(), PARAM_BLOCKED);
        if (onBlocked == offBlocked) {
            throw new IllegalArgumentException("ON and OFF blocked states must be opposites");
        }
    }
}
