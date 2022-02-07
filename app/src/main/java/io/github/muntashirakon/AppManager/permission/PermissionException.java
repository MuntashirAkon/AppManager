// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import android.util.AndroidException;

public class PermissionException extends AndroidException {
    public PermissionException(String name) {
        super(name);
    }

    public PermissionException(String name, Throwable cause) {
        super(name, cause);
    }

    public PermissionException(Exception cause) {
        super(cause);
    }
}
