// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

public enum ActionConflict {
    NO_CONFLICT,
    OWNED_BY_SELF,
    EXTERNAL_CHANGE,
    COMPETING_ACTION,
    UNSUPPORTED_REVERT,
    PARTIAL_CONFLICT
}
