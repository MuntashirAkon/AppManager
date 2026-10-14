// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.content.Intent;
import android.os.Parcel;
import android.os.Build;
import android.provider.Settings;
import android.provider.SettingsHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import io.github.muntashirakon.AppManager.utils.ContextUtils;

public final class DeviceStateAction extends GlobalScopedAction {
    public static final String PARAM_ENABLED = "enabled";
    public static final String TARGET_STATE = "state";

    public static final String STATE_AIRPLANE_MODE = "airplane_mode";
    public static final String STATE_LOCATION = "location";
    public static final String STATE_DEVELOPER_OPTIONS = "developer_options";
    public static final String STATE_USB_DEBUGGING = "usb_debugging";
    public static final String STATE_WIRELESS_DEBUGGING = "wireless_debugging";
    public static final String STATE_AUTO_ROTATE = "auto_rotate";

    public DeviceStateAction(@NonNull String state, boolean onEnabled, boolean offEnabled) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION,
                globalTargetWith(TARGET_STATE, normalizeState(state)), parameters(onEnabled),
                parameters(offEnabled), Strictness.STRICT, RevertMode.EXPLICIT);
    }

    public DeviceStateAction(@NonNull ActionTargetSpec target,
                             @NonNull ActionParameters onParameters,
                             @NonNull ActionParameters offParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, onParameters,
                offParameters, Strictness.STRICT, RevertMode.EXPLICIT);
    }

    public DeviceStateAction(@NonNull String id, @Nullable String name, int schemaVersion,
                             @NonNull ActionTargetSpec target,
                             @NonNull ActionParameters onParameters,
                             @NonNull ActionParameters offParameters,
                             @NonNull Strictness strictness, @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.DEVICE_STATE, target, onParameters, offParameters,
                strictness, revertMode);
    }

    private DeviceStateAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
    }

    @NonNull
    static DeviceStateAction fromParcel(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        return new DeviceStateAction(parcel, kind);
    }

    private String mState;
    private boolean mEnabled;

    @Override
    protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                @NonNull List<ActionExecutionRecord> previousRecords)
            throws IllegalStateException {
        if (mode == ActionMode.OFF && getOffParameters() == null) {
            throw new IllegalStateException("Device state has no reverse operation");
        }
        mState = normalizeState(requireTargetString(TARGET_STATE));
        mEnabled = requireBoolean(requireParameters(mode), PARAM_ENABLED);
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull List<ActionExecutionRecord> previousRecords) {
        boolean supported;
        switch (mState) {
            case STATE_AIRPLANE_MODE:
                supported = setAirplaneMode(mEnabled);
                break;
            case STATE_LOCATION:
                supported = setLocation(mEnabled);
                break;
            case STATE_DEVELOPER_OPTIONS:
                supported = setGlobalSetting(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, mEnabled);
                break;
            case STATE_USB_DEBUGGING:
                supported = setGlobalSetting(Settings.Global.ADB_ENABLED, mEnabled);
                break;
            case STATE_WIRELESS_DEBUGGING:
                supported = setWirelessDebugging(mEnabled);
                break;
            case STATE_AUTO_ROTATE:
                supported = setSystemSetting(Settings.System.ACCELEROMETER_ROTATION, mEnabled);
                break;
            default:
                return globalResult(ActionResultStatus.UNSUPPORTED,
                        "Unsupported device state: " + mState);
        }
        return globalResult(supported ? ActionResultStatus.SUCCESS : ActionResultStatus.UNSUPPORTED,
                supported ? null : "Device state is not supported on this device");
    }

    @NonNull
    @Override
    protected ActionState getCurrentState() throws IllegalArgumentException {
        return readState(mState == null ? normalizeState(requireTargetString(TARGET_STATE)) : mState);
    }

    @NonNull
    @Override
    protected ActionState getOnState() {
        return state(mState, requireBoolean(requireParameters(ActionMode.ON), PARAM_ENABLED));
    }

    @NonNull
    @Override
    protected ActionState getOffState() {
        return state(mState, requireBoolean(requireParameters(ActionMode.OFF), PARAM_ENABLED));
    }

    @NonNull
    private ActionState readState(@NonNull String state) {
        boolean enabled;
        switch (state) {
            case STATE_AIRPLANE_MODE:
                enabled = Settings.Global.getInt(ContextUtils.getContext().getContentResolver(),
                        Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
                break;
            case STATE_LOCATION:
                enabled = Settings.Secure.getInt(ContextUtils.getContext().getContentResolver(),
                        Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF)
                        != Settings.Secure.LOCATION_MODE_OFF;
                break;
            case STATE_DEVELOPER_OPTIONS:
                enabled = getGlobalSetting(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
                break;
            case STATE_USB_DEBUGGING:
                enabled = getGlobalSetting(Settings.Global.ADB_ENABLED);
                break;
            case STATE_WIRELESS_DEBUGGING:
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    throw new IllegalStateException("Wireless debugging is unavailable");
                }
                enabled = getGlobalSetting(SettingsHidden.Global.ADB_WIFI_ENABLED);
                break;
            case STATE_AUTO_ROTATE:
                enabled = Settings.System.getInt(ContextUtils.getContext().getContentResolver(),
                        Settings.System.ACCELEROMETER_ROTATION, 0) != 0;
                break;
            default:
                throw new IllegalArgumentException("Unsupported device state: " + state);
        }
        return state(state, enabled);
    }

    private boolean getGlobalSetting(@NonNull String key) {
        return Settings.Global.getInt(ContextUtils.getContext().getContentResolver(), key, 0) != 0;
    }

    @NonNull
    private static ActionState state(@NonNull String state, boolean enabled) {
        return new ActionState("global:device:" + state,
                ActionValue.of(enabled));
    }

    private boolean setAirplaneMode(boolean enabled) {
        boolean successful = setGlobalSetting(Settings.Global.AIRPLANE_MODE_ON, enabled);
        if (successful) {
            ContextUtils.getContext().sendBroadcast(new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                    .putExtra("state", enabled));
        }
        return successful;
    }

    private boolean setLocation(boolean enabled) {
        return Settings.Secure.putInt(ContextUtils.getContext().getContentResolver(),
                Settings.Secure.LOCATION_MODE,
                enabled ? Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                        : Settings.Secure.LOCATION_MODE_OFF);
    }

    private boolean setGlobalSetting(@NonNull String key, boolean enabled) {
        return Settings.Global.putInt(ContextUtils.getContext().getContentResolver(), key,
                enabled ? 1 : 0);
    }

    private boolean setSystemSetting(@NonNull String key, boolean enabled) {
        return Settings.System.putInt(ContextUtils.getContext().getContentResolver(), key,
                enabled ? 1 : 0);
    }

    private boolean setWirelessDebugging(boolean enabled) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }
        return setGlobalSetting(SettingsHidden.Global.ADB_WIFI_ENABLED, enabled);
    }

    @NonNull
    private static ActionParameters parameters(boolean enabled) {
        return new ActionParameters().put(PARAM_ENABLED, enabled);
    }

    @NonNull
    private static String normalizeState(@NonNull String state) {
        String normalized = state.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case STATE_AIRPLANE_MODE:
            case STATE_LOCATION:
            case STATE_DEVELOPER_OPTIONS:
            case STATE_USB_DEBUGGING:
            case STATE_WIRELESS_DEBUGGING:
            case STATE_AUTO_ROTATE:
                return normalized;
            default:
                throw new IllegalArgumentException("Unsupported device state: " + state);
        }
    }
}
