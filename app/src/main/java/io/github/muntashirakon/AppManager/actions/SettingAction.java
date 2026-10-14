// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import io.github.muntashirakon.AppManager.utils.ContextUtils;

public final class SettingAction extends GlobalScopedAction {
    public static final String PARAM_VALUE = "value";
    public static final String NAMESPACE_GLOBAL = "global";
    public static final String NAMESPACE_SECURE = "secure";
    public static final String NAMESPACE_SYSTEM = "system";

    public SettingAction(@NonNull String namespace, @NonNull String key,
                         @NonNull String onValue, @NonNull String offValue) {
        this(settingTarget(namespace, key),
                new ActionParameters().put(PARAM_VALUE, onValue),
                new ActionParameters().put(PARAM_VALUE, offValue));
    }

    public SettingAction(@NonNull ActionTargetSpec target,
                         @NonNull ActionParameters onParameters,
                         @NonNull ActionParameters offParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, onParameters,
                offParameters, Strictness.STRICT, RevertMode.EXPLICIT);
    }

    public SettingAction(@NonNull String id, @Nullable String name, int schemaVersion,
                         @NonNull ActionTargetSpec target,
                         @NonNull ActionParameters onParameters,
                         @NonNull ActionParameters offParameters,
                         @NonNull Strictness strictness, @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.SETTING, target, onParameters, offParameters,
                strictness, revertMode);
    }

    private SettingAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
    }

    @NonNull
    static SettingAction fromParcel(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        return new SettingAction(parcel, kind);
    }

    @NonNull
    @Override
    public List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                      @NonNull List<ActionExecutionRecord> previousRecords) {

        if (mode == ActionMode.OFF && getOffParameters() == null) {
            return unsupportedResults("Setting has no reverse operation");
        }
        ActionParameters parameters = requireParameters(mode);
        String namespace = requireTargetString("namespace");
        String key = requireTargetString("key");
        String value = requireString(parameters, PARAM_VALUE);
        boolean successful;
        try {
            switch (namespace) {
                case NAMESPACE_GLOBAL:
                    successful = Settings.Global.putString(ContextUtils.getContext()
                            .getContentResolver(), key, value);
                    break;
                case NAMESPACE_SECURE:
                    successful = Settings.Secure.putString(ContextUtils.getContext()
                            .getContentResolver(), key, value);
                    break;
                case NAMESPACE_SYSTEM:
                    successful = Settings.System.putString(ContextUtils.getContext()
                            .getContentResolver(), key, value);
                    break;
                default:
                    return unsupportedResults("Unsupported setting namespace: " + namespace);
            }
        } catch (Throwable exception) {
            return Collections.singletonList(globalResult(ActionResultStatus.FAILED,
                    exception.getMessage()));
        }
        return Collections.singletonList(globalResult(
                successful ? ActionResultStatus.SUCCESS : ActionResultStatus.FAILED,
                successful ? null : "Setting write failed"));
    }

    @NonNull
    private static ActionTargetSpec settingTarget(@NonNull String namespace,
                                                  @NonNull String key) {
        String normalizedNamespace = namespace.toLowerCase(Locale.ROOT);
        if (!NAMESPACE_GLOBAL.equals(normalizedNamespace)
                && !NAMESPACE_SECURE.equals(normalizedNamespace)
                && !NAMESPACE_SYSTEM.equals(normalizedNamespace)) {
            throw new IllegalArgumentException("Unsupported setting namespace: " + namespace);
        }
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Setting key cannot be empty");
        }
        try {
            return globalTarget(new JSONObject()
                    .put("namespace", normalizedNamespace)
                    .put("key", key));
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Could not create setting target", exception);
        }
    }
}
