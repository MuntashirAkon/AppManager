// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.magisk.MagiskDenyList;
import io.github.muntashirakon.AppManager.magisk.MagiskProcess;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

public final class MagiskDenylistAction extends PackageScopedAction {
    public static final String PARAM_PROCESS_NAMES = "process_names";
    public static final String PARAM_ENABLED = "enabled";
    public static final String PARAM_ISOLATED = "isolated";
    public static final String PARAM_APP_ZYGOTE = "app_zygote";

    public MagiskDenylistAction(@NonNull ActionTargetSpec target,
                                @NonNull ActionParameters onParameters,
                                @NonNull ActionParameters offParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, onParameters,
                offParameters, Strictness.STRICT, RevertMode.EXPLICIT);
    }

    public MagiskDenylistAction(@NonNull String id, @Nullable String name, int schemaVersion,
                                @NonNull ActionTargetSpec target,
                                @NonNull ActionParameters onParameters,
                                @NonNull ActionParameters offParameters,
                                @NonNull Strictness strictness, @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.MAGISK_DENYLIST, target, onParameters,
                offParameters, strictness, revertMode);
    }

    private MagiskDenylistAction(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        super(parcel, kind);
    }

    @NonNull
    static MagiskDenylistAction fromParcel(@NonNull Parcel parcel, @NonNull ActionKind kind) {
        return new MagiskDenylistAction(parcel, kind);
    }

    private String[] mProcessNames;
    private boolean mEnabled;
    private boolean mIsolated;
    private boolean mAppZygote;

    @Override
    protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                @NonNull List<UserPackagePair> targets,
                                @NonNull List<ActionExecutionRecord> previousRecords) {
        validateProcessParameters();
        ActionParameters parameters = requireParameters(mode);
        mProcessNames = requireStringArray(parameters, PARAM_PROCESS_NAMES);
        mEnabled = requireBoolean(parameters, PARAM_ENABLED);
        mIsolated = optionalBoolean(parameters, PARAM_ISOLATED, false);
        mAppZygote = optionalBoolean(parameters, PARAM_APP_ZYGOTE, false);
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull UserPackagePair target,
                                     @NonNull List<ActionExecutionRecord> previousRecords) {
        boolean successful = true;
        String message = null;
        for (String processName : mProcessNames) {
            MagiskProcess process = new MagiskProcess(target.getPackageName(), processName);
            process.setEnabled(mEnabled);
            process.setIsolatedProcess(mIsolated);
            process.setAppZygote(mAppZygote);
            try {
                if (!MagiskDenyList.apply(process, true)) {
                    successful = false;
                    message = "Magisk DenyList operation failed for " + processName;
                    break;
                }
            } catch (Throwable exception) {
                successful = false;
                message = exception.getMessage();
                break;
            }
        }
        return actionResult(target,
                successful ? ActionResultStatus.SUCCESS : ActionResultStatus.FAILED, message);
    }

    @NonNull
    @Override
    protected ActionState getCurrentState(@NonNull UserPackagePair target) {
        Collection<String> enabledProcesses = MagiskDenyList.getProcesses(target.getPackageName());
        return processState(target, mProcessNames, enabledProcesses, false);
    }

    @NonNull
    @Override
    protected ActionState getOnState(@NonNull UserPackagePair target) {
        ActionParameters parameters = getOnParameters();
        return processState(target, requireStringArray(parameters, PARAM_PROCESS_NAMES), null,
                requireBoolean(parameters, PARAM_ENABLED));
    }

    @NonNull
    @Override
    protected ActionState getOffState(@NonNull UserPackagePair target) {
        ActionParameters parameters = requireOffParameters();
        return processState(target, requireStringArray(parameters, PARAM_PROCESS_NAMES), null,
                requireBoolean(parameters, PARAM_ENABLED));
    }

    private void validateProcessParameters() {
        ActionParameters onParameters = getOnParameters();
        ActionParameters offParameters = requireOffParameters();
        String[] onProcesses = requireStringArray(onParameters, PARAM_PROCESS_NAMES).clone();
        String[] offProcesses = requireStringArray(offParameters, PARAM_PROCESS_NAMES).clone();
        Arrays.sort(onProcesses);
        Arrays.sort(offProcesses);
        if (!Arrays.equals(onProcesses, offProcesses)) {
            throw new IllegalArgumentException(
                    "ON and OFF must use the same Magisk DenyList process names");
        }
    }

    @NonNull
    private static ActionState processState(@NonNull UserPackagePair target,
                                            @NonNull String[] processNames,
                                            @Nullable Collection<String> enabledProcesses,
                                            boolean enabled) {
        String[] sortedProcessNames = processNames.clone();
        Arrays.sort(sortedProcessNames);
        JSONArray states = new JSONArray();
        try {
            for (String processName : sortedProcessNames) {
                boolean processEnabled = enabledProcesses == null
                        ? enabled : enabledProcesses.contains(processName);
                states.put(new JSONObject()
                        .put("name", processName)
                        .put("enabled", processEnabled));
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("Could not build Magisk DenyList state", exception);
        }
        return new ActionState("package:" + target.getPackageName() + ":magisk_denylist",
                ActionValue.of(states.toString()));
    }
}
