// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules;

import androidx.annotation.Keep;

@Keep
public enum RuleType {
    ACTIVITY,
    PROVIDER,
    RECEIVER,
    SERVICE,
    APP_OP,
    PERMISSION,
    MAGISK_HIDE,
    MAGISK_DENY_LIST,
    BATTERY_OPT,
    NET_POLICY,
    NOTIFICATION,
    URI_GRANT,
    SSAID,
}
