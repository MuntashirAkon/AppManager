// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.List;
import java.util.UUID;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

public final class FreezeAction extends PackageScopedAction {
    public static final String PARAM_FREEZE = "freeze";
    public static final String PARAM_FREEZE_TYPE = "freeze_type";

    public FreezeAction(@NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                        @Nullable ActionParameters offParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, onParameters,
                offParameters, Strictness.STRICT,
                offParameters == null ? RevertMode.NONE : RevertMode.EXPLICIT);
    }

    public FreezeAction(@NonNull String id, @Nullable String name, int schemaVersion,
                        @NonNull ActionTargetSpec target, @NonNull ActionParameters onParameters,
                        @Nullable ActionParameters offParameters, @NonNull Strictness strictness,
                        @NonNull RevertMode revertMode) {
        super(id, name, schemaVersion, ActionKind.FREEZE, target, onParameters, offParameters,
                strictness, revertMode);
    }

    private FreezeAction(@NonNull Parcel in, @NonNull ActionKind kind) {
        super(in, kind);
    }

    @NonNull
    static FreezeAction fromParcel(@NonNull Parcel in, @NonNull ActionKind kind) {
        return new FreezeAction(in, kind);
    }

    private boolean mFreeze;
    private int mFreezeType;

    @Override
    protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                @NonNull List<UserPackagePair> targets,
                                @NonNull List<ActionExecutionRecord> previousRecords)
            throws IllegalArgumentException {
        ActionParameters params = requireParameters(mode);
        mFreeze = requireBoolean(params, PARAM_FREEZE);
        mFreezeType = getFreezeType(requireInt(params, PARAM_FREEZE_TYPE));
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull UserPackagePair target,
                                     @NonNull List<ActionExecutionRecord> previousRecords)
            throws Throwable {
        if (mFreeze) {
            FreezeUtils.freeze(target.getPackageName(), target.getUserId(), mFreezeType);
        } else {
            FreezeUtils.unfreeze(target.getPackageName(), target.getUserId());
        }
        return actionResult(target, ActionResultStatus.SUCCESS, null);
    }

    @NonNull
    @Override
    protected ActionState getCurrentState(@NonNull UserPackagePair target)
            throws RemoteException, PackageManager.NameNotFoundException {
        ApplicationInfo applicationInfo = PackageManagerCompat.getApplicationInfo(
                target.getPackageName(), PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES,
                target.getUserId());
        return state(target, FreezeUtils.isFrozen(applicationInfo));
    }

    @NonNull
    @Override
    protected ActionState getOnState(@NonNull UserPackagePair target) throws IllegalArgumentException {
        return state(target, requireBoolean(requireParameters(ActionMode.ON), PARAM_FREEZE));
    }

    @NonNull
    @Override
    protected ActionState getOffState(@NonNull UserPackagePair target) throws IllegalArgumentException {
        return state(target, requireBoolean(requireParameters(ActionMode.OFF), PARAM_FREEZE));
    }

    @NonNull
    private static ActionState state(@NonNull UserPackagePair target, boolean frozen) {
        return new ActionState("package:" + target.getPackageName() + ":"
                + target.getUserId() + ":freeze", ActionValue.of(frozen));
    }

    @VisibleForTesting
    public int getFreezeType(@NonNull ActionMode mode) {
        ActionParameters params = requireParameters(mode);
        return getFreezeType(requireInt(params, PARAM_FREEZE_TYPE));
    }

    private int getFreezeType(int freezeType) {
        if (freezeType != FreezeUtils.FREEZE_DISABLE
                && freezeType != FreezeUtils.FREEZE_HIDE
                && freezeType != FreezeUtils.FREEZE_SUSPEND
                && freezeType != FreezeUtils.FREEZE_ADV_SUSPEND) {
            throw new IllegalArgumentException("Invalid freeze type: " + freezeType);
        }
        return freezeType;
    }
}
