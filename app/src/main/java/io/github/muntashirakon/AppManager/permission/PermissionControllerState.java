// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Immutable state and capability snapshot reported by a permission controller.
 */
public final class PermissionControllerState {
    @NonNull
    public final String controllerId;
    @NonNull
    public final PermissionState state;
    public final boolean modifiable;
    @Nullable
    public final PermissionUserAction userAction;

    public PermissionControllerState(@NonNull String controllerId, @NonNull PermissionState state,
                                     boolean modifiable, @Nullable PermissionUserAction userAction) {
        this.controllerId = controllerId;
        this.state = state;
        this.modifiable = modifiable;
        this.userAction = userAction;
    }
}
