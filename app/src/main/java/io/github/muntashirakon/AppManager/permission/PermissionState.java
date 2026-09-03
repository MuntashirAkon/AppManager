// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

/**
 * State a permission provider can expose to the controller.
 */
public enum PermissionState {
    GRANTED, DENIED, UNKNOWN, UNSUPPORTED;

    public boolean isGranted() {
        return this == GRANTED;
    }
}
