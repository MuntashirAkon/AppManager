// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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
import io.github.muntashirakon.AppManager.permission.PermissionChangeResult;
import io.github.muntashirakon.AppManager.permission.PermissionControllerRegistry;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;

public final class PermissionStateAction extends PackageScopedAction {
    public static final String PARAM_PERMISSIONS = "permissions";
    public static final String PARAM_GRANTED = "granted";

    public PermissionStateAction(@NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                                 @Nullable ActionParameters offParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, onParameters,
                offParameters);
    }

    public PermissionStateAction(@NonNull String id, @Nullable String name, int schemaVersion,
                                 @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                                 @Nullable ActionParameters offParameters) {
        this(id, name, schemaVersion, target, onParameters, offParameters, Strictness.STRICT,
                offParameters == null ? RevertMode.NONE : RevertMode.EXPLICIT);
    }

    public PermissionStateAction(@NonNull String id, @Nullable String name, int schemaVersion,
                                 @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                                 @Nullable ActionParameters offParameters, @NonNull Strictness strictness,
                                 @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.PERMISSION_STATE, target, onParameters,
                offParameters, strictness, revertMode);
    }

    private PermissionStateAction(@NonNull Parcel in, @NonNull ActionKind kind) {
        super(in, kind);
    }

    @NonNull
    static PermissionStateAction fromParcel(@NonNull Parcel in, @NonNull ActionKind kind) {
        return new PermissionStateAction(in, kind);
    }

    private String[] mPermissions;
    private boolean mGranted;
    private PermissionControllerRegistry mControllers;
    private AppOpsManagerCompat mAppOpsManager;

    @Override
    protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                @NonNull List<UserPackagePair> targets,
                                @NonNull List<ActionExecutionRecord> previousRecords) {
        validatePermissionParameters();
        ActionParameters parameters = requireParameters(mode);
        mPermissions = requireStringArray(parameters, PARAM_PERMISSIONS);
        mGranted = requireBoolean(parameters, PARAM_GRANTED);
        mControllers = PermissionControllerRegistry.getInstance();
        mAppOpsManager = new AppOpsManagerCompat();
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull UserPackagePair target,
                                     @NonNull List<ActionExecutionRecord> previousRecords) {
        try {
            PackageInfo packageInfo = getPackageInfo(target);
            for (String permission : mPermissions) {
                PermissionChangeResult change = mControllers.trySetGrantedForBatch(packageInfo,
                        permission, target.getUserId(), mAppOpsManager, mGranted);
                if (!change.isSuccessful()) {
                    throw new IllegalStateException(change.getMessage(), change.getCause());
                }
            }
            return actionResult(target, ActionResultStatus.SUCCESS, null);
        } catch (Throwable exception) {
            return actionResult(target, ActionResultStatus.FAILED, exception.getMessage());
        }
    }

    @NonNull
    @Override
    protected ActionState getCurrentState(@NonNull UserPackagePair target) {
        JSONArray states = new JSONArray();
        try {
            for (String permission : sortedPermissions(mPermissions)) {
                states.put(new JSONObject()
                        .put("name", permission)
                        .put("granted", PermissionCompat.checkPermission(permission,
                                target.getPackageName(), target.getUserId())
                                == PackageManager.PERMISSION_GRANTED));
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("Could not build permission state", exception);
        }
        return state(target, states.toString());
    }

    @NonNull
    @Override
    protected ActionState getOnState(@NonNull UserPackagePair target) {
        return desiredState(target, getOnParameters());
    }

    @NonNull
    @Override
    protected ActionState getOffState(@NonNull UserPackagePair target) {
        return desiredState(target, requireOffParameters());
    }

    @NonNull
    private PackageInfo getPackageInfo(@NonNull UserPackagePair target) throws Throwable {
        return PackageManagerCompat.getPackageInfo(target.getPackageName(),
                PackageManager.GET_PERMISSIONS
                        | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES,
                target.getUserId());
    }

    private void validatePermissionParameters() {
        String[] onPermissions = sortedPermissions(
                requireStringArray(getOnParameters(), PARAM_PERMISSIONS));
        String[] offPermissions = sortedPermissions(
                requireStringArray(requireOffParameters(), PARAM_PERMISSIONS));
        if (!Arrays.equals(onPermissions, offPermissions)) {
            throw new IllegalArgumentException("ON and OFF must use the same permissions");
        }
    }

    @NonNull
    private ActionState desiredState(@NonNull UserPackagePair target,
                                     @NonNull ActionParameters parameters) {
        JSONArray states = new JSONArray();
        boolean granted = requireBoolean(parameters, PARAM_GRANTED);
        try {
            for (String permission : sortedPermissions(
                    requireStringArray(parameters, PARAM_PERMISSIONS))) {
                states.put(new JSONObject()
                        .put("name", permission)
                        .put("granted", granted));
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("Could not build permission state", exception);
        }
        return state(target, states.toString());
    }

    @NonNull
    private static ActionState state(@NonNull UserPackagePair target, @NonNull String value) {
        return new ActionState("package:" + target.getPackageName() + ":"
                + target.getUserId() + ":permissions", ActionValue.of(value));
    }

    @NonNull
    private static String[] sortedPermissions(@NonNull String[] permissions) {
        String[] sorted = permissions.clone();
        Arrays.sort(sorted);
        return sorted;
    }
}
