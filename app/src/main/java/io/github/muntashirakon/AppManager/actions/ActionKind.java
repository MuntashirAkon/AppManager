// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import androidx.annotation.NonNull;

import android.content.Context;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.util.LocalizedString;

public enum ActionKind implements LocalizedString {
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
    BACKUP_APK(ActionScope.PACKAGE),
    RESTORE_BACKUP(ActionScope.PACKAGE),
    DELETE_BACKUP(ActionScope.PACKAGE),
    DEXOPT(ActionScope.PACKAGE),
    INSTALL_EXISTING(ActionScope.PACKAGE),
    UNINSTALL(ActionScope.PACKAGE),

    INSTALL(ActionScope.GLOBAL),
    SETTING(ActionScope.GLOBAL),
    DEVICE_STATE(ActionScope.GLOBAL),
    REBOOT(ActionScope.GLOBAL),
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

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        int label;
        switch (this) {
            case FREEZE: label = R.string.freeze; break;
            case FORCE_STOP: label = R.string.force_stop; break;
            case COMPONENT_STATE: label = R.string.components; break;
            case PERMISSION_STATE: label = R.string.declared_permission; break;
            case APP_OP_MODE: label = R.string.app_ops; break;
            case NETWORK_POLICY: label = R.string.net_policy; break;
            case SENSOR_ACCESS: label = R.string.sensors; break;
            case BATTERY_OPTIMIZATION: label = R.string.battery_optimization; break;
            case CLEAR_CACHE: label = R.string.clear_cache; break;
            case CLEAR_DATA: label = R.string.clear_data; break;
            case TRACKER_BLOCKING: label = R.string.trackers; break;
            case MAGISK_HIDE: label = R.string.magisk_hide_enabled; break;
            case MAGISK_DENYLIST: label = R.string.magisk_denylist; break;
            case BACKUP: label = R.string.backup; break;
            case BACKUP_APK: label = R.string.save_apk; break;
            case RESTORE_BACKUP: label = R.string.restore; break;
            case DELETE_BACKUP: label = R.string.delete_backup; break;
            case DEXOPT: label = R.string.batch_ops_runtime_optimization; break;
            case INSTALL_EXISTING: label = R.string.action_install_existing; break;
            case UNINSTALL: label = R.string.uninstall; break;
            case INSTALL: label = R.string.install; break;
            case SETTING: label = R.string.settings; break;
            case DEVICE_STATE: label = R.string.action_device_state; break;
            case REBOOT: label = R.string.restart_device; break;
            case RUN_SCRIPT: label = R.string.action_run_script; break;
            case SEND_BROADCAST: label = R.string.action_send_broadcast; break;
            default: throw new AssertionError(this);
        }
        return context.getText(label);
    }

}
