// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import androidx.annotation.NonNull;

public enum ActionKind {
    FREEZE(ActionScope.PACKAGE),
    FORCE_STOP(ActionScope.PACKAGE),
    COMPONENT_STATE(ActionScope.PACKAGE),
    PERMISSION_STATE(ActionScope.PACKAGE),
    APP_OP_MODE(ActionScope.PACKAGE),
    NETWORK_POLICY(ActionScope.PACKAGE),
    SENSOR_ACCESS(ActionScope.PACKAGE),
    BATTERY_OPTIMIZATION(ActionScope.PACKAGE),
    CLEAR_CACHE(ActionScope.PACKAGE),
    CLEAR_DATA(ActionScope.PACKAGE),
    TRACKER_BLOCKING(ActionScope.PACKAGE),
    MAGISK_HIDE(ActionScope.PACKAGE),
    MAGISK_DENYLIST(ActionScope.PACKAGE),
    BACKUP(ActionScope.PACKAGE),
    RESTORE_BACKUP(ActionScope.PACKAGE),
    DEXOPT(ActionScope.PACKAGE),
    INSTALL(ActionScope.PACKAGE),
    UNINSTALL(ActionScope.PACKAGE),

    SETTING(ActionScope.GLOBAL),
    DEVICE_STATE(ActionScope.GLOBAL),
    RUN_SCRIPT(ActionScope.GLOBAL),
    SEND_BROADCAST(ActionScope.GLOBAL);

    private final ActionScope mScope;

    ActionKind(@NonNull ActionScope scope) {
        mScope = scope;
    }

    @NonNull
    public ActionScope getScope() {
        return mScope;
    }

}
