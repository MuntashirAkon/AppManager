// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

/**
 * Controller interface for a permission provider or a composition of controllers.
 */
public interface IPermissionController {
    /**
     * Provider ID
     */
    @NonNull
    String getId();

    /**
     * Whether the provider support permission modification for the context.
     */
    boolean supports(@NonNull PermissionContext context);

    /**
     * Get the controller state for the given context
     */
    @NonNull
    PermissionControllerState getState(@NonNull PermissionContext context);

    /**
     * Set or request state change for the given context
     *
     * @return {@link PermissionChangeResult#unsupported(String)} when they do not own the context,
     * {@link PermissionChangeResult#userActionRequired(String, PermissionUserAction)} when an user
     * interaction is required (usually via Settings).
     */
    @WorkerThread
    @NonNull
    PermissionChangeResult setGranted(@NonNull PermissionContext context, boolean granted);
}
