// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ssaid;

public interface SettingsState {
    String SYSTEM_PACKAGE_NAME = "android";

    int MAX_BYTES_PER_APP_PACKAGE_UNLIMITED = -1;
    int MAX_BYTES_PER_APP_PACKAGE_LIMITED = 20000;

    int SETTINGS_TYPE_GLOBAL = 0;
    int SETTINGS_TYPE_SYSTEM = 1;
    int SETTINGS_TYPE_SECURE = 2;
    int SETTINGS_TYPE_SSAID = 3;
    int SETTINGS_TYPE_CONFIG = 4;

    Setting getSettingLocked(String name);

    boolean insertSettingLocked(String name, String value, String tag, boolean makeDefault, String packageName);

    interface Setting {
        String getValue();

        boolean isNull();
    }
}
