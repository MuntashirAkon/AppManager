// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import android.content.Intent;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.utils.IntentUtils;

/**
 * Describes Settings interaction required to change a user-mediated permission.
 */
public final class PermissionUserAction {
    @NonNull
    private final String mAction;
    private final boolean mSupportsPackage;

    PermissionUserAction(@NonNull String action, boolean supportsPackage) {
        mAction = action;
        mSupportsPackage = supportsPackage;
    }

    @NonNull
    public String getAction() {
        return mAction;
    }

    public boolean supportsPackage() {
        return mSupportsPackage;
    }

    @NonNull
    public Intent toIntent(@NonNull String packageName) {
        return IntentUtils.getSettings(mAction, mSupportsPackage ? packageName : null);
    }
}
