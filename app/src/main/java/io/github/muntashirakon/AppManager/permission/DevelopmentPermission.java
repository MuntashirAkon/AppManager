// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

public class DevelopmentPermission extends Permission {
    public DevelopmentPermission(String name, boolean granted, int appOp, boolean appOpAllowed, int flags) {
        super(name, granted, appOp, appOpAllowed, flags);
        runtime = false;
        readOnly = false;
    }
}
