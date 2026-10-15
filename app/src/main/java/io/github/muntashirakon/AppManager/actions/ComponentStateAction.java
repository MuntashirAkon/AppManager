// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public final class ComponentStateAction extends PackageScopedAction {
    public static final String PARAM_SIGNATURES = "signatures";
    public static final String PARAM_BLOCKED = "blocked";

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

    private String[] mSignatures;
    private final Map<UserPackagePair, Map<String, Boolean>> mComponentsByTarget =
            new LinkedHashMap<>();

    @Override
    protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                @NonNull List<UserPackagePair> targets,
                                @NonNull List<ActionExecutionRecord> previousRecords) {
        validateParameters();
        mSignatures = requireStringArray(requireParameters(mode), PARAM_SIGNATURES);
        mComponentsByTarget.clear();
        for (UserPackagePair target : targets) {
            Map<String, Boolean> components = new TreeMap<>();
            for (String component : PackageUtils.getFilteredComponents(target.getPackageName(),
                    target.getUserId(), mSignatures).keySet()) {
                components.put(component, false);
            }
            mComponentsByTarget.put(target, components);
        }
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull UserPackagePair target,
                                     @NonNull List<ActionExecutionRecord> previousRecords) {
        Map<String, Boolean> components = componentsFor(target);
        if (components.isEmpty()) {
            return actionResult(target, ActionResultStatus.UNSUPPORTED,
                    "No components matched the configured signatures");
        }
        if (requireBoolean(requireParameters(mode), PARAM_BLOCKED)) {
            ComponentUtils.blockFilteredComponents(target, mSignatures);
        } else {
            ComponentUtils.unblockFilteredComponents(target, mSignatures);
        }
        return actionResult(target, ActionResultStatus.SUCCESS, null);
    }

    @Nullable
    @Override
    protected ActionState getCurrentState(@NonNull UserPackagePair target) throws Throwable {
        Map<String, Boolean> components = componentsFor(target);
        if (components.isEmpty()) {
            return null;
        }
        try (ComponentsBlocker blocker = ComponentsBlocker.getInstance(target.getPackageName(),
                target.getUserId())) {
            for (String component : components.keySet()) {
                boolean blocked = blocker.isComponentBlocked(component)
                        || PackageUtils.isComponentDisabledByUser(target.getPackageName(), component,
                        target.getUserId());
                components.put(component, blocked);
            }
        }
        return state(target, components);
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
    private Map<String, Boolean> componentsFor(@NonNull UserPackagePair target) {
        Map<String, Boolean> components = mComponentsByTarget.get(target);
        if (components == null) {
            throw new IllegalStateException("Target was not prepared");
        }
        return components;
    }

    @Nullable
    private ActionState desiredState(@NonNull UserPackagePair target, boolean blocked) {
        Map<String, Boolean> components = componentsFor(target);
        if (components.isEmpty()) {
            return null;
        }
        Map<String, Boolean> desired = new TreeMap<>();
        for (String component : components.keySet()) {
            desired.put(component, blocked);
        }
        return state(target, desired);
    }

    @NonNull
    private static ActionState state(@NonNull UserPackagePair target,
                                     @NonNull Map<String, Boolean> components) {
        JSONObject value = new JSONObject();
        try {
            for (Map.Entry<String, Boolean> entry : new TreeMap<>(components).entrySet()) {
                value.put(entry.getKey(), entry.getValue());
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("Could not build component state", exception);
        }
        return new ActionState("package:" + target.getPackageName() + ":"
                + target.getUserId() + ":components", ActionValue.of(value.toString()));
    }

    private void validateParameters() {
        boolean onBlocked = requireBoolean(getOnParameters(), PARAM_BLOCKED);
        boolean offBlocked = requireBoolean(requireOffParameters(), PARAM_BLOCKED);
        if (onBlocked == offBlocked) {
            throw new IllegalArgumentException("ON and OFF blocked states must be opposites");
        }
        String[] onSignatures = sortedSignatures(
                requireStringArray(getOnParameters(), PARAM_SIGNATURES));
        String[] offSignatures = sortedSignatures(
                requireStringArray(requireOffParameters(), PARAM_SIGNATURES));
        if (!Arrays.equals(onSignatures, offSignatures)) {
            throw new IllegalArgumentException("ON and OFF must use the same signatures");
        }
    }

    @NonNull
    private static String[] sortedSignatures(@NonNull String[] signatures) {
        String[] sorted = signatures.clone();
        Arrays.sort(sorted);
        return sorted;
    }
}
