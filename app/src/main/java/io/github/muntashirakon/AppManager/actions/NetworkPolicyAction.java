// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public final class NetworkPolicyAction extends PackageScopedAction {
    public NetworkPolicyAction(@NonNull ActionTargetSpec target,
                               @NonNull ActionParameters on,
                               @NonNull ActionParameters off) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, target, on, off,
                Strictness.STRICT, RevertMode.EXPLICIT);
    }

    public NetworkPolicyAction(@NonNull String id, @Nullable String name, int schema,
                               @NonNull ActionTargetSpec target, @NonNull ActionParameters on,
                               @NonNull ActionParameters off, @NonNull Strictness strict,
                               @NonNull RevertMode revert) {
        super(id, name, schema, ActionKind.NETWORK_POLICY, target, on, off, strict, revert);
    }

    private NetworkPolicyAction(@NonNull Parcel in, @NonNull ActionKind kind) {
        super(in, kind);
    }

    @NonNull
    static NetworkPolicyAction fromParcel(@NonNull Parcel in, @NonNull ActionKind kind) {
        return new NetworkPolicyAction(in, kind);
    }

    private int mPolicy;

    @Override
    protected void onPreExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                @NonNull List<UserPackagePair> targets,
                                @NonNull List<ActionExecutionRecord> previousRecords) {
        mPolicy = requireInt(requireParameters(mode), "policy");
    }

    @NonNull
    @Override
    protected ActionResult onExecute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                     @NonNull UserPackagePair target,
                                     @NonNull List<ActionExecutionRecord> previousRecords) {
        NetworkPolicyManagerCompat.setUidPolicy(PackageUtils.getAppUid(target), mPolicy);
        return actionResult(target, ActionResultStatus.SUCCESS, null);
    }

    @NonNull
    @Override
    protected ActionState getCurrentState(@NonNull UserPackagePair target) {
        return state(target, NetworkPolicyManagerCompat.getUidPolicy(PackageUtils.getAppUid(target)));
    }

    @NonNull
    @Override
    protected ActionState getOnState(@NonNull UserPackagePair target) {
        return state(target, requireInt(requireParameters(ActionMode.ON), "policy"));
    }

    @NonNull
    @Override
    protected ActionState getOffState(@NonNull UserPackagePair target) {
        return state(target, requireInt(requireParameters(ActionMode.OFF), "policy"));
    }

    @NonNull
    private static ActionState state(@NonNull UserPackagePair target, int policy) {
        return new ActionState("package:" + target.getPackageName() + ":"
                + target.getUserId() + ":network_policy", ActionValue.of(policy));
    }
}
