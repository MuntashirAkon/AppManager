// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import android.util.AndroidException;

import androidx.annotation.Nullable;

public class PermissionException extends AndroidException {
    public PermissionException(@Nullable String name) {
        super(name);
    }

    public PermissionException(@Nullable String name, @Nullable Throwable cause) {
        super(name, cause);
    }

    public PermissionException(@Nullable Exception cause) {
        super(cause);
    }
}
