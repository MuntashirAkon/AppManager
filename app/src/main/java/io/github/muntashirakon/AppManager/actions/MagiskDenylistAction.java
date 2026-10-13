// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
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

    @NonNull
    @Override
    public List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                      @NonNull List<ActionExecutionRecord> previousRecords) {
        List<UserPackagePair> targets = resolveTargets(context);
        ActionParameters parameters = requireParameters(mode);
        String[] processNames = requireStringArray(parameters, PARAM_PROCESS_NAMES);
        boolean enabled = requireBoolean(parameters, PARAM_ENABLED);
        boolean isolated = optionalBoolean(parameters, PARAM_ISOLATED, false);
        boolean appZygote = optionalBoolean(parameters, PARAM_APP_ZYGOTE, false);
        List<ActionResult> results = new ArrayList<>(targets.size());
        for (UserPackagePair target : targets) {
            boolean successful = true;
            String message = null;
            for (String processName : processNames) {
                MagiskProcess process = new MagiskProcess(target.getPackageName(), processName);
                process.setEnabled(enabled);
                process.setIsolatedProcess(isolated);
                process.setAppZygote(appZygote);
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
            results.add(actionResult(target,
                    successful ? ActionResultStatus.SUCCESS : ActionResultStatus.FAILED, message));
        }
        return results;
    }
}
