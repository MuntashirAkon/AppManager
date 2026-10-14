// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.content.ContentResolver;
import android.os.Parcel;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

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

    private String mNamespace;
    private String mKey;
    private String mValue;
    private ContentResolver mResolver;

    @Override
    protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                @NonNull List<ActionExecutionRecord> previousRecords)
            throws IllegalArgumentException {
        if (mode == ActionMode.OFF && getOffParameters() == null) {
            throw new IllegalStateException("Setting has no reverse operation");
        }
        mNamespace = requireTargetString("namespace");
        mKey = requireTargetString("key");
        mValue = requireString(requireParameters(mode), PARAM_VALUE);
        mResolver = ContextUtils.getContext().getContentResolver();
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull List<ActionExecutionRecord> previousRecords) {
        boolean successful;
        switch (mNamespace) {
            case NAMESPACE_GLOBAL:
                successful = Settings.Global.putString(mResolver, mKey, mValue);
                break;
            case NAMESPACE_SECURE:
                successful = Settings.Secure.putString(mResolver, mKey, mValue);
                break;
            case NAMESPACE_SYSTEM:
                successful = Settings.System.putString(mResolver, mKey, mValue);
                break;
            default:
                return globalResult(ActionResultStatus.UNSUPPORTED,
                        "Unsupported setting namespace: " + mNamespace);
        }
        return globalResult(successful ? ActionResultStatus.SUCCESS : ActionResultStatus.FAILED,
                successful ? null : "Setting write failed");
    }

    @Nullable
    private String readValue(@NonNull String namespace, @NonNull String key) {
        switch (namespace) {
            case NAMESPACE_GLOBAL:
                return Settings.Global.getString(ContextUtils.getContext().getContentResolver(), key);
            case NAMESPACE_SECURE:
                return Settings.Secure.getString(ContextUtils.getContext().getContentResolver(), key);
            case NAMESPACE_SYSTEM:
                return Settings.System.getString(ContextUtils.getContext().getContentResolver(), key);
            default:
                throw new IllegalArgumentException("Unsupported setting namespace: " + namespace);
        }
    }

    @NonNull
    @Override
    protected ActionState getCurrentState() throws IllegalArgumentException {
        return state(mNamespace, mKey, readValue(mNamespace, mKey));
    }

    @NonNull
    @Override
    protected ActionState getOnState() {
        return state(mNamespace, mKey, requireString(requireParameters(ActionMode.ON), PARAM_VALUE));
    }

    @NonNull
    @Override
    protected ActionState getOffState() {
        return state(mNamespace, mKey, requireString(requireParameters(ActionMode.OFF), PARAM_VALUE));
    }

    @NonNull
    private static ActionState state(@NonNull String namespace, @NonNull String key,
                                     @Nullable String value) {
        return new ActionState("global:" + namespace + ":" + key,
                ActionValue.of(value == null ? JSONObject.NULL : value));
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
