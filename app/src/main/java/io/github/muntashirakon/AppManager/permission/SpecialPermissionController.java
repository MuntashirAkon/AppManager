// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import android.Manifest;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves special permissions that require user interaction in system Settings.
 *
 * <p>Package Manager and AppOp controllers retain precedence when they can mutate a permission
 * directly. This controller supplies the fallback action and never starts an activity.</p>
 */
public final class SpecialPermissionController implements IPermissionController {
    @NonNull
    private static final SpecialPermissionController INSTANCE =
            new SpecialPermissionController(Build.VERSION.SDK_INT);

    @NonNull
    private final Map<String, PermissionUserAction> mUserActions;

    @NonNull
    public static SpecialPermissionController getInstance() {
        return INSTANCE;
    }

    SpecialPermissionController(int sdkInt) {
        Map<String, PermissionUserAction> actions = new HashMap<>();
        if (sdkInt >= Build.VERSION_CODES.M) {
            add(actions, Manifest.permission.ACCESS_NOTIFICATION_POLICY,
                    Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, false);
            add(actions, Manifest.permission.PACKAGE_USAGE_STATS,
                    Settings.ACTION_USAGE_ACCESS_SETTINGS, true);
            add(actions, Manifest.permission.SYSTEM_ALERT_WINDOW,
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true);
            add(actions, Manifest.permission.WRITE_SETTINGS,
                    Settings.ACTION_MANAGE_WRITE_SETTINGS, true);
        }
        if (sdkInt >= Build.VERSION_CODES.O) {
            add(actions, Manifest.permission.REQUEST_INSTALL_PACKAGES,
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, true);
        }
        if (sdkInt >= Build.VERSION_CODES.R) {
            add(actions, Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, true);
        }
        if (sdkInt >= Build.VERSION_CODES.S) {
            add(actions, Manifest.permission.MANAGE_MEDIA,
                    Settings.ACTION_REQUEST_MANAGE_MEDIA, true);
            add(actions, Manifest.permission.SCHEDULE_EXACT_ALARM,
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true);
        }
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            add(actions, Manifest.permission.POST_NOTIFICATIONS,
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS, true);
        }
        if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(actions, Manifest.permission.RUN_USER_INITIATED_JOBS,
                    "android.settings.MANAGE_APP_LONG_RUNNING_JOBS", true);
            add(actions, Manifest.permission.USE_FULL_SCREEN_INTENT,
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, true);
        }
        if (sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            add(actions, Manifest.permission.MEDIA_ROUTING_CONTROL,
                    Settings.ACTION_REQUEST_MEDIA_ROUTING_CONTROL, true);
        }

        // Bound permissions
        add(actions, Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
                Settings.ACTION_ACCESSIBILITY_SETTINGS, false);
        add(actions, Manifest.permission.BIND_INPUT_METHOD,
                Settings.ACTION_INPUT_METHOD_SETTINGS, false);
        if (sdkInt >= Build.VERSION_CODES.O) {
            add(actions, Manifest.permission.BIND_AUTOFILL_SERVICE,
                    Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, true);
        }
        if (sdkInt >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            add(actions, Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, false);
        }
        if (sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            add(actions, Manifest.permission.BIND_CREDENTIAL_PROVIDER_SERVICE,
                    Settings.ACTION_CREDENTIAL_PROVIDER, true);
        }
        mUserActions = Collections.unmodifiableMap(actions);
    }

    private static void add(@NonNull Map<String, PermissionUserAction> actions,
                            @NonNull String permissionName, @NonNull String action,
                            boolean supportsPackage) {
        actions.put(permissionName, new PermissionUserAction(action, supportsPackage));
    }

    public boolean supports(@NonNull String permissionName) {
        return mUserActions.containsKey(permissionName);
    }

    @NonNull
    @Override
    public String getId() {
        return "special-access";
    }

    @Override
    public boolean supports(@NonNull PermissionContext context) {
        return supports(context.permission.getName());
    }

    @NonNull
    @Override
    public PermissionControllerState getState(@NonNull PermissionContext context) {
        return new PermissionControllerState(getId(), getState(context.permission), false,
                getUserAction(context.permission.getName()));
    }

    @NonNull
    @Override
    public PermissionChangeResult setGranted(@NonNull PermissionContext context,
                                             boolean granted) {
        return requestChange(context.permission.getName(), granted);
    }

    @NonNull
    public PermissionState getState(@NonNull Permission permission) {
        if (!supports(permission.getName())) {
            return PermissionState.UNSUPPORTED;
        }
        if (permission.affectsAppOp()) {
            return permission.isGrantedIncludingAppOp()
                    ? PermissionState.GRANTED : PermissionState.DENIED;
        }
        // TODO: 9/2/26 Handle bound permissions
        return PermissionState.UNKNOWN;
    }

    @Nullable
    public PermissionUserAction getUserAction(@NonNull String permissionName) {
        return mUserActions.get(permissionName);
    }

    @NonNull
    public PermissionChangeResult requestChange(@NonNull String permissionName, boolean granted) {
        PermissionUserAction action = getUserAction(permissionName);
        if (action == null) {
            return PermissionChangeResult.unsupported(
                    "Unsupported special permission " + permissionName);
        }
        return PermissionChangeResult.userActionRequired(
                (granted ? "Grant" : "Revoke") + " requires system Settings", action);
    }
}
