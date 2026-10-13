// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
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

    @NonNull
    @Override
    public List<ActionResult> execute(@NonNull ActionContext context, @NonNull ActionMode mode,
                                      @NonNull List<ActionExecutionRecord> previous) {
        ActionParameters parameters = requireParameters(mode);
        List<UserPackagePair> targets = resolveTargets(context);
        List<ActionResult> results = new ArrayList<>(targets.size());
        int policyValue = requireInt(parameters, "policy");
        for (UserPackagePair target : targets)
            try {
                NetworkPolicyManagerCompat.setUidPolicy(PackageUtils.getAppUid(target), policyValue);
                results.add(actionResult(target, ActionResultStatus.SUCCESS, null));
            } catch (Throwable exception) {
                results.add(actionResult(target, ActionResultStatus.FAILED, exception.getMessage()));
            }
        return results;
    }
}
