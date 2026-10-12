// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import androidx.annotation.NonNull;

import java.util.List;

import io.github.muntashirakon.AppManager.types.UserPackagePair;

public interface ActionTargetResolver {
    @NonNull
    List<UserPackagePair> resolve(@NonNull ActionContext context, @NonNull PackageTargetSpec targetSpec);
}
