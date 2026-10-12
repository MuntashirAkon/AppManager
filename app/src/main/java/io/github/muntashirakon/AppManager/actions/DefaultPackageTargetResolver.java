// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import io.github.muntashirakon.AppManager.types.UserPackagePair;

public final class DefaultPackageTargetResolver implements ActionTargetResolver {
    @NonNull
    @Override
    public List<UserPackagePair> resolve(@NonNull ActionContext context, @NonNull PackageTargetSpec spec) {
        LinkedHashSet<UserPackagePair> result = new LinkedHashSet<>(spec.getConfiguredTargets());
        if (spec.includesSelected()) {
            result.addAll(context.selectedTargets);
        }
        if (spec.includesInherited() && context.triggerTarget != null) {
            result.add(context.triggerTarget);
        }
        return new ArrayList<>(result);
    }
}
