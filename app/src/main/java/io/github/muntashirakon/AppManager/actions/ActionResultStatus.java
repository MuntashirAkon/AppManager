// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

public enum ActionResultStatus {
    SUCCESS,
    UNCHANGED,
    SKIPPED_STRICT_MISMATCH,
    CONFLICT,
    UNSUPPORTED,
    FAILED,
    USER_ACTION_REQUIRED,
    PARTIAL
}
