// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.content.pm.ApplicationInfo;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.rules.compontents.ExternalComponentsImporter;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

public final class AppOpModeAction extends PackageScopedAction {
    public static final String PARAM_APP_OPS = "app_ops";
    public static final String PARAM_MODE = "mode";

    public AppOpModeAction(@NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                           @Nullable ActionParameters offParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, onParameters,
                offParameters);
    }

    public AppOpModeAction(@NonNull String id, @Nullable String name, int schemaVersion,
                           @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                           @Nullable ActionParameters offParameters) {
        this(id, name, schemaVersion, target, onParameters, offParameters, Strictness.STRICT,
                offParameters == null ? RevertMode.NONE : RevertMode.EXPLICIT);
    }

    public AppOpModeAction(@NonNull String id, @Nullable String name, int schemaVersion,
                           @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                           @Nullable ActionParameters offParameters, @NonNull Strictness strictness,
                           @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.APP_OP_MODE, target, onParameters, offParameters,
                strictness, revertMode);
    }

    private AppOpModeAction(@NonNull Parcel in, @NonNull ActionKind kind) {
        super(in, kind);
    }

    @NonNull
    static AppOpModeAction fromParcel(@NonNull Parcel in, @NonNull ActionKind kind) {
        return new AppOpModeAction(in, kind);
    }

    private int[] mAppOps;
    private int mMode;
    private AppOpsManagerCompat mAppOpsManager;

    @Override
    protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                @NonNull List<UserPackagePair> targets,
                                @NonNull List<ActionExecutionRecord> previousRecords) {
        validateAppOpParameters();
        ActionParameters parameters = requireParameters(mode);
        mAppOps = requireIntArray(parameters, PARAM_APP_OPS);
        mMode = requireInt(parameters, PARAM_MODE);
        mAppOpsManager = new AppOpsManagerCompat();
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull UserPackagePair target,
                                     @NonNull List<ActionExecutionRecord> previousRecords) throws Throwable {
        ExternalComponentsImporter.setModeToFilteredAppOps(mAppOpsManager, target,
                resolveAppOps(target, mAppOps), mMode);
        return actionResult(target, ActionResultStatus.SUCCESS, null);
    }

    @NonNull
    @Override
    protected ActionState getCurrentState(@NonNull UserPackagePair target) throws Throwable {
        JSONArray states = new JSONArray();
        try {
            ApplicationInfo applicationInfo = getApplicationInfo(target);
            List<AppOpsManagerCompat.OpEntry> entries =
                    AppOpsManagerCompat.getConfiguredOpsForPackage(mAppOpsManager,
                            applicationInfo.packageName, applicationInfo.uid);
            for (int appOp : resolveAppOps(target, mAppOps)) {
                states.put(new JSONObject()
                        .put("op", appOp)
                        .put("mode", AppOpsManagerCompat.getModeFromOpEntriesOrDefault(appOp,
                                entries)));
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("Could not build app-op state", exception);
        }
        return state(target, states.toString());
    }

    @NonNull
    @Override
    protected ActionState getOnState(@NonNull UserPackagePair target) throws Throwable {
        return desiredState(target, getOnParameters());
    }

    @NonNull
    @Override
    protected ActionState getOffState(@NonNull UserPackagePair target) throws Throwable {
        return desiredState(target, requireOffParameters());
    }

    @NonNull
    private ApplicationInfo getApplicationInfo(@NonNull UserPackagePair target) throws Throwable {
        return PackageManagerCompat.getApplicationInfo(target.getPackageName(),
                PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, target.getUserId());
    }

    @NonNull
    private int[] resolveAppOps(@NonNull UserPackagePair target, @NonNull int[] appOps)
            throws Throwable {
        if (appOps.length != 1 || appOps[0] != AppOpsManagerCompat.OP_NONE) {
            return sortedAppOps(appOps);
        }
        ApplicationInfo applicationInfo = getApplicationInfo(target);
        List<AppOpsManagerCompat.OpEntry> entries =
                AppOpsManagerCompat.getConfiguredOpsForPackage(mAppOpsManager,
                        applicationInfo.packageName, applicationInfo.uid);
        int[] requestedOps = new int[entries.size()];
        for (int i = 0; i < entries.size(); ++i) {
            requestedOps[i] = entries.get(i).getOp();
        }
        return sortedAppOps(requestedOps);
    }

    @NonNull
    private ActionState desiredState(@NonNull UserPackagePair target,
                                     @NonNull ActionParameters parameters) throws Throwable {
        JSONArray states = new JSONArray();
        int desiredMode = requireInt(parameters, PARAM_MODE);
        try {
            for (int appOp : resolveAppOps(target, requireIntArray(parameters, PARAM_APP_OPS))) {
                states.put(new JSONObject().put("op", appOp).put("mode", desiredMode));
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("Could not build app-op state", exception);
        }
        return state(target, states.toString());
    }

    private void validateAppOpParameters() {
        int[] onOps = sortedAppOps(requireIntArray(getOnParameters(), PARAM_APP_OPS));
        int[] offOps = sortedAppOps(requireIntArray(requireOffParameters(), PARAM_APP_OPS));
        if (!Arrays.equals(onOps, offOps)) {
            throw new IllegalArgumentException("ON and OFF must use the same app-ops");
        }
    }

    @NonNull
    private static int[] sortedAppOps(@NonNull int[] appOps) {
        int[] sorted = appOps.clone();
        Arrays.sort(sorted);
        return sorted;
    }

    @NonNull
    private static ActionState state(@NonNull UserPackagePair target, @NonNull String value) {
        return new ActionState("package:" + target.getPackageName() + ":"
                + target.getUserId() + ":app_ops", ActionValue.of(value));
    }
}
