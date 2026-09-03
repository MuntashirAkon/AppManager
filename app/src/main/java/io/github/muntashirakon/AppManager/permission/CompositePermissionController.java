// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolves several applicable permission providers as one permission controller.
 *
 * <p>State is conservative: any denied layer denies the effective permission, while an unknown
 * layer prevents claiming that it is granted. Grant/revoke is carried out in order of the providers
 * and stop at the first failure.</p>
 */
public final class CompositePermissionController implements IPermissionController {
    private final List<IPermissionController> mProviders;

    public CompositePermissionController(@NonNull List<IPermissionController> providers) {
        mProviders = Collections.unmodifiableList(new ArrayList<>(providers));
    }

    @NonNull
    @Override
    public String getId() {
        return "composite";
    }

    @Override
    public boolean supports(@NonNull PermissionContext context) {
        for (IPermissionController provider : mProviders) {
            if (provider.supports(context)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    @Override
    public PermissionControllerState getState(@NonNull PermissionContext context) {
        PermissionState state = PermissionState.GRANTED;
        boolean modifiable = false;
        PermissionUserAction action = null;
        boolean found = false;
        for (IPermissionController provider : mProviders) {
            if (!provider.supports(context)) {
                continue;
            }
            found = true;
            PermissionControllerState providerState = provider.getState(context);
            modifiable |= providerState.modifiable;
            if (action == null) {
                action = providerState.userAction;
            }
            if (providerState.state == PermissionState.DENIED) {
                state = PermissionState.DENIED;
            } else if (providerState.state == PermissionState.UNKNOWN
                    && state == PermissionState.GRANTED) {
                state = PermissionState.UNKNOWN;
            } else if (providerState.state == PermissionState.UNSUPPORTED)
                state = PermissionState.UNKNOWN;
        }
        return new PermissionControllerState(getId(), found ? state : PermissionState.UNSUPPORTED, modifiable, action);
    }

    @NonNull
    @Override
    public PermissionChangeResult setGranted(@NonNull PermissionContext context, boolean granted) {
        boolean found = false;
        for (IPermissionController provider : mProviders) {
            if (!provider.supports(context)) {
                continue;
            }
            found = true;
            PermissionChangeResult result = provider.setGranted(context, granted);
            if (!result.isSuccessful()) {
                return result;
            }
        }
        return found ? PermissionChangeResult.success()
                : PermissionChangeResult.unsupported("No permission provider supports " + context.permission.getName());
    }
}
